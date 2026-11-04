package io.boostercloud.booster.rockets.uploads;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.http.*;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises actual Azure SDK request serialization/SAS code using deterministic HTTP responses, not live Azure. */
class AzureBlobStorageTest {
    private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");
    private static final StorageConfiguration CONFIG = new StorageConfiguration("examplestorage", "assets", List.of("incoming", "incoming/**"));
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private static HttpResponse response(HttpRequest request, String xml) {
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        var headers = new HttpHeaders().set("Content-Type", "application/xml").set("Content-Length", Integer.toString(bytes.length))
                .set("x-ms-request-id", "test-request").set("x-ms-version", "2025-11-05");
        return new HttpResponse(request) {
            public int getStatusCode() { return 200; }
            public String getHeaderValue(String name) { return headers.getValue(name); }
            public HttpHeaders getHeaders() { return headers; }
            public Flux<ByteBuffer> getBody() { return Flux.just(ByteBuffer.wrap(bytes)); }
            public Mono<byte[]> getBodyAsByteArray() { return Mono.just(bytes); }
            public Mono<String> getBodyAsString() { return Mono.just(xml); }
            public Mono<String> getBodyAsString(Charset charset) { return Mono.just(new String(bytes, charset)); }
        };
    }
    private static String delegation() {
        return "<UserDelegationKey><SignedOid>11111111-1111-1111-1111-111111111111</SignedOid>"
                + "<SignedTid>22222222-2222-2222-2222-222222222222</SignedTid><SignedStart>2026-11-04T12:00:00Z</SignedStart>"
                + "<SignedExpiry>2026-11-05T12:00:00Z</SignedExpiry><SignedService>b</SignedService><SignedVersion>2025-11-05</SignedVersion>"
                + "<Value>" + KEY + "</Value></UserDelegationKey>";
    }
    private static AzureBlobStorage storage(HttpClient http) {
        TokenCredential credential = context -> Mono.just(new AccessToken("test-token", OffsetDateTime.now().plusHours(1)));
        var client = new BlobServiceClientBuilder().endpoint("https://examplestorage.blob.core.windows.net")
                .credential(credential).httpClient(http).buildClient();
        return new AzureBlobStorage(client, Clock.fixed(NOW, ZoneOffset.UTC));
    }
    private static Map<String, String> query(URI uri) {
        var result = new HashMap<String, String>();
        for (String entry : uri.getRawQuery().split("&")) {
            var parts = entry.split("=", 2); result.put(parts[0], URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        return result;
    }

    @Test void userDelegationSasUsesIdentityCorrectPermissionsExpiryAndEncodedKey() {
        var calls = new CopyOnWriteArrayList<HttpRequest>();
        var storage = storage(request -> { calls.add(request); return Mono.just(response(request, delegation())); });
        URI put = storage.presignedPut(CONFIG, "incoming", "a photo π.png", Duration.ofHours(2));
        URI get = storage.presignedGet(CONFIG, "incoming", "a photo π.png", Duration.ofHours(2));
        assertEquals(2, calls.size());
        assertEquals("Bearer test-token", calls.getFirst().getHeaders().getValue("Authorization"));
        assertEquals(HttpMethod.POST, calls.getFirst().getHttpMethod());
        assertTrue(calls.getFirst().getUrl().getQuery().contains("comp=userdelegationkey"));
        String body = calls.getFirst().getBodyAsBinaryData().toString();
        assertTrue(body.contains("2026-11-04T12:00:00Z")); assertTrue(body.contains("2026-11-04T14:00:00Z"));
        assertEquals("/assets/incoming/a photo π.png", put.getPath());
        assertEquals("w", query(put).get("sp")); assertEquals("r", query(get).get("sp"));
        assertEquals("https", query(put).get("spr")); assertEquals("2026-11-04T14:00:00Z", query(put).get("se"));
        assertEquals("11111111-1111-1111-1111-111111111111", query(put).get("skoid"));
        assertNotEquals(query(put).get("sig"), query(get).get("sig"));
        assertThrows(IllegalArgumentException.class, () -> storage.presignedPut(CONFIG, "../private", "file", Duration.ofHours(1)));
        assertThrows(IllegalArgumentException.class, () -> storage.presignedGet(CONFIG, "incoming", "file", Duration.ofDays(8)));
        assertThrows(IllegalArgumentException.class, () -> storage.presignedGet(new StorageConfiguration("other", "assets", List.of("**")), "incoming", "file", Duration.ofHours(1)));
        assertEquals(2, calls.size());
    }

    @Test void sdkListingFollowsContinuationAndPreservesMetadataAndProperties() {
        var calls = new CopyOnWriteArrayList<HttpRequest>();
        var storage = storage(request -> {
            calls.add(request);
            boolean next = request.getUrl().getQuery().contains("marker=next-page");
            String name = next ? "incoming/b.png" : "incoming/a.png";
            String xml = "<EnumerationResults ServiceEndpoint=\"https://examplestorage.blob.core.windows.net/\" ContainerName=\"assets\">"
                    + "<Prefix>incoming/</Prefix><Blobs><Blob><Name>" + name + "</Name><Properties>"
                    + "<Creation-Time>Wed, 04 Nov 2026 12:00:00 GMT</Creation-Time><Last-Modified>Wed, 04 Nov 2026 12:01:00 GMT</Last-Modified>"
                    + "<Etag>etag</Etag><Content-Length>3</Content-Length><Content-Type>image/png</Content-Type><BlobType>BlockBlob</BlobType>"
                    + "</Properties><Metadata><owner>alice</owner></Metadata></Blob></Blobs><NextMarker>"
                    + (next ? "" : "next-page") + "</NextMarker></EnumerationResults>";
            return Mono.just(response(request, xml));
        });
        var items = storage.list(CONFIG, "incoming");
        assertEquals(2, items.size()); assertEquals(2, calls.size());
        assertEquals("incoming/", query(URI.create(calls.getFirst().getUrl().toString())).get("prefix"));
        assertEquals("metadata", query(URI.create(calls.getFirst().getUrl().toString())).get("include"));
        assertEquals(List.of("incoming/a.png", "incoming/b.png"), items.stream().map(ListItem::name).toList());
        assertEquals("alice", items.getFirst().metadata().get("owner"));
        assertEquals(3L, items.getFirst().properties().contentLength());
        assertEquals(Instant.parse("2026-11-04T12:01:00Z"), items.getFirst().properties().lastModified());
    }
}
