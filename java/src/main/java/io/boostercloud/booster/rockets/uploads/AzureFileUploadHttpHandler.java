package io.boostercloud.booster.rockets.uploads;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.boostercloud.booster.core.AccessPolicy;
import io.boostercloud.booster.core.Booster;
import io.boostercloud.booster.core.Json;
import io.boostercloud.booster.core.RequestContext;
import io.boostercloud.booster.transport.AuthenticationProvider;
import io.boostercloud.booster.transport.auth.AuthenticationException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Authenticated Event Grid webhook. Subscription validation never substitutes for sender authentication. */
public final class AzureFileUploadHttpHandler implements HttpHandler {
    private final Booster booster;
    private final FileUploadRocket rocket;
    private final AuthenticationProvider authentication;
    private final AccessPolicy policy;

    public AzureFileUploadHttpHandler(Booster booster, FileUploadRocket rocket,
                                     AuthenticationProvider authentication, AccessPolicy policy) {
        this.booster = Objects.requireNonNull(booster); this.rocket = Objects.requireNonNull(rocket);
        this.authentication = Objects.requireNonNull(authentication); this.policy = Objects.requireNonNull(policy);
    }

    @Override public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!exchange.getRequestMethod().equals("POST")) {
                exchange.getResponseHeaders().set("Allow", "POST"); respond(exchange, 405, Map.of("error", "Method not allowed")); return;
            }
            var headers = exchange.getRequestHeaders().get("Authorization");
            if (headers == null || headers.size() != 1) throw new AuthenticationException();
            var user = authentication.authenticate(headers.getFirst());
            if (!user.authenticated()) throw new AuthenticationException();
            byte[] body = exchange.getRequestBody().readNBytes(1_048_577);
            if (body.length > 1_048_576) { respond(exchange, 413, Map.of("error", "Payload too large")); return; }
            JsonNode payload = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build().readTree(body);
            if (payload == null || !(payload.isObject() || payload.isArray()) || payload.isArray() && payload.size() > 100)
                throw new IllegalArgumentException("Invalid event payload");
            if (!policy.permits(new RequestContext(null, user, Map.of("transport", "azure-file-upload")), payload))
                throw new AuthenticationException();
            Iterable<JsonNode> events = payload.isArray() ? payload : List.of(payload);
            for (JsonNode event : events) {
                if (!event.isObject()) throw new IllegalArgumentException("Invalid event");
                if (event.path("eventType").asText().equals("Microsoft.EventGrid.SubscriptionValidationEvent")) {
                    if (payload.isArray() && payload.size() != 1) throw new IllegalArgumentException("Invalid validation batch");
                    JsonNode code = event.path("data").path("validationCode");
                    if (!code.isTextual() || code.asText().length() > 512 || code.asText().isBlank())
                        throw new IllegalArgumentException("Validation code required");
                    respond(exchange, 200, Map.of("validationResponse", code.asText())); return;
                }
                rocket.handleCloudEvent(booster, event);
            }
            respond(exchange, 200, Map.of("status", "success"));
        } catch (AuthenticationException error) { respond(exchange, 401, Map.of("error", "Invalid event sender")); }
        catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException error) {
            respond(exchange, 400, Map.of("error", "Invalid event payload"));
        } catch (RuntimeException error) { respond(exchange, 503, Map.of("error", "Event processing failed; retry delivery")); }
        finally { exchange.close(); }
    }

    private static void respond(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] bytes = Json.MAPPER.writeValueAsBytes(payload);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes);
    }
}
