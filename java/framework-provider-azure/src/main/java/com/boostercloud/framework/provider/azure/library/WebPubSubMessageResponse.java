package com.boostercloud.framework.provider.azure.library;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Port of {@code WebPubSubMessageResponse}: the return value of the Web PubSub {@code messages} function, sent back to
 * the client on its connection.
 *
 * @param data the payload, optional
 * @param dataType {@code json}, {@code text} or {@code binary}
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record WebPubSubMessageResponse(Object data, String dataType) {}
