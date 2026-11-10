package com.boostercloud.framework.core;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.envelope.EventSearchRequest;
import com.boostercloud.framework.types.envelope.EventSearchResponse;
import java.util.List;

/**
 * Port of {@code booster-events-reader.ts}: validates and authorizes an event search request (GraphQL
 * {@code eventsByEntity}/{@code eventsByType}) and runs it.
 *
 * <p>Skeleton: implemented by stage <b>core-2a</b> (spec 06 §3.9).
 */
public class BoosterEventsReader {

  private final BoosterConfig config;

  /**
   * @param config the configuration
   */
  public BoosterEventsReader(BoosterConfig config) {
    this.config = config;
  }

  /**
   * @return the configuration
   */
  public BoosterConfig config() {
    return config;
  }

  /**
   * {@code fetch(eventRequest)}. Implemented by stage core-2a.
   *
   * @param eventRequest the request
   * @return the events
   */
  public List<EventSearchResponse> fetch(EventSearchRequest eventRequest) {
    throw new UnsupportedOperationException("not implemented yet: BoosterEventsReader#fetch");
  }
}
