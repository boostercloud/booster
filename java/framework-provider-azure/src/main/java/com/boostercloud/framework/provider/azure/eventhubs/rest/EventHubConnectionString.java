package com.boostercloud.framework.provider.azure.eventhubs.rest;

import com.boostercloud.framework.provider.azure.http.ConnectionStrings;
import java.net.URI;
import java.util.Map;

/**
 * A parsed Event Hubs connection string
 * ({@code Endpoint=sb://<ns>.servicebus.windows.net/;SharedAccessKeyName=<name>;SharedAccessKey=<key>;EntityPath=<hub>},
 * the {@code EVENTHUB_CONNECTION_STRING} app setting). A pre-computed {@code SharedAccessSignature=...} is accepted
 * instead of the key.
 *
 * @param endpoint the namespace endpoint ({@code sb://...})
 * @param sharedAccessKeyName the policy name, optional when a signature is given
 * @param sharedAccessKey the policy key, optional when a signature is given
 * @param entityPath the event hub, optional
 * @param sharedAccessSignature a ready-made SAS token, optional
 */
public record EventHubConnectionString(
    URI endpoint, String sharedAccessKeyName, String sharedAccessKey, String entityPath, String sharedAccessSignature) {

  /**
   * @param connectionString the connection string
   * @return the parsed connection string
   * @throws IllegalArgumentException when the endpoint or the credentials are missing
   */
  public static EventHubConnectionString parse(String connectionString) {
    Map<String, String> parts = ConnectionStrings.parse(connectionString);
    String endpoint = parts.get("endpoint");
    String keyName = parts.get("sharedaccesskeyname");
    String key = parts.get("sharedaccesskey");
    String signature = parts.get("sharedaccesssignature");
    if (endpoint == null || endpoint.isEmpty()) {
      throw new IllegalArgumentException("Connection string should have an Endpoint key.");
    }
    if (signature == null && (keyName == null || key == null)) {
      throw new IllegalArgumentException(
          "Connection string should have SharedAccessKeyName and SharedAccessKey, or SharedAccessSignature.");
    }
    return new EventHubConnectionString(URI.create(endpoint), keyName, key, parts.get("entitypath"), signature);
  }

  /**
   * @return the HTTPS base URL of the namespace ({@code sb://} becomes {@code https://}; {@code http(s)://} is kept,
   *     which the contract tests use)
   */
  public URI httpBase() {
    String scheme = endpoint.getScheme();
    String httpScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme) ? scheme : "https";
    String authority = endpoint.getRawAuthority();
    return URI.create(httpScheme + "://" + authority + "/");
  }

  @Override
  public String toString() {
    return "EventHubConnectionString[endpoint=" + endpoint + ", entityPath=" + entityPath + ", credentials=***]";
  }
}
