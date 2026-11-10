package com.boostercloud.framework.core;

import com.boostercloud.framework.types.BoosterConfig;

/**
 * Port of {@code booster-graphql-dispatcher.ts}: the entry point of every GraphQL request (HTTP and websocket):
 * envelope, {@code CONNECT}/{@code MESSAGE}/{@code DISCONNECT} routing, token verification, parse/validate/execute,
 * error formatting and the provider response.
 *
 * <p>Skeleton: implemented by stage <b>core-3</b> (spec 08 §3.12); trace actions {@code GRAPHQL_DISPATCH} and
 * {@code GRAPHQL_RUN_OPERATION}.
 */
public class BoosterGraphQLDispatcher {

  private final BoosterConfig config;

  /**
   * @param config the configuration
   */
  public BoosterGraphQLDispatcher(BoosterConfig config) {
    this.config = config;
  }

  /**
   * @return the configuration
   */
  public BoosterConfig config() {
    return config;
  }

  /**
   * {@code dispatch(request)}. Implemented by stage core-3.
   *
   * @param request the provider-specific request
   * @return the provider-specific response
   */
  public Object dispatch(Object request) {
    throw new UnsupportedOperationException("not implemented yet: BoosterGraphQLDispatcher#dispatch");
  }
}
