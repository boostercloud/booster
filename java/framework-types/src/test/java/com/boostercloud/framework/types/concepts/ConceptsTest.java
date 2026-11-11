package com.boostercloud.framework.types.concepts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.metadata.ClassMetadata;
import com.boostercloud.metadata.TypeGroup;
import com.boostercloud.metadata.TypeMetadata;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Runtime behaviour of the concept types (no TypeScript tests exist for them). */
class ConceptsTest {

  private static final String UUID_V4 = "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$";

  record Cart(UUID id) {}

  @Nested
  class Identifiers {
    @Test
    void acceptsAnyStringAsUuid() {
      assertThat(UUID.of("1234").value()).isEqualTo("1234");
      assertThat(UUID.of("cart-42")).isEqualTo(UUID.of("cart-42"));
      assertThat(UUID.of("cart-42").toString()).isEqualTo("cart-42");
      assertThat(UUID.ofNullable(null)).isNull();
      assertThatThrownBy(() -> UUID.of(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void generatesRandomVersion4Uuids() {
      assertThat(UUID.generate().value()).matches(UUID_V4);
      assertThat(UUID.generate()).isNotEqualTo(UUID.generate());
    }

    @Test
    void generatesTimeKeysFromAMoment() {
      assertThat(TimeKey.generate(1234).value()).matches("^1234-" + UUID_V4.substring(1));
      assertThat(TimeKey.generate().value()).matches("^\\d{13}-.*");
      assertThat(TimeKey.of("1").compareTo(TimeKey.of("2"))).isNegative();
    }

    @Test
    void describesUuidFieldsWithTheUuidName() {
      TypeMetadata id = ClassMetadata.of(Cart.class).field("id").orElseThrow().typeInfo();
      assertThat(id.name()).isEqualTo("UUID");
      assertThat(id.typeGroup()).isEqualTo(TypeGroup.CLASS);
      assertThat(id.isExternal()).isTrue();
    }
  }

  @Nested
  class TheRegister {
    @Test
    void appendsEventsInOrderAndIsFluent() {
      Register register = new Register(UUID.of("1234"), new HashMap<>(), r -> {});
      assertThat(register.events("a").events("b", "c")).isSameAs(register);
      assertThat(register.events()).isSameAs(register);
      assertThat(register.eventList()).containsExactly("a", "b", "c");
    }

    @Test
    void sharesTheResponseHeadersMap() {
      Map<String, String> headers = new HashMap<>();
      Register register = new Register(UUID.of("1"), headers, r -> {});
      register.responseHeaders().put("X-Test", "yes");
      assertThat(headers).containsEntry("X-Test", "yes");
    }

    @Test
    void flushesThroughTheFlusherAndClearsTheListInPlace() {
      FlusherFunction flusher = mock(FlusherFunction.class);
      UserEnvelope user = new UserEnvelope(null, "u", List.of(), Map.of(), null);
      Register register = new Register(UUID.of("1"), new HashMap<>(), flusher, user);
      List<Object> list = register.eventList();
      register.events("a");
      register.flush();
      verify(flusher).flush(register);
      assertThat(list).isEmpty();
      assertThat(register.currentUser()).isSameAs(user);
      assertThat(register.context()).isNull();
    }

    @Test
    void keepsTheEventsWhenTheFlusherFails() {
      List<Object> seen = new ArrayList<>();
      Register register = new Register(UUID.of("1"), new HashMap<>(), r -> {
        seen.addAll(r.eventList());
        throw new IllegalStateException("store failed");
      });
      register.events("a", "b");
      assertThatThrownBy(register::flush).hasMessage("store failed");
      assertThat(register.eventList()).containsExactly("a", "b");
      assertThat(seen).containsExactly("a", "b");
    }
  }

  @Nested
  class Enums {
    @Test
    void keepTheTypeScriptNumericValues() {
      assertThat(ReadModelAction.DELETE.value()).isZero();
      assertThat(ReadModelAction.NOTHING.value()).isEqualTo(1);
      assertThat(ProjectionInfoReason.ENTITY_PROJECTED.value()).isZero();
      assertThat(ProjectionInfoReason.ENTITY_DELETED.value()).isEqualTo(1);
      assertThat(DataMigrationStatus.RUNNING.value()).isZero();
      assertThat(DataMigrationStatus.FINISHED.value()).isEqualTo(1);
      assertThat(DataMigrationStatus.fromValue("FINISHED")).isEqualTo(DataMigrationStatus.FINISHED);
      assertThatThrownBy(() -> DataMigrationStatus.fromValue(7)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void serializeSignUpMethodsAndModesWithTheirLiterals() {
      assertThat(BoosterJson.toJson(List.of(SignUpMethod.EMAIL, SignUpMethod.PHONE))).isEqualTo("[\"email\",\"phone\"]");
      assertThat(BoosterJson.toJson(RoleMetadata.defaults())).isEqualTo("{\"auth\":{}}");
      assertThat(BoosterJson.toJson(EventStreamConfiguration.Mode.EXPONENTIAL)).isEqualTo("\"exponential\"");
    }
  }

  @Nested
  class Projections {
    @SuppressWarnings("unchecked")
    static ProjectionResult<Cart> project(Cart cart) {
      if (cart == null) {
        return ReadModelAction.DELETE;
      }
      if (cart.id().value().isEmpty()) {
        return ProjectionResult.nothing();
      }
      return ProjectionResult.of(cart);
    }

    private String outcome(ProjectionResult<?> result) {
      return switch (result) {
        case ProjectionResult.Upsert<?> upsert -> "upsert " + upsert.value();
        case ReadModelAction action -> action.name();
      };
    }

    @Test
    void distinguishesUpsertsFromActions() {
      assertThat(outcome(project(null))).isEqualTo("DELETE");
      assertThat(outcome(project(new Cart(UUID.of(""))))).isEqualTo("NOTHING");
      assertThat(outcome(project(new Cart(UUID.of("1"))))).isEqualTo("upsert Cart[id=1]");
      assertThat(outcome(ProjectionResult.delete())).isEqualTo("DELETE");
      assertThatThrownBy(() -> ProjectionResult.of(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void interpretsRawReturnValues() {
      assertThat(ProjectionResult.fromReturnValue(ReadModelAction.NOTHING)).isSameAs(ReadModelAction.NOTHING);
      assertThat(ProjectionResult.fromReturnValue(new Cart(UUID.of("1")))).isEqualTo(ProjectionResult.of(new Cart(UUID.of("1"))));
      assertThat(ProjectionResult.fromReturnValue(null)).isNull();
    }

    @Test
    void comparesJoinKeys() {
      ReadModelJoinKeyFunction<Cart> function = cart -> Map.of("id", Map.of("eq", cart.id()));
      assertThat(JoinKey.property("id")).isEqualTo(JoinKey.property("id"));
      assertThat(JoinKey.query(function)).isEqualTo(JoinKey.query(function));
      assertThat(JoinKey.property("id").toString()).isEqualTo("id");
      ProjectionMetadata metadata = new ProjectionMetadata(Cart.class, "project", JoinKey.property("id"));
      assertThat(metadata.projectionMethod()).isEqualTo("Cart.project");
      AtomicReference<String> kind = new AtomicReference<>();
      switch (metadata.joinKey()) {
        case JoinKey.EntityProperty property -> kind.set("property " + property.propertyName());
        case JoinKey.Query query -> kind.set("query");
      }
      assertThat(kind.get()).isEqualTo("property id");
    }
  }

  @Nested
  class Authorization {
    static final class Admin {}

    @Test
    void modelsTheAuthorizeUnion() {
      Authorize<CommandAuthorizer> all = Authorize.all();
      Authorize<CommandAuthorizer> roles = Authorize.roles(Admin.class);
      CommandAuthorizer custom = (user, command) -> {};
      Authorize<CommandAuthorizer> function = Authorize.custom(custom);
      assertThat(all).isInstanceOf(Authorize.All.class);
      assertThat(((Authorize.Roles<CommandAuthorizer>) roles).roleNames()).containsExactly("Admin");
      assertThat(((Authorize.Custom<CommandAuthorizer>) function).authorizer()).isSameAs(custom);
      assertThat(new CommandRoleAccess(null).authorize()).isNull();
      assertThat(HealthRoleAccess.all().authorize()).isInstanceOf(Authorize.All.class);
    }

    @Test
    void defaultsEmptyHooksAndAuthMetadata() {
      assertThat(new CommandFilterHooks(null).before()).isEmpty();
      assertThat(new RoleMetadata(null).auth()).isEqualTo(new AuthMetadata(null, null));
      assertThat(new QueryMetadata(Cart.class, List.of(), List.of(), (u, q) -> {}, null).before()).isEmpty();
    }
  }

  @Nested
  class EventStreams {
    @Test
    void enforcesTheUnionShape() {
      assertThat(BoosterJson.toJson(EventStreamConfiguration.disabled())).isEqualTo("{\"enabled\":false}");
      EventStreamConfiguration enabled = EventStreamConfiguration.enabled(
          new EventStreamConfiguration.Parameters("t", 3, 1, 86400, 5, EventStreamConfiguration.Mode.FIXED));
      assertThat(BoosterJson.toJson(enabled)).isEqualTo(
          "{\"enabled\":true,\"parameters\":{\"streamTopic\":\"t\",\"partitionCount\":3,\"messageRetention\":1,"
              + "\"dedupTtl\":86400,\"maxRetries\":5,\"mode\":\"fixed\"}}");
      assertThat(BoosterJson.fromJson(BoosterJson.toJson(enabled), EventStreamConfiguration.class)).isEqualTo(enabled);
      assertThatThrownBy(() -> new EventStreamConfiguration(true, null)).isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> new EventStreamConfiguration(false, EventStreamConfiguration.Parameters.of("t", 1, 1)))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readsDecodedTokenClaims() {
      DecodedToken token = new DecodedToken(Map.of("kid", "key-1"), Map.of("sub", "user-17", "email", "a@b.c"));
      assertThat(token.kid()).isEqualTo("key-1");
      assertThat(token.sub()).isEqualTo("user-17");
      assertThat(token.email()).isEqualTo("a@b.c");
      assertThat(token.phoneNumber()).isNull();
    }
  }
}
