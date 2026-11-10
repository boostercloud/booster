package com.boostercloud.framework.core;

import com.boostercloud.framework.types.BoosterConfig;

/**
 * Port of {@code booster-subscribers-notifier.ts}: the entry point invoked by the provider when read models change;
 * re-executes every matching subscription against the changed read models and pushes the results to the
 * subscribers' connections.
 *
 * <p>Skeleton: implemented by stage <b>core-2b</b> (spec 07 §3.5); trace action {@code DISPATCH_SUBSCRIBER_NOTIFIER}.
 */
public class BoosterSubscribersNotifier {

  private final BoosterConfig config;

  /**
   * @param config the configuration
   */
  public BoosterSubscribersNotifier(BoosterConfig config) {
    this.config = config;
  }

  /**
   * @return the configuration
   */
  public BoosterConfig config() {
    return config;
  }

  /**
   * {@code dispatch(request)}. Implemented by stage core-2b.
   *
   * @param request the provider-specific request
   */
  public void dispatch(Object request) {
    throw new UnsupportedOperationException("not implemented yet: BoosterSubscribersNotifier#dispatch");
  }
}
