package com.boostercloud.framework.types.provider;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.envelope.ConnectionDataEnvelope;
import java.util.Optional;

/**
 * Websocket connection storage and messaging of a provider. Port of {@code ProviderConnectionsLibrary}.
 */
public interface ProviderConnectionsLibrary {
  /**
   * @param config the configuration
   * @param connectionID the connection id
   * @param data the data to store
   */
  void storeData(BoosterConfig config, String connectionID, ConnectionDataEnvelope data);

  /**
   * @param config the configuration
   * @param connectionID the connection id
   * @return the stored data, if any
   */
  Optional<ConnectionDataEnvelope> fetchData(BoosterConfig config, String connectionID);

  /**
   * @param config the configuration
   * @param connectionID the connection id
   */
  void deleteData(BoosterConfig config, String connectionID);

  /**
   * Sends a message (a {@code GraphQLServerMessage}, serialized with {@code BoosterJson}) to a connection.
   *
   * @param config the configuration
   * @param connectionID the connection id
   * @param data the message
   */
  void sendMessage(BoosterConfig config, String connectionID, Object data);
}
