package com.boostercloud.framework.provider.local.infrastructure;

import com.boostercloud.framework.provider.local.library.ApiAdapter;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.UserApp;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A stand-in for core: records what the local infrastructure hands to the application entry points and answers with
 * configurable functions. Events are dispatched like core does (each one through {@code storeDispatched}).
 */
public class FakeUserApp implements UserApp {

  public final BoosterConfig config;
  public final List<Object> graphQLRequests = Collections.synchronizedList(new ArrayList<>());
  public final List<Object> healthRequests = Collections.synchronizedList(new ArrayList<>());
  public final List<Object> scheduledTriggers = Collections.synchronizedList(new ArrayList<>());
  public final List<Object> rocketRequests = Collections.synchronizedList(new ArrayList<>());
  public final List<EventEnvelope> processedEvents = Collections.synchronizedList(new ArrayList<>());

  private volatile ProviderLibrary provider;
  private volatile Function<Object, Object> onGraphQL =
      request -> ApiAdapter.requestSucceeded(Map.of("data", Map.of("ok", true)), Map.of());
  private volatile Function<Object, Object> onHealth =
      request -> ApiAdapter.healthRequestResult(List.of(Map.of("status", "UP")), true);
  private volatile Consumer<Object> onScheduled = trigger -> { };
  private volatile Consumer<Object> onDispatch = events -> { };

  public FakeUserApp(BoosterConfig config) {
    this.config = config;
  }

  public FakeUserApp provider(ProviderLibrary newProvider) {
    this.provider = newProvider;
    return this;
  }

  public ProviderLibrary provider() {
    return provider;
  }

  public FakeUserApp onGraphQL(Function<Object, Object> handler) {
    this.onGraphQL = handler;
    return this;
  }

  public FakeUserApp onHealth(Function<Object, Object> handler) {
    this.onHealth = handler;
    return this;
  }

  public FakeUserApp onDispatch(Consumer<Object> handler) {
    this.onDispatch = handler;
    return this;
  }

  public FakeUserApp onScheduled(Consumer<Object> handler) {
    this.onScheduled = handler;
    return this;
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
  public void boosterEventDispatcher(Object rawEvents) {
    onDispatch.accept(rawEvents);
    for (EventEnvelope event : provider.events().rawToEnvelopes(rawEvents)) {
      if (provider.events().storeDispatched(event, config)) {
        processedEvents.add(event);
      }
    }
  }

  @Override
  public Object boosterServeGraphQL(Object rawRequest) {
    graphQLRequests.add(rawRequest);
    return onGraphQL.apply(rawRequest);
  }

  @Override
  public void boosterTriggerScheduledCommands(Object rawRequest) {
    scheduledTriggers.add(rawRequest);
    onScheduled.accept(rawRequest);
  }

  @Override
  public void boosterNotifySubscribers(Object rawRequest) {
    // not used by these tests
  }

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
    healthRequests.add(rawRequest);
    return onHealth.apply(rawRequest);
  }
}
