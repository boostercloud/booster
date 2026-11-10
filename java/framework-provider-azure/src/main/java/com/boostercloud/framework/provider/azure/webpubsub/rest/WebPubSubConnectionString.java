package com.boostercloud.framework.provider.azure.webpubsub.rest;

import com.boostercloud.framework.provider.azure.http.ConnectionStrings;
import java.net.URI;
import java.util.Map;

/**
 * A parsed {@code Endpoint=https://<name>.webpubsub.azure.com;AccessKey=<key>;Version=1.0;} connection string (the
 * {@code WebPubSubConnectionString} app setting). An optional {@code Port=} replaces the endpoint port, as in
 * {@code @azure/web-pubsub}.
 *
 * @param endpoint the service endpoint (port applied, no trailing slash)
 * @param accessKey the access key
 */
public record WebPubSubConnectionString(URI endpoint, String accessKey) {

  /**
   * @param connectionString the connection string
   * @return the parsed connection string
   * @throws IllegalArgumentException when the endpoint or the access key is missing
   */
  public static WebPubSubConnectionString parse(String connectionString) {
    Map<String, String> parts = ConnectionStrings.parse(connectionString);
    String endpoint = parts.get("endpoint");
    String key = parts.get("accesskey");
    if (endpoint == null || endpoint.isEmpty() || key == null || key.isEmpty()) {
      throw new IllegalArgumentException("Invalid connection string: Endpoint and AccessKey are required");
    }
    URI uri = URI.create(endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint);
    String port = parts.get("port");
    if (port != null && !port.isEmpty()) {
      uri = URI.create(uri.getScheme() + "://" + uri.getHost() + ":" + port + (uri.getRawPath() == null ? "" : uri.getRawPath()));
    }
    return new WebPubSubConnectionString(uri, key);
  }

  @Override
  public String toString() {
    return "WebPubSubConnectionString[endpoint=" + endpoint + ", accessKey=***]";
  }
}
