package com.boostercloud.framework.provider.azure.types;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The {@code request} of a Web PubSub trigger ({@code AzureWebPubSubFunctionInput.request} in TypeScript).
 *
 * @param connectionContext the connection context, when the trigger delivered it in the request
 * @param data the message data (a string or a raw value), optional
 * @param dataType {@code text}, {@code json} or {@code binary}, optional
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record AzureWebPubSubRequest(WebPubSubConnectionContext connectionContext, Object data, String dataType) {}
