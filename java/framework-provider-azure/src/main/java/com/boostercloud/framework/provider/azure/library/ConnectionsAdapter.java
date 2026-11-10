package com.boostercloud.framework.provider.azure.library;

import static com.boostercloud.framework.provider.azure.helpers.JsValues.get;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.provider.azure.Constants;
import com.boostercloud.framework.provider.azure.Constants.ConnectionsStoreAttributes;
import com.boostercloud.framework.provider.azure.Constants.OtherEnvironmentVarNames;
import com.boostercloud.framework.provider.azure.cosmos.CosmosClient;
import com.boostercloud.framework.provider.azure.cosmos.CosmosContainer;
import com.boostercloud.framework.provider.azure.cosmos.FeedOptions;
import com.boostercloud.framework.provider.azure.cosmos.SqlParameter;
import com.boostercloud.framework.provider.azure.cosmos.SqlQuerySpec;
import com.boostercloud.framework.provider.azure.helpers.JsValues;
import com.boostercloud.framework.provider.azure.webpubsub.WebPubSubServiceClient;
import com.boostercloud.framework.provider.azure.webpubsub.WebPubSubServiceClientFactory;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.envelope.ConnectionDataEnvelope;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Port of {@code library/connections-adapter.ts}: websocket connection data in the connections container (partition
 * key {@code /connectionID}) and messages to connections through Web PubSub.
 */
public final class ConnectionsAdapter {

  static final String CONNECTION_QUERY = "SELECT *\n              FROM c\n              WHERE c[\""
      + ConnectionsStoreAttributes.PARTITION_KEY + "\"] = @partitionKey";

  private ConnectionsAdapter() {}

  /**
   * {@code storeConnectionData}: {@code { ...data, connectionID, ttl: data.expirationTime }}.
   *
   * @param cosmosDb the client
   * @param config the configuration
   * @param connectionID the connection
   * @param data the data
   */
  public static void storeConnectionData(
      CosmosClient cosmosDb, BoosterConfig config, String connectionID, ConnectionDataEnvelope data) {
    long ttl = data.expirationTime();
    Map<String, Object> document = new LinkedHashMap<>(BoosterJson.toRawMap(data));
    document.put(ConnectionsStoreAttributes.PARTITION_KEY, connectionID);
    document.put("ttl", ttl);
    connectionsContainer(cosmosDb, config).items().create(document);
  }

  /**
   * {@code fetchConnectionData}.
   *
   * @param cosmosDb the client
   * @param config the configuration
   * @param connectionID the connection
   * @return the first stored data of the connection, if any
   */
  public static Optional<ConnectionDataEnvelope> fetchConnectionData(
      CosmosClient cosmosDb, BoosterConfig config, String connectionID) {
    List<Object> resources = query(cosmosDb, config, connectionID);
    return resources.isEmpty()
        ? Optional.empty()
        : Optional.of(BoosterJson.convert(resources.getFirst(), ConnectionDataEnvelope.class));
  }

  /**
   * {@code deleteConnectionData}: deletes the first stored data of the connection.
   *
   * @param cosmosDb the client
   * @param config the configuration
   * @param connectionID the connection
   */
  public static void deleteConnectionData(CosmosClient cosmosDb, BoosterConfig config, String connectionID) {
    Logger logger = Loggers.getLogger(config, "connections-adapter#deleteConnectionData");
    List<Object> foundConnections = query(cosmosDb, config, connectionID);
    if (foundConnections.isEmpty()) {
      logger.info("No connections found with connectionID=" + connectionID);
      return;
    }

    // There can't be more than one, as we used the full primary key in the query
    Object connectionsToDelete = foundConnections.getFirst();
    logger.debug("Deleting connection = ", connectionsToDelete);
    connectionsContainer(cosmosDb, config)
        .item(String.valueOf(get(connectionsToDelete, "id")),
            get(connectionsToDelete, ConnectionsStoreAttributes.PARTITION_KEY))
        .delete();
  }

  /**
   * {@code sendMessageToConnection}: a new service client per call (hub {@code booster}, connection string from the
   * {@code WebPubSubConnectionString} app setting) sends {@code JSON.stringify(data)} as {@code text/plain}.
   *
   * @param clients creates the Web PubSub client
   * @param config the configuration
   * @param connectionID the connection
   * @param data the message (a {@code GraphQLServerMessage})
   */
  public static void sendMessageToConnection(
      WebPubSubServiceClientFactory clients, BoosterConfig config, String connectionID, Object data) {
    Logger logger = Loggers.getLogger(config, "connection-adapter#sendMessageToConnection");
    logger.debug("Sending message " + JsValues.stringify(data) + " to connection " + connectionID);
    WebPubSubServiceClient serviceClient = clients.create(
        EnvironmentVariables.get(OtherEnvironmentVarNames.WEB_PUB_SUB_CONNECTION_STRING).orElse(null),
        Constants.WEB_PUB_SUB_HUB);
    serviceClient.sendToConnection(connectionID, BoosterJson.toJson(data), WebPubSubServiceClient.TEXT_PLAIN);
  }

  private static List<Object> query(CosmosClient cosmosDb, BoosterConfig config, String connectionID) {
    return connectionsContainer(cosmosDb, config).items()
        .query(new SqlQuerySpec(CONNECTION_QUERY, List.of(SqlParameter.of("@partitionKey", connectionID))),
            FeedOptions.forPartition(connectionID))
        .fetchAll()
        .resources();
  }

  private static CosmosContainer connectionsContainer(CosmosClient cosmosDb, BoosterConfig config) {
    return cosmosDb.database(config.resourceNames().applicationStack())
        .container(config.resourceNames().connectionsStore());
  }
}
