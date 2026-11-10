package com.boostercloud.framework.types.instrumentation;

/**
 * The traced actions of the framework. Port of the string enum {@code TraceActionTypes}; the value of each constant is
 * its name ({@link #name()}). The action type passed to trace handlers is a plain string, so applications may use
 * their own action names too.
 */
public enum TraceActionTypes {
  /** {@code CUSTOM}. */
  CUSTOM,
  /** {@code EVENT_HANDLERS_PROCESS}. */
  EVENT_HANDLERS_PROCESS,
  /** {@code HANDLE_EVENT}. */
  HANDLE_EVENT,
  /** {@code DISPATCH_ENTITY_TO_EVENT_HANDLERS}. */
  DISPATCH_ENTITY_TO_EVENT_HANDLERS,
  /** {@code DISPATCH_EVENTS}. */
  DISPATCH_EVENTS,
  /** {@code CONSUME_STREAM_EVENTS}. */
  CONSUME_STREAM_EVENTS,
  /** {@code PRODUCE_STREAM_EVENTS}. */
  PRODUCE_STREAM_EVENTS,
  /** {@code FETCH_ENTITY_SNAPSHOT}. */
  FETCH_ENTITY_SNAPSHOT,
  /** {@code STORE_SNAPSHOT}. */
  STORE_SNAPSHOT,
  /** {@code LOAD_LATEST_SNAPSHOT}. */
  LOAD_LATEST_SNAPSHOT,
  /** {@code LOAD_EVENT_STREAM_SINCE}. */
  LOAD_EVENT_STREAM_SINCE,
  /** {@code ENTITY_REDUCER}. */
  ENTITY_REDUCER,
  /** {@code READ_MODEL_FIND_BY_ID}. */
  READ_MODEL_FIND_BY_ID,
  /** {@code GRAPHQL_READ_MODEL_SEARCH}. */
  GRAPHQL_READ_MODEL_SEARCH,
  /** {@code READ_MODEL_SEARCH}. */
  READ_MODEL_SEARCH,
  /** {@code COMMAND_HANDLER}. */
  COMMAND_HANDLER,
  /** {@code MIGRATION_RUN}. */
  MIGRATION_RUN,
  /** {@code GRAPHQL_DISPATCH}. */
  GRAPHQL_DISPATCH,
  /** {@code GRAPHQL_RUN_OPERATION}. */
  GRAPHQL_RUN_OPERATION,
  /** {@code SCHEDULED_COMMAND_HANDLER}. */
  SCHEDULED_COMMAND_HANDLER,
  /** {@code DISPATCH_SUBSCRIBER_NOTIFIER}. */
  DISPATCH_SUBSCRIBER_NOTIFIER,
  /** {@code READ_MODEL_SCHEMA_MIGRATOR_MIGRATE}. */
  READ_MODEL_SCHEMA_MIGRATOR_MIGRATE,
  /** {@code SCHEMA_MIGRATOR_MIGRATE}. */
  SCHEMA_MIGRATOR_MIGRATE;

  /**
   * @return the action name (same as {@link #name()})
   */
  public String value() {
    return name();
  }
}
