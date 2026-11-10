package com.boostercloud.framework.types.envelope;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * A changed read model, as reported by the provider to notify subscribers. Port of {@code ReadModelEnvelope}.
 *
 * @param typeName the read model class name
 * @param value the read model (raw value or instance)
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@JsonPropertyOrder({"typeName", "value"})
public record ReadModelEnvelope(
    String typeName,
    Object value) {
}
