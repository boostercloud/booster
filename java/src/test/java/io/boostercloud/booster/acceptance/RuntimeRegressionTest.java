package io.boostercloud.booster.acceptance;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import io.boostercloud.booster.core.AccessPolicy;
import io.boostercloud.booster.core.Booster;
import io.boostercloud.booster.core.BoosterException;
import io.boostercloud.booster.core.InMemoryEventStore;
import io.boostercloud.booster.core.Json;
import io.boostercloud.booster.core.OptimisticConcurrencyException;
import io.boostercloud.booster.core.RequestContext;
import io.boostercloud.booster.core.UserContext;
import io.boostercloud.booster.store.JdbcEventStore;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Cross-component regressions derived from adversarial runtime review. */
class RuntimeRegressionTest {
  @TempDir Path directory;
  public record IdentityResult() {}
  public record Account(String id, int balance) {}
  public record AccountView(String id, int balance) {}
  public record Adjust(String id, int amount) {}
  public record Deposit(String id, int amount) {}
  public record InvalidBatch(String id) {}
  public record Begin(String id) {}
  public record Started(String id) {}
  public record Applied(String id) {}

  @Test
  void idempotentResultCannotCrossTenantClaimsWithIdenticalSubjectAndRoles() {
    try (var app = Booster.builder(new InMemoryEventStore())
        .command(IdentityResult.class, String.class, AccessPolicy.authenticated(),
            (command, register) -> register.context().user().claims().get("tenant"))
        .build()) {
      var first = new RequestContext("same-request", user("tenant-a"), Map.of());
      var second = new RequestContext("same-request", user("tenant-b"), Map.of());
      assertEquals("tenant-a", app.executeCommand("IdentityResult", json(new IdentityResult()), first).textValue());
      var conflict = assertThrows(BoosterException.class,
          () -> app.executeCommand("IdentityResult", json(new IdentityResult()), second));
      assertEquals(BoosterException.Code.CONFLICT, conflict.code());
    }
  }

  @Test
  void authorizationIsRecheckedBeforeReturningAnExistingReceipt() {
    try (var app = Booster.builder(new InMemoryEventStore())
        .command(IdentityResult.class, String.class, AccessPolicy.roles("admin"), (command, register) -> "private-result")
        .build()) {
      var authorized = new RequestContext("same-request", new UserContext("same-subject", Set.of("admin"), Map.of()), Map.of());
      var revoked = new RequestContext("same-request", new UserContext("same-subject", Set.of(), Map.of()), Map.of());
      assertEquals("private-result", app.executeCommand("IdentityResult", json(new IdentityResult()), authorized).textValue());
      var denied = assertThrows(BoosterException.class,
          () -> app.executeCommand("IdentityResult", json(new IdentityResult()), revoked));
      assertEquals(BoosterException.Code.FORBIDDEN, denied.code());
    }
  }

  @Test
  void failedProjectionDoesNotCommitAPoisonEventOrPublishPartialBatchState() {
    var store = new InMemoryEventStore();
    try (var app = Booster.builder(store)
        .event(Adjust.class, Adjust::id, Account.class,
            (event, old) -> new Account(event.id(), (old == null ? 0 : old.balance()) + event.amount()))
        .readModel(AccountView.class, Account.class, AccessPolicy.publicAccess(), (account, old) -> {
          if (account.balance() < 0) throw new IllegalArgumentException("Negative balances cannot be projected");
          return new AccountView(account.id(), account.balance());
        })
        .command(InvalidBatch.class, AccessPolicy.publicAccess(), (command, register) -> {
          register.emit(new Adjust(command.id(), 10));
          register.emit(new Adjust(command.id(), -20));
          return "should-not-commit";
        })
        .build()) {
      assertThrows(BoosterException.class,
          () -> app.executeCommand("InvalidBatch", json(new InvalidBatch("a")), context("invalid")));
      assertEquals(0, store.lastPosition(), "Preflight failure must leave durable history unchanged");
      assertTrue(store.findCommand("invalid").isEmpty());
      assertTrue(app.getReadModel("AccountView", "a", context("read")).isNull());
      assertEquals(0, app.position());
    }
  }

  @Test
  void externalCommandsCannotReserveInternalHandlerReceiptIdentifiers() {
    var store = new InMemoryEventStore();
    try (var app = Booster.builder(store)
        .command(IdentityResult.class, AccessPolicy.publicAccess(), (command, register) -> "external")
        .build()) {
      String reserved = "__booster_handler:known-event:0";
      assertThrows(BoosterException.class,
          () -> app.executeCommand("IdentityResult", json(new IdentityResult()), context(reserved)));
      assertTrue(store.findCommand(reserved).isEmpty());
    }
  }

  @Test
  void reducerCannotChangeTheAggregateIdentityRecordedInTheStream() {
    var store = new InMemoryEventStore();
    try (var app = Booster.builder(store)
        .event(Adjust.class, Adjust::id, Account.class, (event, old) -> new Account("different-account", event.amount()))
        .command(Deposit.class, AccessPolicy.publicAccess(), (command, register) -> {
          register.emit(new Adjust(command.id(), command.amount()));
          return "accepted";
        }).build()) {
      assertThrows(BoosterException.class,
          () -> app.executeCommand("Deposit", json(new Deposit("actual-account", 1)), context("invalid-entity-id")));
      assertEquals(0, store.lastPosition());
    }
  }

  @Test
  void projectionCannotPublishARecordWhoseIdDiffersFromItsLookupKey() {
    var store = new InMemoryEventStore();
    try (var app = Booster.builder(store)
        .event(Adjust.class, Adjust::id, Account.class, (event, old) -> new Account(event.id(), event.amount()))
        .readModel(AccountView.class, Account.class, AccessPolicy.publicAccess(),
            (account, old) -> new AccountView("different-view", account.balance()))
        .command(Deposit.class, AccessPolicy.publicAccess(), (command, register) -> {
          register.emit(new Adjust(command.id(), command.amount()));
          return "accepted";
        }).build()) {
      assertThrows(BoosterException.class,
          () -> app.executeCommand("Deposit", json(new Deposit("actual-account", 1)), context("invalid-view-id")));
      assertEquals(0, store.lastPosition());
      assertTrue(app.getReadModel("AccountView", "actual-account", context("read")).isNull());
    }
  }

  @Test
  @Timeout(20)
  void twoRuntimesSharingAFileDatabaseRejectLostUpdatesAndCatchUpTheirViews() throws Exception {
    Path file = directory.resolve("shared-runtime");
    var ready = new CountDownLatch(2);
    var proceed = new CountDownLatch(1);
    try (var first = accountRuntime(new JdbcEventStore(file), ready, proceed);
         var second = accountRuntime(new JdbcEventStore(file), ready, proceed);
         var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var a = executor.submit(() -> deposit(first, "request-a"));
      var b = executor.submit(() -> deposit(second, "request-b"));
      assertTrue(ready.await(5, TimeUnit.SECONDS));
      proceed.countDown();
      assertNotEquals(a.get(5, TimeUnit.SECONDS), b.get(5, TimeUnit.SECONDS));
      assertEquals(1, first.eventStore().lastPosition());
      assertEquals(1, first.getReadModel("AccountView", "account", context("read-a")).path("balance").intValue());
      assertEquals(1, second.getReadModel("AccountView", "account", context("read-b")).path("balance").intValue());
      first.rebuild();
      assertEquals(1, first.getReadModel("AccountView", "account", context("read-c")).path("balance").intValue());
    } finally {
      proceed.countDown();
    }
  }

  @Test
  void retryAfterHandlerFailureResumesDurableWorkWithoutRepeatingTheCommand() {
    var failOnce = new AtomicBoolean(true);
    var commandInvocations = new AtomicInteger();
    var handlerInvocations = new AtomicInteger();
    var reportedFailures = new AtomicInteger();
    var store = new InMemoryEventStore();
    try (var app = Booster.builder(store)
        .event(Started.class, Started::id, Account.class, (event, old) -> new Account(event.id(), 0))
        .event(Applied.class, Applied::id, Account.class, (event, old) -> new Account(event.id(), old.balance() + 1))
        .readModel(AccountView.class, Account.class, AccessPolicy.publicAccess(),
            (account, old) -> new AccountView(account.id(), account.balance()))
        .command(Begin.class, AccessPolicy.publicAccess(), (command, register) -> {
          commandInvocations.incrementAndGet();
          register.emit(new Started(command.id()));
          return "accepted";
        })
        .eventHandler(Started.class, (event, register) -> {
          handlerInvocations.incrementAndGet();
          register.emit(new Applied(event.id()));
          if (failOnce.getAndSet(false)) throw new IllegalStateException("Transient handler failure");
        })
        .onError(error -> reportedFailures.incrementAndGet())
        .build()) {
      assertEquals("accepted", app.executeCommand("Begin", json(new Begin("a")), context("request")).textValue());
      assertEquals(1, reportedFailures.get(), "A failed post-commit handler must be reported without misreporting a committed command as failed");
      assertEquals(1, store.lastPosition());
      assertTrue(store.findCommand("request").isPresent());
      assertEquals("accepted", app.executeCommand("Begin", json(new Begin("a")), context("request")).textValue());
      assertEquals("accepted", app.executeCommand("Begin", json(new Begin("a")), context("request")).textValue());
      assertEquals(2, store.lastPosition());
      assertEquals(1, commandInvocations.get());
      assertEquals(2, handlerInvocations.get());
      assertEquals(1, app.getReadModel("AccountView", "a", context("read")).path("balance").intValue());
    }
  }

  private static Booster accountRuntime(JdbcEventStore store, CountDownLatch ready, CountDownLatch proceed) {
    return Booster.builder(store)
        .event(Adjust.class, Adjust::id, Account.class,
            (event, old) -> new Account(event.id(), (old == null ? 0 : old.balance()) + event.amount()))
        .readModel(AccountView.class, Account.class, AccessPolicy.publicAccess(),
            (account, old) -> new AccountView(account.id(), account.balance()))
        .command(Deposit.class, AccessPolicy.publicAccess(), (command, register) -> {
          register.entity(Account.class, command.id());
          ready.countDown();
          if (!proceed.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent command did not start");
          register.emit(new Adjust(command.id(), command.amount()));
          return "accepted";
        })
        .build();
  }

  private static boolean deposit(Booster app, String requestId) {
    try {
      app.executeCommand("Deposit", json(new Deposit("account", 1)), context(requestId));
      return true;
    } catch (OptimisticConcurrencyException expected) {
      return false;
    }
  }

  private static UserContext user(String tenant) { return new UserContext("subject", Set.of("customer"), Map.of("tenant", tenant)); }
  private static RequestContext context(String requestId) { return new RequestContext(requestId, UserContext.anonymous(), Map.of()); }
  private static JsonNode json(Object value) { return Json.MAPPER.valueToTree(value); }
}
