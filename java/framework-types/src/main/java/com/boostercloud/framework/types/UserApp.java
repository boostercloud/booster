package com.boostercloud.framework.types;

import java.util.Set;
import java.util.function.Consumer;

/**
 * The entry points of a Booster application, as seen by providers. Port of {@code UserApp}, the shape of a compiled
 * TypeScript project's {@code dist/index.js}. Core implements it and registers the instance in
 * {@link UserAppRegistry} during {@code Booster.start}; providers obtain it with {@link UserAppRegistry#get()}
 * (TypeScript: {@code require(dist/index.js)}).
 *
 * <p>Every entry point takes the provider-native request ({@code unknown} in TypeScript). TypeScript's nested
 * {@code Booster} object ({@code userApp.Booster.config}) is flattened into {@link #config()},
 * {@link #configureCurrentEnv(Consumer)} and {@link #configuredEnvironments()}.
 */
public interface UserApp {
  /**
   * @return the application configuration ({@code userApp.Booster.config})
   */
  BoosterConfig config();

  /**
   * Runs a configurator on the configuration of the current environment ({@code Booster.configureCurrentEnv}).
   *
   * @param configurator the configurator
   */
  void configureCurrentEnv(Consumer<BoosterConfig> configurator);

  /**
   * @return the environments named in {@code Booster.configure(env, ...)}
   */
  Set<String> configuredEnvironments();

  /**
   * Stores and dispatches events.
   *
   * @param rawEvents the provider-native events payload
   */
  void boosterEventDispatcher(Object rawEvents);

  /**
   * Serves a GraphQL request (HTTP or websocket).
   *
   * @param rawRequest the provider-native request
   * @return the provider-native response
   */
  Object boosterServeGraphQL(Object rawRequest);

  /**
   * Runs a scheduled command.
   *
   * @param rawRequest the provider-native trigger
   */
  void boosterTriggerScheduledCommands(Object rawRequest);

  /**
   * Deprecated alias of {@link #boosterTriggerScheduledCommands(Object)} (the name {@code config.scheduledTaskHandler}
   * refers to).
   *
   * @param rawRequest the provider-native trigger
   * @deprecated [EOL v3] use {@link #boosterTriggerScheduledCommands(Object)}
   */
  @Deprecated
  default void boosterTriggerScheduledCommand(Object rawRequest) {
    boosterTriggerScheduledCommands(rawRequest);
  }

  /**
   * Notifies the subscribers of changed read models.
   *
   * @param rawRequest the provider-native change notification
   */
  void boosterNotifySubscribers(Object rawRequest);

  /**
   * Runs a rocket function.
   *
   * @param rawRequest the provider-native request
   * @return the function result
   */
  Object boosterRocketDispatcher(Object rawRequest);

  /**
   * Consumes events from the event stream.
   *
   * @param rawEvent the provider-native stream payload
   * @return the provider-native result
   */
  Object boosterConsumeEventStream(Object rawEvent);

  /**
   * Produces events to the event stream.
   *
   * @param rawEvent the provider-native events payload
   * @return the provider-native result
   */
  Object boosterProduceEventStream(Object rawEvent);

  /**
   * Serves a health request.
   *
   * @param rawRequest the provider-native request
   * @return the provider-native response
   */
  Object boosterHealth(Object rawRequest);
}
