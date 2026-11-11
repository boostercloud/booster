package com.boostercloud.framework.provider.azure.webpubsub;

/** Creates service clients ({@code new WebPubSubServiceClient(connectionString, hub)}). */
@FunctionalInterface
public interface WebPubSubServiceClientFactory {
  /**
   * @param connectionString {@code Endpoint=https://...;AccessKey=...;Version=1.0;}
   * @param hub the hub name
   * @return a client for that hub
   */
  WebPubSubServiceClient create(String connectionString, String hub);
}
