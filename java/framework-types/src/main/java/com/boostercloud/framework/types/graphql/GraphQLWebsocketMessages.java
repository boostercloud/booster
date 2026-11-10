package com.boostercloud.framework.types.graphql;

import java.util.Map;

/**
 * Constants of {@code graphql-websocket-messages.ts}.
 */
public final class GraphQLWebsocketMessages {

  /** Name of the websocket sub-protocol ({@code graphql-ws}, the legacy Apollo protocol). */
  public static final String SUBPROTOCOL = "graphql-ws";

  /**
   * {@code graphQLWebsocketSubprotocolHeaders}: the header Booster answers websocket {@code CONNECT} events with.
   */
  public static final Map<String, String> GRAPHQL_WEBSOCKET_SUBPROTOCOL_HEADERS =
      Map.of("Sec-WebSocket-Protocol", SUBPROTOCOL);

  private GraphQLWebsocketMessages() {}
}
