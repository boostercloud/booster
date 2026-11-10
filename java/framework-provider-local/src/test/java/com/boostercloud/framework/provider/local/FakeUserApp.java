package com.boostercloud.framework.provider.local;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.UserApp;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A stand-in for core: implements the {@link UserApp} entry points the local provider calls, and dispatches events the
 * way core does (rawToEnvelopes, then {@code storeDispatched} for each event, processing only the new ones).
 */
public class FakeUserApp implements UserApp {

  /** The configuration. */
  public final BoosterConfig config;
  /** Every batch handed to {@code boosterEventDispatcher}. */
  public final List<List<EventEnvelope>> dispatchedBatches = Collections.synchronizedList(new ArrayList<>());
  /** Events for which {@code storeDispatched} answered {@code true}. */
  public final List<EventEnvelope> processedEvents = Collections.synchronizedList(new ArrayList<>());
  /** Events for which {@code storeDispatched} answered {@code false}. */
  public final List<EventEnvelope> skippedEvents = Collections.synchronizedList(new ArrayList<>());
  /** Raw GraphQL requests. */
  public final List<Object> graphQLRequests = Collections.synchronizedList(new ArrayList<>());
  /** Raw subscription notifications. */
  public final List<Object> notifications = Collections.synchronizedList(new ArrayList<>());
  /** Raw health requests. */
  public final List<Object> healthRequests = Collections.synchronizedList(new ArrayList<>());

  private volatile ProviderLibrary provider;
  private volatile Consumer<EventEnvelope> onEvent = event -> { };
  private volatile Function<Object, Object> onGraphQL = request -> null;
  private volatile Consumer<Object> onNotification = request -> { };

  /**
   * @param config the configuration
   */
  public FakeUserApp(BoosterConfig config) {
    this.config = config;
  }

  /**
   * @param newProvider the provider used to dispatch events
   * @return this app
   */
  public FakeUserApp provider(ProviderLibrary newProvider) {
    this.provider = newProvider;
    return this;
  }

  /**
   * @param handler runs for every processed event
   * @return this app
   */
  public FakeUserApp onEvent(Consumer<EventEnvelope> handler) {
    this.onEvent = handler;
    return this;
  }

  /**
   * @param handler answers GraphQL requests
   * @return this app
   */
  public FakeUserApp onGraphQL(Function<Object, Object> handler) {
    this.onGraphQL = handler;
    return this;
  }

  /**
   * @param handler runs for every notification
   * @return this app
   */
  public FakeUserApp onNotification(Consumer<Object> handler) {
    this.onNotification = handler;
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
    List<EventEnvelope> events = provider.events().rawToEnvelopes(rawEvents);
    dispatchedBatches.add(List.copyOf(events));
    for (EventEnvelope event : events) {
      if (provider.events().storeDispatched(event, config)) {
        processedEvents.add(event);
        onEvent.accept(event);
      } else {
        skippedEvents.add(event);
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
    throw new UnsupportedOperationException();
  }

  @Override
  public void boosterNotifySubscribers(Object rawRequest) {
    notifications.add(rawRequest);
    onNotification.accept(rawRequest);
  }

  @Override
  public Object boosterRocketDispatcher(Object rawRequest) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Object boosterConsumeEventStream(Object rawEvent) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Object boosterProduceEventStream(Object rawEvent) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Object boosterHealth(Object rawRequest) {
    healthRequests.add(rawRequest);
    return null;
  }
}
