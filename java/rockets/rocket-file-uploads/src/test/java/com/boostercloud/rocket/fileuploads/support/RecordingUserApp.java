package com.boostercloud.rocket.fileuploads.support;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.UserApp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** A stand-in for core that records the rocket function requests the local infrastructure dispatches. */
public final class RecordingUserApp implements UserApp {

  private final BoosterConfig config;
  /** The requests received by {@link #boosterRocketDispatcher(Object)}, in order. */
  public final List<Object> rocketRequests = Collections.synchronizedList(new ArrayList<>());

  /**
   * @param config the configuration
   */
  public RecordingUserApp(BoosterConfig config) {
    this.config = config;
  }

  /**
   * @return the names ({@code name}) of the recorded requests
   */
  public List<Object> names() {
    synchronized (rocketRequests) {
      return rocketRequests.stream().<Object>map(request -> ((Map<?, ?>) request).get("name")).toList();
    }
  }

  @Override
  public BoosterConfig config() {
    return config;
  }

  @Override
  public void configureCurrentEnv(Consumer<BoosterConfig> configurator) {
    configurator.accept(config);
  }

  @Override
  public Set<String> configuredEnvironments() {
    return Set.of(config.environmentName);
  }

  @Override
  public void boosterEventDispatcher(Object rawEvents) {}

  @Override
  public Object boosterServeGraphQL(Object rawRequest) {
    throw new UnsupportedOperationException("GraphQL is not served by this fake");
  }

  @Override
  public void boosterTriggerScheduledCommands(Object rawRequest) {}

  @Override
  public void boosterNotifySubscribers(Object rawRequest) {}

  @Override
  public Object boosterRocketDispatcher(Object rawRequest) {
    rocketRequests.add(rawRequest);
    return null;
  }

  @Override
  public Object boosterConsumeEventStream(Object rawEvent) {
    return null;
  }

  @Override
  public Object boosterProduceEventStream(Object rawEvent) {
    return null;
  }

  @Override
  public Object boosterHealth(Object rawRequest) {
    return null;
  }
}
