package com.boostercloud.framework.types.envelope;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Data stored per websocket connection. Port of {@code ConnectionDataEnvelope}.
 *
 * @param expirationTime expiration, in epoch <b>seconds</b>
 * @param user the authenticated user, absent for anonymous connections
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonPropertyOrder({"expirationTime", "user"})
public record ConnectionDataEnvelope(
    long expirationTime,
    UserEnvelope user) {
}
