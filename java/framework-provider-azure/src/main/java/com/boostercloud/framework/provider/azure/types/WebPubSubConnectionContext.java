package com.boostercloud.framework.provider.azure.types;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Port of {@code WebPubSubConnectionContext}.
 *
 * @param connectionId the websocket connection id
 * @param userId the user id, optional
 * @param hub the hub ({@code booster})
 * @param eventType {@code system} or {@code user}
 * @param eventName {@code connect}, {@code connected}, {@code disconnected}, {@code message}, ...
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record WebPubSubConnectionContext(
    String connectionId, String userId, String hub, String eventType, String eventName) {}
