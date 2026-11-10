package com.boostercloud.framework.core;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.envelope.EventSearchParameters;
import com.boostercloud.framework.types.envelope.EventSearchResponse;
import java.util.List;

/**
 * Port of {@code booster-event-search.ts} ({@code eventSearch(config, request)}): calls
 * {@code provider.events().search} and turns each result's {@code value} into an instance of the registered event or
 * notification class (left raw, with a warning, when unknown).
 *
 * <p>Skeleton: implemented by stage <b>core-2a</b> (spec 06 §3.10).
 */
public final class BoosterEventSearch {

  private BoosterEventSearch() {}

  /**
   * {@code eventSearch(config, request)}. Implemented by stage core-2a.
   *
   * @param config the configuration
   * @param request the search parameters
   * @return the events, in provider order
   */
  public static List<EventSearchResponse> eventSearch(BoosterConfig config, EventSearchParameters request) {
    throw new UnsupportedOperationException("not implemented yet: BoosterEventSearch#eventSearch");
  }
}
