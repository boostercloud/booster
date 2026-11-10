package com.boostercloud.framework.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.core.decorators.Decorators;
import com.boostercloud.framework.core.decorators.Entity;
import com.boostercloud.framework.core.decorators.GlobalErrorHandler;
import com.boostercloud.framework.core.decorators.Reduces;
import com.boostercloud.framework.core.decorators.SchemaMigration;
import com.boostercloud.framework.core.decorators.ToVersion;
import com.boostercloud.framework.core.services.graphql.GraphQLResolverContext;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.concepts.CommandAuthorizer;
import com.boostercloud.framework.types.concepts.CommandBeforeFunction;
import com.boostercloud.framework.types.concepts.CommandMetadata;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.CommandEnvelope;
import com.boostercloud.framework.types.envelope.NonPersistedEventEnvelope;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.framework.types.errors.InvalidParameterError;
import com.boostercloud.framework.types.errors.NotAuthorizedError;
import com.boostercloud.framework.types.errors.NotFoundError;
import com.boostercloud.framework.types.provider.ProviderEventsLibrary;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Port of {@code test/booster-command-dispatcher.test.ts}, plus the untested paths of the TypeScript source. */
class BoosterCommandDispatcherTest extends BoosterTestSupport {

  static final List<Object> HANDLED = new ArrayList<>();
  static final AtomicReference<Register> REGISTER = new AtomicReference<>();
  static final AtomicReference<RuntimeException> FAILURE = new AtomicReference<>();
  static final AtomicReference<Boolean> SWALLOW = new AtomicReference<>(false);

  @Entity
  public record Post(UUID id) {
    @Reduces(CommentPosted.class)
    public static Post react(CommentPosted event, Post current) {
      return current;
    }
  }

  public record CommentPosted(UUID postId, String comment) {
    public UUID entityID() {
      return postId;
    }
  }

  @Command(authorize = All.class)
  public record PostComment(String comment) {
    public static Object handle(PostComment command, Register register) {
      HANDLED.add(command);
      REGISTER.set(register);
      register.responseHeaders().put("Test-Header", "test");
      register.events(new CommentPosted(UUID.of("post-1"), command.comment()));
      if (FAILURE.get() != null) {
        throw FAILURE.get();
      }
      return "result of " + command.comment();
    }
  }

  @Command(authorize = All.class)
  public record SlowCommand(String comment) {
    public static CompletableFuture<String> handle(SlowCommand command, Register register) {
      return CompletableFuture.supplyAsync(() -> {
        HANDLED.add("slow finished");
        return "slow";
      }, CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS));
    }
  }

  public static class ChangeComment implements CommandBeforeFunction {
    @Override
    public Map<String, Object> apply(Map<String, Object> input, UserEnvelope currentUser) {
      Map<String, Object> changed = new LinkedHashMap<>(input);
      changed.put("comment", "Look, I changed the message");
      return changed;
    }
  }

  public static class ChangeAgain implements CommandBeforeFunction {
    @Override
    public Map<String, Object> apply(Map<String, Object> input, UserEnvelope currentUser) {
      if ("Look, I changed the message".equals(input.get("comment"))) {
        input = new LinkedHashMap<>(input);
        input.put("comment", "Yes, I changed it for a second time");
      }
      return input;
    }
  }

  @Command(authorize = All.class, before = ChangeComment.class)
  public record HookedComment(String comment) {
    public static void handle(HookedComment command, Register register) {
      HANDLED.add(command);
    }
  }

  @Command(authorize = All.class, before = {ChangeComment.class, ChangeAgain.class})
  public record DoubleHookedComment(String comment) {
    public static void handle(DoubleHookedComment command, Register register) {
      HANDLED.add(command);
    }
  }

  public record CommentV1(String text) {}

  public record CommentV2(String comment) {}

  @SchemaMigration(MigratedComment.class)
  public static class MigratedCommentMigration {
    @ToVersion(toVersion = 2, fromSchema = CommentV1.class, toSchema = CommentV2.class)
    public CommentV2 rename(CommentV1 old) {
      return new CommentV2(old.text());
    }
  }

  public static class SeesInput implements CommandBeforeFunction {
    @Override
    public Map<String, Object> apply(Map<String, Object> input, UserEnvelope currentUser) {
      HANDLED.add(Map.copyOf(input));
      return input;
    }
  }

  @Command(authorize = All.class, before = SeesInput.class)
  public record MigratedComment(String comment) {
    public static void handle(MigratedComment command, Register register) {
      HANDLED.add(command);
    }
  }

  @GlobalErrorHandler
  public static class ErrorHandler {
    public static Throwable onCommandHandlerError(Throwable error, CommandEnvelope envelope, CommandMetadata metadata) {
      return SWALLOW.get() ? null : new IllegalStateException("handled: " + error.getMessage());
    }
  }

  private BoosterConfig config;
  private ProviderEventsLibrary events;

  @BeforeEach
  @SuppressWarnings("deprecation")
  void setUp() {
    HANDLED.clear();
    REGISTER.set(null);
    FAILURE.set(null);
    SWALLOW.set(false);
    config = Booster.config();
    config.eventStoreRetry.initialDelay = 1;
    ProviderLibrary provider = mock(ProviderLibrary.class);
    events = mock(ProviderEventsLibrary.class);
    when(provider.events()).thenReturn(events);
    when(events.store(anyList(), any())).thenReturn(List.of());
    config.setProvider(provider);
    for (Class<?> type : List.of(Post.class, PostComment.class, SlowCommand.class, HookedComment.class,
        DoubleHookedComment.class, MigratedCommentMigration.class, MigratedComment.class)) {
      Decorators.registerClass(type);
    }
  }

  private static CommandEnvelope envelope(String typeName, int version, Map<String, Object> value) {
    return CommandEnvelope.builder().requestID(UUID.of("42")).typeName(typeName).version(version).value(value)
        .currentUser(UserEnvelope.builder().username("user@example.com").roles(List.of("Loki")).build()).build();
  }

  private static GraphQLResolverContext context(Map<String, String> responseHeaders) {
    return new GraphQLResolverContext(null, responseHeaders, null, UUID.of("42"), null, true, null, null);
  }

  @Test
  void failsWhenTheVersionIsMissing() {
    BoosterCommandDispatcher dispatcher = new BoosterCommandDispatcher(config);

    assertThatThrownBy(() -> dispatcher.dispatchCommand(envelope("PostComment", 0, Map.of()), context(new LinkedHashMap<>())))
        .isInstanceOf(InvalidParameterError.class)
        .hasMessage("The required command \"version\" was not present");
  }

  @Test
  void failsWhenThereIsNoHandler() {
    BoosterCommandDispatcher dispatcher = new BoosterCommandDispatcher(config);

    assertThatThrownBy(() -> dispatcher.dispatchCommand(envelope("Unknown", 1, Map.of()), context(new LinkedHashMap<>())))
        .isInstanceOf(NotFoundError.class)
        .hasMessage("Could not find a proper handler for Unknown");
  }

  @Test
  void failsWhenTheUserIsNotAuthorizedAndRunsNothingElse() {
    class Thor {}
    CommandMetadata metadata = config.commandHandlers.get("PostComment");
    config.commandHandlers.put("PostComment", new CommandMetadata(PostComment.class, metadata.properties(),
        metadata.methods(), BoosterAuthorizer.authorizeRoles(List.of(Thor.class)), List.of()));

    assertThatThrownBy(() -> new BoosterCommandDispatcher(config)
        .dispatchCommand(envelope("PostComment", 1, Map.of("comment", "x")), context(new LinkedHashMap<>())))
        .isInstanceOf(NotAuthorizedError.class)
        .hasMessage("Access denied for this resource");
    assertThat(HANDLED).isEmpty();
  }

  @Test
  void callsTheHandlerWithAnInstanceAndReturnsItsResult() {
    Object result = new BoosterCommandDispatcher(config)
        .dispatchCommand(envelope("PostComment", 1, Map.of("comment", "to handle")), context(new LinkedHashMap<>()));

    assertThat(HANDLED).containsExactly(new PostComment("to handle"));
    assertThat(result).isEqualTo("result of to handle");
  }

  @Test
  void sharesTheResponseHeadersWithTheContext() {
    Map<String, String> responseHeaders = new LinkedHashMap<>();

    new BoosterCommandDispatcher(config)
        .dispatchCommand(envelope("PostComment", 1, Map.of("comment", "x")), context(responseHeaders));

    assertThat(responseHeaders).isEqualTo(Map.of("Test-Header", "test"));
    assertThat(REGISTER.get().responseHeaders()).isSameAs(responseHeaders);
  }

  @Test
  @SuppressWarnings("unchecked")
  void storesTheRegisteredEventsWithTheRequestAndUser() {
    CommandEnvelope envelope = envelope("PostComment", 1, Map.of("comment", "hello"));

    new BoosterCommandDispatcher(config).dispatchCommand(envelope, context(new LinkedHashMap<>()));

    assertThat(REGISTER.get().requestID()).isEqualTo(UUID.of("42"));
    assertThat(REGISTER.get().currentUser()).isEqualTo(envelope.currentUser());
    ArgumentCaptor<List<NonPersistedEventEnvelope>> stored = ArgumentCaptor.forClass(List.class);
    verify(events).store(stored.capture(), any());
    assertThat(stored.getValue()).singleElement().satisfies(event -> {
      assertThat(event.typeName()).isEqualTo("CommentPosted");
      assertThat(event.entityTypeName()).isEqualTo("Post");
      assertThat(event.requestID()).isEqualTo(UUID.of("42"));
      assertThat(event.currentUser()).isEqualTo(envelope.currentUser());
      assertThat(event.value()).isEqualTo(new CommentPosted(UUID.of("post-1"), "hello"));
    });
  }

  @Test
  void waitsForAsynchronousHandlers() {
    Object result = new BoosterCommandDispatcher(config)
        .dispatchCommand(envelope("SlowCommand", 1, Map.of("comment", "x")), context(new LinkedHashMap<>()));

    assertThat(HANDLED).containsExactly("slow finished");
    assertThat(result).isEqualTo("slow");
  }

  @Test
  void appliesTheBeforeHooksBeforeCreatingTheInstance() {
    new BoosterCommandDispatcher(config)
        .dispatchCommand(envelope("HookedComment", 1, Map.of("comment", "This test is good!")), context(new LinkedHashMap<>()));

    assertThat(HANDLED).containsExactly(new HookedComment("Look, I changed the message"));
  }

  @Test
  void chainsTheBeforeHooksInOrder() {
    new BoosterCommandDispatcher(config)
        .dispatchCommand(envelope("DoubleHookedComment", 1, Map.of("comment", "x")), context(new LinkedHashMap<>()));

    assertThat(HANDLED).containsExactly(new DoubleHookedComment("Yes, I changed it for a second time"));
  }

  @Test
  void rethrowsHandlerErrorsWithoutStoringEvents() {
    FAILURE.set(new IllegalArgumentException("boom"));

    assertThatThrownBy(() -> new BoosterCommandDispatcher(config)
        .dispatchCommand(envelope("PostComment", 1, Map.of("comment", "x")), context(new LinkedHashMap<>())))
        .isSameAs(FAILURE.get());
    verify(events, never()).store(anyList(), any());
  }

  @Test
  void routesHandlerErrorsToTheGlobalErrorHandler() {
    Decorators.registerClass(ErrorHandler.class);
    FAILURE.set(new IllegalArgumentException("boom"));

    assertThatThrownBy(() -> new BoosterCommandDispatcher(config)
        .dispatchCommand(envelope("PostComment", 1, Map.of("comment", "x")), context(new LinkedHashMap<>())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("handled: boom");
  }

  @Test
  void storesTheEventsWhenTheGlobalErrorHandlerSwallowsTheError() {
    Decorators.registerClass(ErrorHandler.class);
    FAILURE.set(new IllegalArgumentException("boom"));
    SWALLOW.set(true);

    Object result = new BoosterCommandDispatcher(config)
        .dispatchCommand(envelope("PostComment", 1, Map.of("comment", "x")), context(new LinkedHashMap<>()));

    assertThat(result).isNull();
    verify(events).store(anyList(), any());
  }

  @Test
  void migratesTheInputBeforeTheHooksAndAuthorizesTheOriginalEnvelope() {
    AtomicReference<CommandEnvelope> authorized = new AtomicReference<>();
    CommandMetadata metadata = config.commandHandlers.get("MigratedComment");
    CommandAuthorizer recording = (user, commandEnvelope) -> authorized.set(commandEnvelope);
    config.commandHandlers.put("MigratedComment", new CommandMetadata(MigratedComment.class, metadata.properties(),
        metadata.methods(), recording, metadata.before()));
    CommandEnvelope envelope = envelope("MigratedComment", 1, Map.of("text", "old field"));

    new BoosterCommandDispatcher(config).dispatchCommand(envelope, context(new LinkedHashMap<>()));

    assertThat(authorized.get()).isSameAs(envelope);
    assertThat(HANDLED).containsExactly(Map.of("comment", "old field"), new MigratedComment("old field"));
  }
}
