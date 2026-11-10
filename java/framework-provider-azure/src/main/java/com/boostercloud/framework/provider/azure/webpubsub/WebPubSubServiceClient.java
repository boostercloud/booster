package com.boostercloud.framework.provider.azure.webpubsub;

/**
 * The Web PubSub service operations Booster uses ({@code WebPubSubServiceClient} of {@code @azure/web-pubsub}). The
 * production implementation is
 * {@link com.boostercloud.framework.provider.azure.webpubsub.rest.WebPubSubRestServiceClient}.
 */
public interface WebPubSubServiceClient {

  /** Content type of text messages ({@code { contentType: 'text/plain' }}). */
  String TEXT_PLAIN = "text/plain";

  /**
   * {@code serviceClient.sendToConnection(connectionId, message, { contentType })}.
   *
   * @param connectionId the websocket connection id
   * @param message the message
   * @param contentType {@link #TEXT_PLAIN} (or {@code application/json}, {@code application/octet-stream})
   * @throws WebPubSubException when the service rejects the request
   */
  void sendToConnection(String connectionId, String message, String contentType);
}
