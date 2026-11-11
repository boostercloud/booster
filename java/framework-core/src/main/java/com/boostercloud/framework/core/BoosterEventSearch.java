package com.boostercloud.framework.core;

import com.boostercloud.framework.common.Instances;
import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.EventMetadata;
import com.boostercloud.framework.types.concepts.NotificationMetadata;
import com.boostercloud.framework.types.envelope.EventSearchParameters;
import com.boostercloud.framework.types.envelope.EventSearchResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * Port of {@code booster-event-search.ts} ({@code eventSearch(config, request)}, used by {@code Booster.events}):
 * calls {@code provider.events().search(config, request)} and binds each result's {@code value} to the class
 * registered for its {@code type} in {@code config.events}, or else in {@code config.notifications}; an unknown type
 * keeps its raw value (with a warning). No authorization, filtering, sorting or schema migration happens here:
 * order, {@code from}/{@code to}/{@code limit} and the kinds returned are up to the provider.
 */
public final class BoosterEventSearch {

  private BoosterEventSearch() {}

  /**
   * {@code eventSearch(config, request)}.
   *
   * @param config the configuration
   * @param request the search parameters
   * @return the events, in provider order, with instances as values (new records: TypeScript mutates the provider's
   *     result objects)
   */
  public static List<EventSearchResponse> eventSearch(BoosterConfig config, EventSearchParameters request) {
    List<EventSearchResponse> events = config.provider().events().search(config, request);
    List<EventSearchResponse> results = new ArrayList<>(events.size());
    for (EventSearchResponse event : events) {
      results.add(createEventValueInstance(config, event));
    }
    return results;
  }

  private static EventSearchResponse createEventValueInstance(BoosterConfig config, EventSearchResponse event) {
    Logger logger = Loggers.getLogger(config, "booster-event-search#createEventValueInstance");
    EventMetadata eventMetadata = config.events.get(event.type());
    if (eventMetadata != null) {
      EventSearchResponse result =
          event.toBuilder().value(Instances.createInstance(eventMetadata.type(), event.value())).build();
      logger.debug("Found @Event for \"" + event.type() + "\". Created value instance");
      return result;
    }
    NotificationMetadata notificationMetadata = config.notifications.get(event.type());
    if (notificationMetadata != null) {
      EventSearchResponse result =
          event.toBuilder().value(Instances.createInstance(notificationMetadata.type(), event.value())).build();
      logger.debug("Found @Notification for \"" + event.type() + "\"");
      return result;
    }
    logger.warn("Could not find @Event or @Notification class for \"" + event.type() + "\". Returned the event as it was");
    return event;
  }
}
