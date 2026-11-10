package com.boostercloud.framework.provider.azure.cosmos.rest;

import com.boostercloud.framework.provider.azure.http.ConnectionStrings;
import java.net.URI;
import java.util.Map;

/**
 * A parsed {@code AccountEndpoint=https://<account>.documents.azure.com:443/;AccountKey=<base64>;} connection string
 * (the {@code COSMOSDB_CONNECTION_STRING} app setting).
 *
 * @param endpoint the account endpoint
 * @param accountKey the base64 master key
 */
public record CosmosConnectionString(URI endpoint, String accountKey) {

  /**
   * @param connectionString the connection string
   * @return the parsed connection string
   * @throws IllegalArgumentException {@code Could not parse the provided connection string} (the JavaScript SDK
   *     message) when the endpoint or the key is missing
   */
  public static CosmosConnectionString parse(String connectionString) {
    Map<String, String> parts = ConnectionStrings.parse(connectionString);
    String endpoint = parts.get("accountendpoint");
    String key = parts.get("accountkey");
    if (endpoint == null || endpoint.isEmpty() || key == null || key.isEmpty()) {
      throw new IllegalArgumentException("Could not parse the provided connection string");
    }
    return new CosmosConnectionString(URI.create(endpoint), key);
  }

  @Override
  public String toString() {
    return "CosmosConnectionString[endpoint=" + endpoint + ", accountKey=***]";
  }
}
