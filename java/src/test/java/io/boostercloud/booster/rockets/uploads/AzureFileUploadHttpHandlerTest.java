package io.boostercloud.booster.rockets.uploads;

import com.sun.net.httpserver.HttpServer;
import io.boostercloud.booster.core.*;
import io.boostercloud.booster.transport.auth.AuthenticationException;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.net.http.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AzureFileUploadHttpHandlerTest {
    @Test void authenticatedValidationAndBlobDeliveryRejectSpoofingAndDuplicateEvents() throws Exception {
        var config = new StorageConfiguration("media", "assets", List.of("**"));
        var rocket = new FileUploadRocket(List.of(config), Map.of("media", FileUploadContractTest.azure("media")), AccessPolicy.deny(), AccessPolicy.publicAccess());
        try (var booster = rocket.install(Booster.builder(new InMemoryEventStore())).build()) {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/azure/files", new AzureFileUploadHttpHandler(booster, rocket, header -> {
                if (!"Bearer eventgrid".equals(header)) throw new AuthenticationException();
                return new UserContext("event-grid", Set.of("file-notifications"), Map.of());
            }, AccessPolicy.roles("file-notifications")));
            server.start();
            try {
                URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/azure/files");
                String validation = "[{\"eventType\":\"Microsoft.EventGrid.SubscriptionValidationEvent\",\"data\":{\"validationCode\":\"challenge\"}}]";
                assertEquals(401, post(endpoint, validation, null).statusCode());
                var validated = post(endpoint, validation, "Bearer eventgrid");
                assertEquals(200, validated.statusCode());
                assertEquals("challenge", Json.MAPPER.readTree(validated.body()).path("validationResponse").asText());
                String upload = "[{\"id\":\"one\",\"eventType\":\"Microsoft.Storage.BlobCreated\",\"data\":{\"url\":\"https://media.blob.core.windows.net/assets/incoming/a.png\",\"eTag\":\"v1\"}}]";
                assertEquals(401, post(endpoint, upload, "Bearer spoof").statusCode()); assertEquals(0, booster.position());
                assertEquals(200, post(endpoint, upload, "Bearer eventgrid").statusCode());
                assertEquals(200, post(endpoint, upload, "Bearer eventgrid").statusCode()); assertEquals(1, booster.position());
                assertEquals(400, post(endpoint, "{\"data\":1,\"data\":2}", "Bearer eventgrid").statusCode());
            } finally { server.stop(0); }
        }
    }

    private static HttpResponse<String> post(URI uri, String body, String authorization) throws Exception {
        var builder = HttpRequest.newBuilder(uri).POST(HttpRequest.BodyPublishers.ofString(body)).header("Content-Type", "application/json");
        if (authorization != null) builder.header("Authorization", authorization);
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
