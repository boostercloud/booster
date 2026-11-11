package com.boostercloud.framework.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.core.coreconcepts.datamigration.events.BoosterDataMigrationStarted;
import com.boostercloud.framework.core.coreconcepts.datamigration.events.BoosterEntityMigrated;
import com.boostercloud.framework.core.coreconcepts.touchentity.events.BoosterEntityTouched;
import com.boostercloud.framework.core.decorators.Notification;
import com.boostercloud.framework.core.decorators.PartitionKey;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.concepts.NotificationMetadata;
import com.boostercloud.framework.types.concepts.ReducerMetadata;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.NonPersistedEventEnvelope;
import com.boostercloud.framework.types.envelope.SuperKindType;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.framework.types.errors.NotFoundError;
import com.boostercloud.framework.types.provider.ProviderEventsLibrary;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Port of {@code test/booster-register-handler.test.ts}, plus the untested branches. */
class BoosterRegisterHandlerTest extends BoosterTestSupport {

  public record SomeEntity(UUID id) {}

  public record SomeEvent(String someField) {
    public UUID entityID() {
      return UUID.of("42");
    }
  }

  public record SomeNotification() {}

  public record NotificationWithPartitionId(String partitionId) {}

  @Notification(topic = "my-topic")
  public record PartitionedNotification(@PartitionKey String key) {}

  public record EventWithoutEntityID(String someField) {}

  public record EventWithEmptyEntityID(String someField) {
    public String entityID() {
      return "";
    }
  }

  private BoosterConfig config;
  private ProviderEventsLibrary events;
  private final UserEnvelope user = UserEnvelope.builder().username("paco@example.com").roles(List.of("Paco")).build();

  @BeforeEach
  @SuppressWarnings("deprecation")
  void setUp() {
    config = Booster.config();
    config.eventStoreRetry.initialDelay = 1;
    config.eventStoreRetry.maxDelay = 2;
    ProviderLibrary provider = mock(ProviderLibrary.class);
    events = mock(ProviderEventsLibrary.class);
    when(provider.events()).thenReturn(events);
    when(events.store(anyList(), any())).thenReturn(List.of());
    config.setProvider(provider);
    config.reducers.put("SomeEvent", new ReducerMetadata(SomeEntity.class, "someMethod"));
  }

  private Register register(UserEnvelope currentUser) {
    return new Register(UUID.of("1234"), new LinkedHashMap<>(), RegisterHandler::flush, currentUser);
  }

  @Test
  void storesAllTheEventsInOneCall() {
    Register register = register(null);
    SomeEvent event1 = new SomeEvent("a");
    SomeEvent event2 = new SomeEvent("b");
    register.events(event1, event2);

    RegisterHandler.handle(config, register);

    NonPersistedEventEnvelope expected1 = NonPersistedEventEnvelope.builder().version(1).superKind(SuperKindType.DOMAIN)
        .entityID(UUID.of("42")).requestID(UUID.of("1234")).entityTypeName("SomeEntity").typeName("SomeEvent")
        .value(event1).build();
    verify(events, times(1)).store(List.of(expected1, expected1.toBuilder().value(event2).build()), config);
    assertThat(register.eventList()).hasSize(2);
  }

  @Test
  void doesNothingWithoutEvents() {
    RegisterHandler.handle(config, register(null));

    verifyNoInteractions(events);
  }

  @Test
  void wrapsDomainEvents() {
    SomeEvent event = new SomeEvent("a");

    assertThat(RegisterHandler.wrapEvent(config, event, register(user))).isEqualTo(NonPersistedEventEnvelope.builder()
        .version(1).superKind(SuperKindType.DOMAIN).entityID(UUID.of("42")).requestID(UUID.of("1234"))
        .entityTypeName("SomeEntity").value(event).currentUser(user).typeName("SomeEvent").build());
  }

  @Test
  void wrapsNotificationsWithTheDefaults() {
    config.notifications.put("SomeNotification", new NotificationMetadata(SomeNotification.class));
    SomeNotification notification = new SomeNotification();

    assertThat(RegisterHandler.wrapEvent(config, notification, register(user))).isEqualTo(NonPersistedEventEnvelope
        .builder().version(1).superKind(SuperKindType.DOMAIN).entityID(UUID.of("default")).requestID(UUID.of("1234"))
        .entityTypeName("defaultTopic").value(notification).currentUser(user).typeName("SomeNotification").build());
  }

  @Test
  void usesThePartitionIdOrThePartitionKeyAndTopicOfNotifications() {
    config.notifications.put("NotificationWithPartitionId", new NotificationMetadata(NotificationWithPartitionId.class));
    com.boostercloud.framework.core.decorators.Decorators.registerClass(PartitionedNotification.class);

    NonPersistedEventEnvelope withPartitionId =
        RegisterHandler.wrapEvent(config, new NotificationWithPartitionId("p-1"), register(user));
    NonPersistedEventEnvelope withPartitionKey =
        RegisterHandler.wrapEvent(config, new PartitionedNotification("k-1"), register(user));

    assertThat(withPartitionId.entityID()).isEqualTo(UUID.of("p-1"));
    assertThat(withPartitionId.entityTypeName()).isEqualTo("defaultTopic");
    assertThat(withPartitionKey.entityID()).isEqualTo(UUID.of("k-1"));
    assertThat(withPartitionKey.entityTypeName()).isEqualTo("my-topic");
  }

  @Test
  void wrapsInternalEventsEvenWhenAReducerIsRegistered() {
    config.reducers.put("BoosterEntityMigrated", new ReducerMetadata(SomeEntity.class, "someMethod"));
    BoosterEntityMigrated event =
        new BoosterEntityMigrated("oldEntity", UUID.of("oldEntityId"), "newEntityName", new SomeEntity(UUID.of("42")));

    assertThat(RegisterHandler.wrapEvent(config, event, register(user))).isEqualTo(NonPersistedEventEnvelope.builder()
        .version(1).superKind(SuperKindType.BOOSTER).entityTypeName("oldEntity").entityID(UUID.of("oldEntityId"))
        .requestID(UUID.of("1234")).value(event).currentUser(user).typeName("BoosterEntityMigrated").build());
  }

  @Test
  void wrapsTouchedEntitiesAndDataMigrationEvents() {
    Booster.configureBoosterConcepts();

    NonPersistedEventEnvelope touched =
        RegisterHandler.wrapEvent(config, new BoosterEntityTouched("Cart", UUID.of("c1")), register(null));
    NonPersistedEventEnvelope started =
        RegisterHandler.wrapEvent(config, new BoosterDataMigrationStarted("Migration1", "2026-01-01T00:00:00.000Z"), register(null));

    assertThat(touched.superKind()).isEqualTo(SuperKindType.BOOSTER);
    assertThat(touched.entityTypeName()).isEqualTo("Cart");
    assertThat(touched.entityID()).isEqualTo(UUID.of("c1"));
    assertThat(started.superKind()).isEqualTo(SuperKindType.BOOSTER);
    assertThat(started.entityTypeName()).isEqualTo("BoosterDataMigrationEntity");
    assertThat(started.entityID()).isEqualTo(UUID.of("Migration1"));
  }

  @Test
  void stampsTheCurrentSchemaVersion() {
    config.schemaMigrations.put("SomeEvent", new LinkedHashMap<>(Map.of(2, new com.boostercloud.framework.types.concepts
        .SchemaMigrationMetadata(Object.class, "m", 2, Object.class, Object.class))));

    assertThat(RegisterHandler.wrapEvent(config, new SomeEvent("a"), register(null)).version()).isEqualTo(2);
  }

  @Test
  void failsForEventsThatNoEntityReduces() {
    assertThatThrownBy(() -> RegisterHandler.wrapEvent(config, new EventWithoutEntityID("a"), register(null)))
        .isInstanceOf(NotFoundError.class)
        .hasMessage("Couldn't find information about event EventWithoutEntityID. Is the event handled by an entity?");
  }

  @Test
  void failsWhenTheEntityIDIsMissingOrEmpty() {
    config.reducers.put("EventWithoutEntityID", new ReducerMetadata(SomeEntity.class, "m"));
    config.reducers.put("EventWithEmptyEntityID", new ReducerMetadata(SomeEntity.class, "m"));

    String expected = "Event EventWithoutEntityID has no specification for the entity ID. Make sure to specify a"
        + " string-compatible value identifying the entity this event belongs to.\n\n      You can do it by:\n\n"
        + "      1. Adding an entityID method to the event";
    assertThatThrownBy(() -> RegisterHandler.wrapEvent(config, new EventWithoutEntityID("a"), register(null)))
        .isInstanceOf(IllegalStateException.class).hasMessage(expected);
    assertThatThrownBy(() -> RegisterHandler.wrapEvent(config, new EventWithEmptyEntityID("a"), register(null)))
        .hasMessage(expected.replace("EventWithoutEntityID", "EventWithEmptyEntityID"));
  }

  @Test
  void anEmptyPartitionKeyIsAnEmptyEntityID() {
    com.boostercloud.framework.core.decorators.Decorators.registerClass(PartitionedNotification.class);

    assertThatThrownBy(() -> RegisterHandler.wrapEvent(config, new PartitionedNotification(""), register(null)))
        .hasMessage("Event PartitionedNotification has an empty 'entityID' or the required 'entityID' method was not"
            + " implemented. Make sure to return a string-compatible value identifying the entity this event belongs to.");
  }

  @Test
  void retriesTheStorageWithBackoffAndRethrowsTheLastError() {
    RuntimeException failure = new IllegalStateException("store failed");
    when(events.store(anyList(), any())).thenThrow(failure);
    Register register = register(null);
    register.events(new SomeEvent("a"));

    assertThatThrownBy(() -> RegisterHandler.handle(config, register)).isSameAs(failure);
    verify(events, times(3)).store(anyList(), eq(config));
  }

  @Test
  void flushUsesTheGlobalConfigurationAndClearsTheRegister() {
    Register register = register(null);
    register.events(new SomeEvent("a"));

    register.flush();

    verify(events).store(anyList(), eq(Booster.config()));
    assertThat(register.eventList()).isEmpty();
    verify(events, never()).storeSnapshot(any(), any());
  }
}
