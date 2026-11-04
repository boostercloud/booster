package io.boostercloud.booster.transport;

import io.boostercloud.booster.core.BoosterException;
import io.boostercloud.booster.core.Json;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Explicit application error safe to expose through Booster's GraphQL code/data contract. */
public final class GraphQLClientException extends BoosterException {
    private final String clientCode;
    private final JsonNode clientData;
    public GraphQLClientException(String code, String message, Map<String, Object> data) {
        super(Code.VALIDATION, message);
        if (code == null || code.isBlank() || code.length() > 128 || message == null || message.isBlank())
            throw new IllegalArgumentException("Public code and message required");
        clientCode = code; clientData = Json.MAPPER.valueToTree(data == null ? Map.of() : data);
    }
    public String clientCode() { return clientCode; }
    public JsonNode clientData() { return clientData.deepCopy(); }
}
