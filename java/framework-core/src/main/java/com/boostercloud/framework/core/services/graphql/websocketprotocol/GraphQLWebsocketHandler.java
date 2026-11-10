package com.boostercloud.framework.core.services.graphql.websocketprotocol;

import com.boostercloud.framework.core.BoosterTokenVerifier;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.envelope.GraphQLRequestEnvelopeOrError;
import com.boostercloud.framework.types.provider.ProviderConnectionsLibrary;

/**
 * Port of {@code services/graphql/websocket-protocol/graphql-websocket-protocol.ts}: the server side of the legacy
 * Apollo {@code subscriptions-transport-ws} protocol ({@code graphql-ws}).
 *
 * <p>Skeleton: implemented by stage <b>core-3</b> (spec 08 §3.13).
 */
public class GraphQLWebsocketHandler {

  /**
   * @param config the configuration
   * @param connectionManager the provider's connections library
   * @param callbacks the dispatcher callbacks
   * @param boosterTokenVerifier verifies the {@code connection_init} token
   */
  public GraphQLWebsocketHandler(BoosterConfig config, ProviderConnectionsLibrary connectionManager,
      GraphQLWebsocketHandlerCallbacks callbacks, BoosterTokenVerifier boosterTokenVerifier) {}

  /**
   * {@code handle(envelope)}. Implemented by stage core-3.
   *
   * @param envelope the websocket message envelope, or the error the provider produced
   */
  public void handle(GraphQLRequestEnvelopeOrError envelope) {
    throw new UnsupportedOperationException("not implemented yet: GraphQLWebsocketHandler#handle");
  }
}
