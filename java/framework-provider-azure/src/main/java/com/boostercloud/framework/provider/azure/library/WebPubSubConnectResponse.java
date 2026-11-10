package com.boostercloud.framework.provider.azure.library;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Port of {@code WebPubSubConnectResponse}: the return value of the Web PubSub {@code connect} function.
 *
 * @param subprotocol the accepted subprotocol ({@code graphql-ws})
 * @param userId optional
 * @param groups optional
 * @param roles optional
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record WebPubSubConnectResponse(String subprotocol, String userId, List<String> groups, List<String> roles) {}
