package com.boostercloud.framework.core;

import com.boostercloud.framework.core.sensor.health.BoosterHealthService;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.UserApp;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The {@link UserApp} of a started application: the Java form of the functions TypeScript exports from
 * {@code framework-core/src/index.ts}, which the user re-exports from its {@code src/index.ts} for the cloud runtime.
 * Each one is a thin delegate that passes the global {@link Booster#config()}; instance dispatchers are created per
 * call, as in TypeScript. {@link Booster#start(Class)} registers an instance in
 * {@link com.boostercloud.framework.types.UserAppRegistry}, where providers find it.
 */
public class BoosterUserApp implements UserApp {

  /** Creates the application view (stateless). */
  public BoosterUserApp() {}

  @Override
  public BoosterConfig config() {
    return Booster.config();
  }

  @Override
  public void configureCurrentEnv(Consumer<BoosterConfig> configurator) {
    Booster.configureCurrentEnv(configurator);
  }

  @Override
  public Set<String> configuredEnvironments() {
    return Booster.configuredEnvironments();
  }

  /** {@code boosterEventDispatcher(rawEvents)}: {@code BoosterEventDispatcher.dispatch(rawEvents, Booster.config)}. */
  @Override
  public void boosterEventDispatcher(Object rawEvents) {
    BoosterEventDispatcher.dispatch(rawEvents, Booster.config());
  }

  /** {@code boosterServeGraphQL(rawRequest)}: {@code new BoosterGraphQLDispatcher(Booster.config).dispatch(raw)}. */
  @Override
  public Object boosterServeGraphQL(Object rawRequest) {
    return new BoosterGraphQLDispatcher(Booster.config()).dispatch(rawRequest);
  }

  /**
   * {@code boosterTriggerScheduledCommands(rawRequest)} (the deprecated singular alias is the inherited default
   * method): {@code new BoosterScheduledCommandDispatcher(Booster.config).dispatch(raw)}.
   */
  @Override
  public void boosterTriggerScheduledCommands(Object rawRequest) {
    new BoosterScheduledCommandDispatcher(Booster.config()).dispatch(rawRequest);
  }

  /** {@code boosterNotifySubscribers(rawRequest)}: {@code new BoosterSubscribersNotifier(config).dispatch(raw)}. */
  @Override
  public void boosterNotifySubscribers(Object rawRequest) {
    new BoosterSubscribersNotifier(Booster.config()).dispatch(rawRequest);
  }

  /** {@code boosterRocketDispatcher(rawRequest)}: {@code new BoosterRocketDispatcher(config).dispatch(raw)}. */
  @Override
  public Object boosterRocketDispatcher(Object rawRequest) {
    return new BoosterRocketDispatcher(Booster.config()).dispatch(rawRequest);
  }

  /**
   * {@code boosterConsumeEventStream(rawEvent)}: {@code BoosterEventStreamConsumer.consume(raw, Booster.config)}.
   *
   * @return {@code null} (TypeScript resolves to {@code undefined})
   */
  @Override
  public Object boosterConsumeEventStream(Object rawEvent) {
    BoosterEventStreamConsumer.consume(rawEvent, Booster.config());
    return null;
  }

  /**
   * {@code boosterProduceEventStream(rawEvent)}: {@code BoosterEventStreamProducer.produce(raw, Booster.config)}.
   *
   * @return {@code null} (TypeScript resolves to {@code undefined})
   */
  @Override
  public Object boosterProduceEventStream(Object rawEvent) {
    BoosterEventStreamProducer.produce(rawEvent, Booster.config());
    return null;
  }

  /** {@code boosterHealth(request)}: {@code new BoosterHealthService(Booster.config).boosterHealth(request)}. */
  @Override
  public Object boosterHealth(Object rawRequest) {
    return new BoosterHealthService(Booster.config()).boosterHealth(rawRequest);
  }
}
