package io.boostercloud.booster.transport.auth;

import com.sun.net.httpserver.HttpServer;
import io.boostercloud.booster.core.Json;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class JwksVerifierTest {
    @Test void usesPinnedJwksCachesKeysAndRefreshesAfterRotationWithoutFollowingTokenUrls() throws Exception {
        KeyPair first = keys(), second = keys(); MutableClock clock = new MutableClock();
        AtomicInteger requests = new AtomicInteger(); AtomicReference<String> response = new AtomicReference<>(document("first", first));
        HttpServer server = server(response, requests);
        try (var client = HttpClient.newHttpClient()) {
            var verifier = new JwksVerifier(uri(server), "issuer", "app", "custom:role", client, clock, Duration.ofMinutes(10));
            assertEquals(Set.of("editor"), verifier.verify(token("first", first, clock, "custom:role", "editor")).roles());
            verifier.verify(token("first", first, clock, "custom:role", List.of("editor")));
            assertEquals(1, requests.get());
            response.set(document("next", second)); clock.now = clock.now.plusSeconds(6);
            assertEquals("u", verifier.verify(token("next", second, clock, "custom:role", List.of("editor"))).subject());
            assertEquals(2, requests.get());
            assertThrows(AuthenticationException.class, () -> verifier.verify(token("unknown", second, clock, "custom:role", List.of())));
            assertEquals(2, requests.get(), "Unknown kids must not create an unbounded fetch loop");
        } finally { server.stop(0); }
    }

    @Test void rejectsExpiredCacheInvalidDocumentsAndDuplicateIdsWithoutStaleKeyFallback() throws Exception {
        KeyPair pair = keys(); MutableClock clock = new MutableClock();
        AtomicInteger requests = new AtomicInteger(); AtomicReference<String> response = new AtomicReference<>(document("key", pair));
        HttpServer server = server(response, requests);
        try (var client = HttpClient.newHttpClient()) {
            var verifier = new JwksVerifier(uri(server), "issuer", "app", "roles", client, clock, Duration.ofSeconds(5));
            String token = token("key", pair, clock, "roles", List.of("reader"));
            assertEquals(Set.of("reader"), verifier.verify(token).roles());
            response.set("not-json"); clock.now = clock.now.plusSeconds(6);
            assertThrows(AuthenticationException.class, () -> verifier.verify(token));
            var jwk = jwk("key", pair); response.set(Json.MAPPER.writeValueAsString(Map.of("keys", List.of(jwk, jwk))));
            clock.now = clock.now.plusSeconds(6); assertThrows(AuthenticationException.class, () -> verifier.verify(token));
            response.set(" ".repeat(262145)); clock.now = clock.now.plusSeconds(6);
            assertThrows(AuthenticationException.class, () -> verifier.verify(token));
        } finally { server.stop(0); }
    }

    @Test void configurableRoleClaimAcceptsScalarAndArrayWithoutTrustingAnUnconfiguredRoleClaim() throws Exception {
        KeyPair pair = keys(); MutableClock clock = new MutableClock();
        var standard = new JwtVerifier((RSAPublicKey) pair.getPublic(), "issuer", "app", clock);
        var custom = new JwtVerifier((RSAPublicKey) pair.getPublic(), "issuer", "app", "roles", clock);
        String roles = token("key", pair, clock, "roles", List.of("admin"));
        assertTrue(standard.verify(roles).roles().isEmpty());
        assertEquals(Set.of("admin"), custom.verify(roles).roles());
        assertEquals(Set.of("reader"), standard.verify(token("key", pair, clock, "custom:role", "reader")).roles());
    }

    @Test void rejectsInsecureRemoteUrisAndRedirectingClients() {
        assertThrows(IllegalArgumentException.class, () -> new JwksVerifier(URI.create("http://keys.example/jwks"), "issuer", "app"));
        try (var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()) {
            assertThrows(IllegalArgumentException.class, () -> new JwksVerifier(URI.create("https://keys.example/jwks"), "issuer", "app", "roles", client,
                    Clock.systemUTC(), Duration.ofMinutes(10)));
        }
    }

    @Test void issuerOnlyJwksProfileAcceptsOptionalClaimsWhileKeepingKeyValidation() throws Exception {
        KeyPair pair = keys(); MutableClock clock = new MutableClock();
        AtomicInteger requests = new AtomicInteger(); AtomicReference<String> response = new AtomicReference<>(document("key", pair));
        HttpServer server = server(response, requests);
        try (var client = HttpClient.newHttpClient()) {
            var compatible = JwksVerifier.boosterCompatible(uri(server), "issuer", "roles", client, clock, Duration.ofMinutes(15));
            String input = encode(Json.MAPPER.writeValueAsBytes(Map.of("alg", "RS256", "kid", "key"))) + "."
                    + encode(Json.MAPPER.writeValueAsBytes(Map.of("iss", "issuer", "iat", clock.instant().plusSeconds(100).getEpochSecond(), "roles", "reader")));
            Signature signature = Signature.getInstance("SHA256withRSA"); signature.initSign(pair.getPrivate()); signature.update(input.getBytes(StandardCharsets.US_ASCII));
            String token = input + "." + encode(signature.sign());
            var user = io.boostercloud.booster.transport.AuthenticationProvider.jwt(compatible).authenticate("Bearer " + token);
            assertTrue(user.authenticated()); assertNull(user.subject()); assertEquals(Set.of("reader"), user.roles());
            assertEquals(1, requests.get());
            var strict = new JwksVerifier(uri(server), "issuer", "app", "roles", client, clock, Duration.ofMinutes(15));
            assertThrows(AuthenticationException.class, () -> strict.verify(token));
        } finally { server.stop(0); }
    }

    private static HttpServer server(AtomicReference<String> response, AtomicInteger requests) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 10);
        server.createContext("/jwks", exchange -> {
            requests.incrementAndGet(); byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
            try (exchange) { exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); }
        }); server.start(); return server;
    }
    private static URI uri(HttpServer server) { return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/jwks"); }
    private static KeyPair keys() throws Exception { var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); return generator.generateKeyPair(); }
    private static String document(String id, KeyPair pair) throws Exception { return Json.MAPPER.writeValueAsString(Map.of("keys", List.of(jwk(id, pair)))); }
    private static Map<String,Object> jwk(String id, KeyPair pair) {
        RSAPublicKey key = (RSAPublicKey) pair.getPublic();
        return Map.of("kid", id, "kty", "RSA", "use", "sig", "alg", "RS256", "n", unsigned(key.getModulus()), "e", unsigned(key.getPublicExponent()));
    }
    private static String unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray(); if (bytes[0] == 0) bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        return encode(bytes);
    }
    private static String token(String kid, KeyPair pair, Clock clock, String roleClaim, Object roles) throws Exception {
        String header = encode(Json.MAPPER.writeValueAsBytes(Map.of("alg", "RS256", "kid", kid, "jku", "https://untrusted.invalid/key")));
        String claims = encode(Json.MAPPER.writeValueAsBytes(Map.of("iss", "issuer", "aud", "app", "sub", "u", "exp", clock.instant().plusSeconds(600).getEpochSecond(), roleClaim, roles)));
        String input = header + "." + claims;
        Signature signature = Signature.getInstance("SHA256withRSA"); signature.initSign(pair.getPrivate()); signature.update(input.getBytes(StandardCharsets.US_ASCII));
        return input + "." + encode(signature.sign());
    }
    private static String encode(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-04T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
