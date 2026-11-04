package io.boostercloud.booster.transport.auth;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Pinned JWKS URL, bounded cache and rate-limited unknown-key rotation. Only RS256 signing keys are used. */
public final class JwksVerifier implements TokenVerifier {
    private static final int MAX_DOCUMENT_BYTES = 262144;
    private static final Duration REFRESH_COOLDOWN = Duration.ofSeconds(5);
    private final URI uri;
    private final String issuer;
    private final String audience;
    private final String rolesClaim;
    private final HttpClient client;
    private final Clock clock;
    private final Duration lifetime;
    private final boolean boosterCompatible;
    private Map<String, RSAPublicKey> keys = Map.of();
    private Instant expires = Instant.MIN;
    private Instant nextRefresh = Instant.MIN;

    public JwksVerifier(URI uri, String issuer, String audience) {
        this(uri, issuer, audience, JwtVerifier.DEFAULT_ROLES_CLAIM);
    }
    public JwksVerifier(URI uri, String issuer, String audience, String rolesClaim) {
        this(uri, issuer, audience, rolesClaim, configuredClient(), Clock.systemUTC(), Duration.ofMinutes(10));
    }
    public JwksVerifier(URI uri, String issuer, String audience, String rolesClaim,
                        HttpClient client, Clock clock, Duration cacheLifetime) {
        this(uri, issuer, audience, rolesClaim, client, clock, cacheLifetime, false);
    }
    /** Issuer-only claim validation matching Booster's JWKS token verifier. */
    public static JwksVerifier boosterCompatible(URI uri, String issuer) {
        return boosterCompatible(uri, issuer, JwtVerifier.DEFAULT_ROLES_CLAIM);
    }
    public static JwksVerifier boosterCompatible(URI uri, String issuer, String rolesClaim) {
        return boosterCompatible(uri, issuer, rolesClaim, configuredClient(), Clock.systemUTC(), Duration.ofMinutes(15));
    }
    public static JwksVerifier boosterCompatible(URI uri, String issuer, String rolesClaim,
                                               HttpClient client, Clock clock, Duration cacheLifetime) {
        return new JwksVerifier(uri, issuer, null, rolesClaim, client, clock, cacheLifetime, true);
    }
    private JwksVerifier(URI uri, String issuer, String audience, String rolesClaim,
                         HttpClient client, Clock clock, Duration cacheLifetime, boolean boosterCompatible) {
        this.uri = Objects.requireNonNull(uri);
        boolean loopback = "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()) || "[::1]".equals(uri.getHost());
        if ((!"https".equals(uri.getScheme()) && !("http".equals(uri.getScheme()) && loopback))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("JWKS requires a configured HTTPS URI (HTTP allowed only on loopback)");
        if (issuer == null || issuer.isBlank() || !boosterCompatible && (audience == null || audience.isBlank()) || rolesClaim == null || rolesClaim.isBlank())
            throw new IllegalArgumentException("Issuer, strict-profile audience and roles claim are required");
        if (cacheLifetime.isNegative() || cacheLifetime.compareTo(Duration.ofSeconds(5)) < 0
                || cacheLifetime.compareTo(Duration.ofHours(24)) > 0)
            throw new IllegalArgumentException("JWKS cache lifetime must be between 5 seconds and 24 hours");
        this.issuer = issuer; this.audience = audience; this.rolesClaim = rolesClaim;
        this.client = Objects.requireNonNull(client); this.clock = Objects.requireNonNull(clock); this.lifetime = cacheLifetime;
        this.boosterCompatible = boosterCompatible;
        if (client.followRedirects() != HttpClient.Redirect.NEVER)
            throw new IllegalArgumentException("JWKS HTTP redirects must be disabled");
    }

    @Override public VerifiedToken verify(String token) {
        try {
            if (token == null || token.length() > JwtVerifier.MAX_TOKEN_LENGTH) throw new AuthenticationException();
            String[] segments = token.split("\\.", -1);
            if (segments.length != 3 || !segments[0].matches("[A-Za-z0-9_-]+")) throw new AuthenticationException();
            JsonNode header = json().readTree(Base64.getUrlDecoder().decode(segments[0]));
            if (!"RS256".equals(header.path("alg").asText()) || !header.path("kid").isTextual()
                    || header.path("kid").asText().isBlank() || header.path("kid").asText().length() > 256
                    || header.has("crit") || header.has("b64")) throw new AuthenticationException();
            RSAPublicKey key = key(header.path("kid").asText());
            return (boosterCompatible ? JwtVerifier.boosterCompatible(key, issuer, rolesClaim, clock)
                    : new JwtVerifier(key, issuer, audience, rolesClaim, clock)).verify(token);
        } catch (Exception failure) { throw new AuthenticationException(); }
    }

    private synchronized RSAPublicKey key(String id) throws Exception {
        Instant now = clock.instant();
        if (!now.isBefore(expires) || !keys.containsKey(id)) {
            if (now.isBefore(nextRefresh)) throw new AuthenticationException();
            nextRefresh = now.plus(REFRESH_COOLDOWN);
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).header("Accept", "application/json").GET().build();
            var body = new BoundedBody();
            var pending = client.sendAsync(request, ignored -> body);
            HttpResponse<byte[]> response;
            try { response = pending.get(5, TimeUnit.SECONDS); }
            finally { body.cancel(); if (!pending.isDone()) pending.cancel(true); }
            if (response.statusCode() != 200) throw new AuthenticationException();
            byte[] bytes = response.body();
            JsonNode document = json().readTree(bytes);
            JsonNode entries = document.path("keys");
            if (!entries.isArray() || entries.size() > 64) throw new AuthenticationException();
            Map<String, RSAPublicKey> refreshed = new LinkedHashMap<>();
            for (JsonNode item : entries) {
                if (!"RSA".equals(item.path("kty").asText()) || (item.has("use") && !"sig".equals(item.path("use").asText()))
                        || (item.has("alg") && !"RS256".equals(item.path("alg").asText()))) continue;
                if (item.has("key_ops") && (!item.path("key_ops").isArray()
                        || !containsVerify(item.path("key_ops")))) continue;
                String kid = item.path("kid").asText("");
                if (kid.isBlank() || kid.length() > 256 || refreshed.containsKey(kid)) throw new AuthenticationException();
                BigInteger n = unsigned(item.path("n")), e = unsigned(item.path("e"));
                if (n.bitLength() < 2048 || n.bitLength() > 8192 || e.compareTo(BigInteger.valueOf(3)) < 0 || !e.testBit(0))
                    throw new AuthenticationException();
                refreshed.put(kid, (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e)));
            }
            keys = Map.copyOf(refreshed); expires = now.plus(lifetime);
        }
        RSAPublicKey key = keys.get(id);
        if (key == null) throw new AuthenticationException();
        return key;
    }
    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public synchronized void onSubscribe(Flow.Subscription value) {
            if (subscription != null || result.isDone()) { value.cancel(); return; }
            subscription = value; value.request(1);
        }
        @Override public synchronized void onNext(List<ByteBuffer> chunks) {
            if (result.isDone()) return;
            for (ByteBuffer chunk : chunks) {
                if (chunk.remaining() > MAX_DOCUMENT_BYTES - bytes.size()) {
                    result.completeExceptionally(new AuthenticationException()); subscription.cancel(); return;
                }
                byte[] copy = new byte[chunk.remaining()]; chunk.get(copy); bytes.writeBytes(copy);
            }
            subscription.request(1);
        }
        @Override public synchronized void onError(Throwable error) { result.completeExceptionally(error); }
        @Override public synchronized void onComplete() { result.complete(bytes.toByteArray()); }
        synchronized void cancel() {
            if (!result.isDone()) result.completeExceptionally(new AuthenticationException());
            if (subscription != null) subscription.cancel();
        }
    }

    private static BigInteger unsigned(JsonNode node) {
        if (!node.isTextual() || node.asText().length() > 1500 || !node.asText().matches("[A-Za-z0-9_-]+"))
            throw new AuthenticationException();
        return new BigInteger(1, Base64.getUrlDecoder().decode(node.asText()));
    }
    private static boolean containsVerify(JsonNode operations) {
        for (JsonNode op : operations) if (op.isTextual() && "verify".equals(op.asText())) return true;
        return false;
    }
    private static JsonMapper json() {
        return JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    }
    private static HttpClient configuredClient() {
        var builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER);
        String proxy = System.getenv("HTTPS_PROXY");
        if (proxy == null) proxy = System.getenv("https_proxy");
        if (proxy != null && !proxy.isBlank()) {
            URI proxyUri = URI.create(proxy);
            builder.proxy(ProxySelector.of(new InetSocketAddress(proxyUri.getHost(), proxyUri.getPort() < 0 ? 8080 : proxyUri.getPort())));
        }
        return builder.build();
    }
}
