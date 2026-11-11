package com.boostercloud.framework.core;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.common.Retrier;
import com.boostercloud.framework.core.coreconcepts.datamigration.events.BoosterDataMigrationFinished;
import com.boostercloud.framework.core.coreconcepts.datamigration.events.BoosterDataMigrationStarted;
import com.boostercloud.framework.core.coreconcepts.datamigration.events.BoosterEntityMigrated;
import com.boostercloud.framework.core.coreconcepts.touchentity.events.BoosterEntityTouched;
import com.boostercloud.framework.core.internal.Js;
import com.boostercloud.framework.core.internal.UserCode;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.ReducerMetadata;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.NonPersistedEventEnvelope;
import com.boostercloud.framework.types.envelope.SuperKindType;
import com.boostercloud.framework.types.errors.NotFoundError;
import com.boostercloud.metadata.ClassMetadata;
import com.boostercloud.metadata.PropertyMetadata;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

/**
 * Port of {@code booster-register-handler.ts}: turns the events and notifications accumulated in a {@link Register}
 * into {@link NonPersistedEventEnvelope}s and stores them through the provider with
 * {@link Retrier#retryWithBackoff retry/backoff} ({@code config.eventStoreRetry}). {@link #flush(Register)} is the
 * {@code FlusherFunction} of every register created by the framework.
 */
public final class RegisterHandler {

  /** The event types stored with {@code superKind: booster}. */
  static final List<String> BOOSTER_EVENTS_TYPES_NAMES = List.of(
      BoosterEntityMigrated.class.getSimpleName(),
      BoosterDataMigrationStarted.class.getSimpleName(),
      BoosterDataMigrationFinished.class.getSimpleName(),
      BoosterEntityTouched.class.getSimpleName());

  /** The entity type of notifications without a topic. */
  static final String DEFAULT_TOPIC = "defaultTopic";

  /** The entity id of notifications without a partition key or {@code partitionId}. */
  static final String DEFAULT_PARTITION_ID = "default";

  private RegisterHandler() {}

  /**
   * {@code handle(config, register)}: does nothing for an empty register (the provider is not touched); otherwise
   * wraps every event in registration order and stores them in one {@code provider.events().store} call. The wrapping
   * runs inside the retried block, as in TypeScript, so wrapping errors are retried too. The register's event list is
   * not cleared (only {@code Register.flush()} does that).
   *
   * @param config the configuration
   * @param register the register
   */
  public static void handle(BoosterConfig config, Register register) {
    if (register.eventList().isEmpty()) {
      return;
    }
    Logger logger = Loggers.getLogger(config, "RegisterHandler#handle");
    Retrier.retryWithBackoff(
        () -> config.provider().events().store(
            register.eventList().stream().map(event -> wrapEvent(config, event, register)).toList(), config),
        config.eventStoreRetry,
        logger);
  }

  /**
   * {@code flush(record)}: {@link #handle(BoosterConfig, Register)} with the <b>global</b> {@link Booster#config()}
   * (not the configuration of the dispatcher that created the register).
   *
   * @param register the register
   */
  public static void flush(Register register) {
    handle(Booster.config(), register);
  }

  /**
   * {@code wrapEvent(config, event, register)}.
   *
   * @param config the configuration
   * @param event the event or notification instance
   * @param register the register (request id and user are copied; its context is not)
   * @return the envelope
   * @throws NotFoundError {@code Couldn't find information about event <E>. Is the event handled by an entity?}
   * @throws IllegalStateException when the entity id is empty or missing
   */
  static NonPersistedEventEnvelope wrapEvent(BoosterConfig config, Object event, Register register) {
    String eventTypeName = event.getClass().getSimpleName();
    String entityTypeName = getTopicName(eventTypeName, event, config);
    if (!Js.isTruthy(entityTypeName)) {
      throw new NotFoundError(
          "Couldn't find information about event " + eventTypeName + ". Is the event handled by an entity?");
    }
    UUID entityID = getPartitionKey(event, config);
    if (!Js.isTruthy(entityID)) {
      throw new IllegalStateException("Event " + eventTypeName + " has an empty 'entityID' or the required 'entityID'"
          + " method was not implemented. Make sure to return a string-compatible value identifying the entity this"
          + " event belongs to.");
    }
    return NonPersistedEventEnvelope.builder()
        .version(config.currentVersionFor(eventTypeName))
        .superKind(getSuperKind(eventTypeName))
        .entityID(entityID)
        .requestID(register.requestID())
        .currentUser(register.currentUser())
        .entityTypeName(entityTypeName)
        .typeName(eventTypeName)
        .value(event)
        .build();
  }

  private static SuperKindType getSuperKind(String eventTypeName) {
    return BOOSTER_EVENTS_TYPES_NAMES.contains(eventTypeName) ? SuperKindType.BOOSTER : SuperKindType.DOMAIN;
  }

  private static String getTopicName(String eventTypeName, Object event, BoosterConfig config) {
    if (event instanceof BoosterEntityMigrated migrated) {
      return migrated.oldEntityName();
    }
    if (event instanceof BoosterEntityTouched touched) {
      return touched.entityName();
    }
    if (config.notifications.containsKey(eventTypeName)) {
      String topic = config.eventToTopic.get(eventTypeName);
      return topic != null ? topic : DEFAULT_TOPIC;
    }
    ReducerMetadata reducerInfo = config.reducers.get(eventTypeName);
    return reducerInfo == null || reducerInfo.type() == null ? null : reducerInfo.type().getSimpleName();
  }

  private static UUID getPartitionKey(Object event, BoosterConfig config) {
    String eventName = event.getClass().getSimpleName();
    String entityIdField = config.partitionKeys.get(eventName);
    if (Js.isTruthy(entityIdField)) {
      Optional<Object> value = propertyValue(event, entityIdField);
      if (value.isPresent() && isString(value.get())) {
        return UUID.of(value.get().toString());
      }
    }
    if (config.notifications.containsKey(eventName)) {
      return getDefaultNotificationPartitionId(event);
    }
    return getDefaultStateEventEntityId(event);
  }

  private static UUID getDefaultStateEventEntityId(Object event) {
    Optional<Method> entityIDMethod = UserCode.findInstance(event.getClass(), "entityID");
    Object entityID = entityIDMethod.isPresent() ? UserCode.invoke(entityIDMethod.get(), event) : null;
    if (Js.isTruthy(entityID)) {
      return entityID instanceof UUID uuid ? uuid : UUID.of(Js.toString(entityID));
    }
    throw new IllegalStateException("Event " + event.getClass().getSimpleName() + " has no specification for the entity"
        + " ID. Make sure to specify a string-compatible value identifying the entity this event belongs to.\n\n"
        + "      You can do it by:\n\n      1. Adding an entityID method to the event");
  }

  private static UUID getDefaultNotificationPartitionId(Object event) {
    Optional<Object> partitionId = propertyValue(event, "partitionId");
    if (partitionId.isPresent() && Js.isTruthy(partitionId.get()) && isString(partitionId.get())) {
      return UUID.of(partitionId.get().toString());
    }
    return UUID.of(DEFAULT_PARTITION_ID);
  }

  private static Optional<Object> propertyValue(Object instance, String propertyName) {
    Optional<PropertyMetadata> property = ClassMetadata.of(instance.getClass()).field(propertyName);
    return property.map(metadata -> metadata.readValue(instance));
  }

  /** TypeScript {@code typeof value === 'string'}: Booster's {@code UUID} is a string in TypeScript. */
  private static boolean isString(Object value) {
    return value instanceof CharSequence || value instanceof UUID;
  }
}
