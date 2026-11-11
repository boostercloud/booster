package com.boostercloud.framework.provider.local.library;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.provider.local.services.ConnectionData;
import com.boostercloud.framework.provider.local.services.WebSocketRegistry;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.envelope.ConnectionDataEnvelope;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Websocket connection data and messaging. Port of {@code library/connections-adapter.ts}.
 */
public final class ConnectionsAdapter {

  private ConnectionsAdapter() {}

  /**
   * {@code storeConnectionData(db, config, connectionId, data)}: stores {@code { ...data, connectionID }}.
   *
   * @param db the connections registry
   * @param config the configuration
   * @param connectionId the connection id
   * @param data the connection data
   */
  public static void storeConnectionData(WebSocketRegistry db, BoosterConfig config, String connectionId,
      ConnectionDataEnvelope data) {
    Logger logger = Loggers.getLogger(config, "connections-adapter#storeConnectionData");
    logger.debug("Storing the following connection envelope:", data);
    db.store(ConnectionData.of(data, connectionId));
  }

  /**
   * {@code fetchConnectionData(db, config, connectionId)}: the data stored for a connection.
   *
   * <p>Fixed TypeScript bug: TypeScript queries {@code connectionId} (lower-case {@code d}) while the documents are
   * stored with {@code connectionID}, so it never finds anything and websocket operations run without the
   * authenticated user. This port queries {@code connectionID}.
   *
   * @param db the connections registry
   * @param config the configuration
   * @param connectionId the connection id
   * @return the first stored data, if any
   */
  public static Optional<ConnectionDataEnvelope> fetchConnectionData(WebSocketRegistry db, BoosterConfig config,
      String connectionId) {
    List<Map<String, Object>> results = db.query(Map.of("connectionID", connectionId));
    if (results == null || results.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(BoosterJson.convert(results.get(0), ConnectionDataEnvelope.class));
  }

  /**
   * {@code deleteConnectionData(db, config, connectionId)}.
   *
   * @param db the connections registry
   * @param config the configuration
   * @param connectionId the connection id
   */
  public static void deleteConnectionData(WebSocketRegistry db, BoosterConfig config, String connectionId) {
    Logger logger = Loggers.getLogger(config, "connections-adapter#deleteConnectionData");
    int removed = db.delete(Map.of("connectionID", connectionId));
    if (removed == 0) {
      logger.info("No connections found with connectionID=" + connectionId);
      return;
    }
    logger.debug("Deleted connection = ", connectionId);
  }

  /**
   * {@code sendMessageToConnection(webSocketServerAdapter, config, connectionId, data)}: sends {@code data} as one
   * JSON text frame.
   *
   * @param webSocketServerAdapter the websocket server
   * @param config the configuration
   * @param connectionId the connection id
   * @param data the message
   */
  public static void sendMessageToConnection(WebSocketServerAdapter webSocketServerAdapter, BoosterConfig config,
      String connectionId, Object data) {
    Logger logger = Loggers.getLogger(config, "connection-adapter#sendMessageToConnection");
    logger.debug("Sending message " + BoosterJson.toJson(data) + " to connection " + connectionId);
    webSocketServerAdapter.sendToConnection(connectionId, data);
  }
}
