package com.boostercloud.framework.core.integration.inmemory;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.envelope.ConnectionDataEnvelope;
import com.boostercloud.framework.types.provider.ProviderConnectionsLibrary;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The websocket connections of the in-memory provider: the connection data the core stores, and the messages sent to
 * every connection (as the JSON a client would receive). A {@link #close(String) closed} connection rejects messages,
 * as a gone client does.
 */
public final class InMemoryConnections implements ProviderConnectionsLibrary {

  private final Map<String, ConnectionDataEnvelope> data = new LinkedHashMap<>();
  private final Map<String, List<Map<String, Object>>> messages = new LinkedHashMap<>();
  private final Set<String> closed = new HashSet<>();

  /**
   * @param connectionID a connection
   * @return the messages sent to it, oldest first, as raw JSON
   */
  public synchronized List<Map<String, Object>> messages(String connectionID) {
    return List.copyOf(messages.getOrDefault(connectionID, List.of()));
  }

  /**
   * @param connectionID a connection
   * @return its stored data
   */
  public synchronized Optional<ConnectionDataEnvelope> data(String connectionID) {
    return Optional.ofNullable(data.get(connectionID));
  }

  /**
   * The client went away: later messages to it fail.
   *
   * @param connectionID a connection
   */
  public synchronized void close(String connectionID) {
    closed.add(connectionID);
  }

  @Override
  public synchronized void storeData(BoosterConfig config, String connectionID, ConnectionDataEnvelope connectionData) {
    data.put(connectionID, BoosterJson.convert(BoosterJson.toRaw(connectionData), ConnectionDataEnvelope.class));
  }

  @Override
  public synchronized Optional<ConnectionDataEnvelope> fetchData(BoosterConfig config, String connectionID) {
    return Optional.ofNullable(data.get(connectionID));
  }

  @Override
  public synchronized void deleteData(BoosterConfig config, String connectionID) {
    data.remove(connectionID);
  }

  @Override
  public synchronized void sendMessage(BoosterConfig config, String connectionID, Object message) {
    if (closed.contains(connectionID)) {
      throw new IllegalStateException("The connection " + connectionID + " is closed");
    }
    messages.computeIfAbsent(connectionID, id -> new ArrayList<>()).add(BoosterJson.toRawMap(message));
  }
}
