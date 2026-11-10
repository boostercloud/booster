package com.boostercloud.framework.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.EventMetadata;
import com.boostercloud.framework.types.concepts.NotificationMetadata;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EventParametersFilterByType;
import com.boostercloud.framework.types.envelope.EventSearchParameters;
import com.boostercloud.framework.types.envelope.EventSearchResponse;
import com.boostercloud.framework.types.provider.ProviderEventsLibrary;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@code eventSearch} through {@link Booster#events} (port of the {@code Booster.events} cases of
 * {@code test/booster.test.ts}), plus ordering and logging.
 */
class BoosterEventSearchTest extends BoosterTestSupport {

  public record TestEvent(UUID id) {
    public UUID entityID() {
      return id;
    }

    public UUID getId() {
      return id;
    }
  }

  public record TestEventNoRegistered(UUID id) {}

  public record TestNotification(UUID id) {
    public UUID getId() {
      return id;
    }
  }

  private BoosterConfig config;
  private ProviderEventsLibrary events;
  private Logger logger;
  private final EventSearchParameters request = new EventParametersFilterByType("TestEvent", null, null, null);

  @BeforeEach
  @SuppressWarnings("deprecation")
  void setUp() {
    config = Booster.config();
    ProviderLibrary provider = mock(ProviderLibrary.class);
    events = mock(ProviderEventsLibrary.class);
    when(provider.events()).thenReturn(events);
    config.setProvider(provider);
    logger = mock(Logger.class);
    config.logger = logger;
  }

  private static EventSearchResponse result(String type, String id) {
    return EventSearchResponse.builder()
        .requestID(UUID.generate())
        .type(type)
        .entity("TestEntity")
        .entityID(UUID.of(id))
        .createdAt("2026-11-10T10:00:00.000Z")
        .value(Map.of("id", id))
        .build();
  }

  @Test
  void bindsEveryValueToTheClassOfItsEvent() {
    config.events.put("TestEvent", new EventMetadata(TestEvent.class));
    config.events.put("TestEventNoRegistered", new EventMetadata(TestEventNoRegistered.class));
    when(events.search(any(), any())).thenReturn(List.of(result("TestEvent", "1"), result("TestEventNoRegistered", "2")));

    List<EventSearchResponse> results = Booster.events(request);

    verify(events, times(1)).search(config, request);
    assertThat(results).hasSize(2);
    assertThat(results.get(0).value()).isInstanceOfSatisfying(TestEvent.class,
        event -> assertThat(event.getId()).isEqualTo(UUID.of("1")));
    assertThat(results.get(1).value()).isEqualTo(new TestEventNoRegistered(UUID.of("2")));
  }

  @Test
  void keepsTheRawValueOfAnUnregisteredType() {
    config.events.put("TestEvent", new EventMetadata(TestEvent.class));
    EventSearchResponse unregistered = result("TestEventNoRegistered", "2");
    when(events.search(any(), any())).thenReturn(List.of(result("TestEvent", "1"), unregistered));

    List<EventSearchResponse> results = Booster.events(request);

    assertThat(results.get(0).value()).isInstanceOf(TestEvent.class);
    assertThat(results.get(1)).isSameAs(unregistered);
    assertThat(results.get(1).value()).isEqualTo(Map.of("id", "2"));
    verify(logger).warn("[Booster]|booster-event-search#createEventValueInstance: ",
        "Could not find @Event or @Notification class for \"TestEventNoRegistered\". Returned the event as it was");
    verify(logger).debug("[Booster]|booster-event-search#createEventValueInstance: ",
        "Found @Event for \"TestEvent\". Created value instance");
  }

  @Test
  void bindsTheValueOfANotification() {
    config.notifications.put("TestNotification", new NotificationMetadata(TestNotification.class));
    when(events.search(any(), any())).thenReturn(List.of(result("TestNotification", "3")));

    List<EventSearchResponse> results = Booster.events(request);

    assertThat(results.getFirst().value()).isInstanceOfSatisfying(TestNotification.class,
        notification -> assertThat(notification.getId()).isEqualTo(UUID.of("3")));
    verify(logger).debug("[Booster]|booster-event-search#createEventValueInstance: ",
        "Found @Notification for \"TestNotification\"");
  }

  @Test
  void keepsTheProviderOrderAndTheOtherFields() {
    config.events.put("TestEvent", new EventMetadata(TestEvent.class));
    EventSearchResponse first = result("TestEvent", "9").toBuilder().deletedAt("2026-11-11T00:00:00.000Z").build();
    EventSearchResponse second = result("TestEvent", "1");
    when(events.search(any(), any())).thenReturn(List.of(first, second));

    List<EventSearchResponse> results = BoosterEventSearch.eventSearch(config, request);

    assertThat(results).extracting(EventSearchResponse::entityID).containsExactly(UUID.of("9"), UUID.of("1"));
    assertThat(results.getFirst()).isEqualTo(first.toBuilder().value(new TestEvent(UUID.of("9"))).build());
  }
}
