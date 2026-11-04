package io.boostercloud.booster.transport.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JwtVerifierTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long NOW = Instant.parse("2026-11-04T12:00:00Z").getEpochSecond();
    private static KeyPair keys;
    private static JwtVerifier verifier;

    @BeforeAll
    static void prepareKey() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        verifier = new JwtVerifier((RSAPublicKey) keys.getPublic(), "https://issuer.example", "booster",
                Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC));
    }

    @Test
    void verifiesSignedClaimsAndRoles() throws Exception {
        var claims = claims();
        claims.put("custom:role", List.of("writer", "reader", "writer"));
        claims.put("profile", Map.of("name", "Test user", "nullable", List.of("ok")));
        VerifiedToken identity = verifier.verify(sign(claims));
        assertEquals("user-123", identity.subject());
        assertEquals("RS256", identity.header().get("alg"));
        assertThrows(UnsupportedOperationException.class, () -> identity.header().put("kid", "other"));
        var user = io.boostercloud.booster.transport.AuthenticationProvider.jwt(verifier).authenticate("Bearer " + sign(claims));
        assertEquals(identity.header(), user.header()); assertEquals("user-123", user.id()); assertEquals("user-123", user.username());
        assertEquals(Set.of("writer", "reader"), identity.roles());
        assertEquals("https://issuer.example", identity.claims().get("iss"));
        assertThrows(UnsupportedOperationException.class, () -> identity.claims().put("admin", true));
        @SuppressWarnings("unchecked")
        var profile = (Map<String, Object>) identity.claims().get("profile");
        assertThrows(UnsupportedOperationException.class, () -> profile.put("name", "Changed"));
    }

    @Test
    void acceptsAudienceArrayAndAbsentRoles() throws Exception {
        var claims = claims();
        claims.put("aud", List.of("another-service", "booster"));
        claims.put("nbf", NOW);
        claims.put("iat", NOW - 30);
        VerifiedToken identity = verifier.verify(sign(claims));
        assertTrue(identity.roles().isEmpty());
    }

    @Test
    void rejectsTamperingAndWrongSigningKey() throws Exception {
        String token = sign(claims());
        String[] parts = token.split("\\.");
        var changed = claims();
        changed.put("roles", List.of("administrator"));
        reject(parts[0] + "." + encode(JSON.writeValueAsBytes(changed)) + "." + parts[2]);
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        reject(sign("{\"alg\":\"RS256\"}", JSON.writeValueAsString(claims()), generator.generateKeyPair()));
    }

    @Test
    void rejectsAlgorithmConfusionAndCriticalHeaders() throws Exception {
        for (String header : List.of("{\"alg\":\"none\"}", "{\"alg\":\"HS256\"}",
                "{\"alg\":\"RS512\"}", "{\"alg\":\"RS256\",\"crit\":[\"custom\"]}",
                "{\"alg\":\"RS256\",\"b64\":false}", "{\"alg\":\"RS256\",\"typ\":\"other\"}")) {
            reject(sign(header, JSON.writeValueAsString(claims()), keys));
        }
    }

    @Test
    void rejectsExpiredAndFutureTokens() throws Exception {
        for (long expiration : List.of(NOW, NOW - 1)) {
            var claims = claims();
            claims.put("exp", expiration);
            reject(sign(claims));
        }
        for (String field : List.of("nbf", "iat")) {
            var claims = claims();
            claims.put(field, NOW + 1);
            reject(sign(claims));
        }
    }

    @Test
    void requiresIntegralExpirationAndIdentityClaims() throws Exception {
        for (String field : List.of("exp", "sub", "aud", "iss")) {
            var claims = claims();
            claims.remove(field);
            reject(sign(claims));
        }
        for (Object invalidExpiration : List.of("9999999999", NOW + 1.5, true)) {
            var claims = claims();
            claims.put("exp", invalidExpiration);
            reject(sign(claims));
        }
        var claims = claims();
        claims.put("sub", " ");
        reject(sign(claims));
    }

    @Test
    void rejectsWrongIssuerAndAudience() throws Exception {
        for (String field : List.of("iss", "aud")) {
            var claims = claims();
            claims.put(field, "wrong-value");
            reject(sign(claims));
        }
        var claims = claims();
        claims.put("aud", List.of("booster", 123));
        reject(sign(claims));
    }

    @Test
    void rejectsDuplicateClaimsTrailingJsonAndMalformedRoles() throws Exception {
        String payload = JSON.writeValueAsString(claims());
        reject(sign("{\"alg\":\"RS256\",\"alg\":\"RS256\"}", payload, keys));
        reject(sign("{\"alg\":\"RS256\"}", payload.replace("\"exp\":", "\"exp\":0,\"exp\":"), keys));
        reject(sign("{\"alg\":\"RS256\"}", payload + " {}", keys));
        for (Object roles : List.of(123, List.of(123), List.of(""))) {
            var claims = claims();
            claims.put("custom:role", roles);
            reject(sign(claims));
        }
    }

    @Test
    void rejectsMalformedAndOversizedTokensWithoutExposingTheirContents() {
        for (String token : List.of("", "Bearer secret", "a.b", "a.b.c.d", "a..c", "=.b.c",
                "sensitive-token".repeat(1000))) {
            reject(token);
        }
        reject(null);
    }

    @Test
    void rejectsWeakKeysAndMissingConfiguration() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        assertThrows(IllegalArgumentException.class, () -> new JwtVerifier(
                (RSAPublicKey) generator.generateKeyPair().getPublic(), "issuer", "audience"));
        assertThrows(IllegalArgumentException.class,
                () -> new JwtVerifier((RSAPublicKey) keys.getPublic(), "", "audience"));
    }

    @Test
    void explicitBoosterProfileAcceptsIssuerOnlyTokensAndPreservesVerifiedIdentityWithoutSubject() throws Exception {
        var compatible = JwtVerifier.boosterCompatible((RSAPublicKey) keys.getPublic(), "https://issuer.example", "custom:role",
                Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC));
        var minimal = new LinkedHashMap<String,Object>(); minimal.put("iss", "https://issuer.example");
        minimal.put("iat", NOW + 500); minimal.put("email", "reader@example.test");
        minimal.put("aud", "not-the-strict-audience"); minimal.put("custom:role", List.of("reader", ""));
        String token = sign(minimal);
        var user = io.boostercloud.booster.transport.AuthenticationProvider.jwt(compatible).authenticate("Bearer " + token);
        assertNull(user.id()); assertTrue(user.authenticated()); assertTrue(user.verifiedIdentity());
        assertEquals("reader@example.test", user.username()); assertEquals(Set.of("reader", ""), user.roles());
        assertFalse(io.boostercloud.booster.core.UserContext.anonymous().authenticated());
        assertEquals(user, io.boostercloud.booster.core.Json.MAPPER.readValue(
                io.boostercloud.booster.core.Json.MAPPER.writeValueAsBytes(user), io.boostercloud.booster.core.UserContext.class));
        assertTrue(io.boostercloud.booster.core.Json.MAPPER.readValue(
                "{\"subject\":\"legacy\",\"roles\":[],\"claims\":{}}", io.boostercloud.booster.core.UserContext.class).authenticated());
        reject(token); // Existing constructors retain the strict claim contract.
    }

    @Test
    void boosterProfileEnforcesPresentNumericDatesIssuerAndSignature() throws Exception {
        var compatible = JwtVerifier.boosterCompatible((RSAPublicKey) keys.getPublic(), "https://issuer.example", "custom:role",
                Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC));
        for (Map<String,Object> additions : List.<Map<String,Object>>of(
                Map.of("exp", NOW), Map.of("exp", "later"), Map.of("nbf", NOW + 0.5),
                Map.of("nbf", false), Map.of("iss", "wrong"))) {
            var payload = new LinkedHashMap<String,Object>(); payload.put("iss", "https://issuer.example"); payload.putAll(additions);
            assertThrows(AuthenticationException.class, () -> compatible.verify(sign(payload)));
        }
        var valid = new LinkedHashMap<String,Object>(); valid.put("iss", "https://issuer.example");
        valid.put("exp", NOW + 0.5); valid.put("nbf", NOW - 0.5); valid.put("iat", NOW + 100); valid.put("custom:role", false);
        assertTrue(compatible.verify(sign(valid)).roles().isEmpty());
        String token = sign(valid); String[] parts = token.split("\\.");
        valid.put("custom:role", "administrator");
        assertThrows(AuthenticationException.class, () -> compatible.verify(parts[0] + "." + encode(JSON.writeValueAsBytes(valid)) + "." + parts[2]));
    }

    private static Map<String, Object> claims() {
        var claims = new LinkedHashMap<String, Object>();
        claims.put("iss", "https://issuer.example");
        claims.put("aud", "booster");
        claims.put("sub", "user-123");
        claims.put("exp", NOW + 300);
        return claims;
    }

    private static String sign(Map<String, Object> claims) throws Exception {
        return sign("{\"alg\":\"RS256\",\"typ\":\"JWT\"}", JSON.writeValueAsString(claims), keys);
    }

    private static String sign(String header, String claims, KeyPair pair) throws Exception {
        String unsigned = encode(header.getBytes(StandardCharsets.UTF_8)) + "."
                + encode(claims.getBytes(StandardCharsets.UTF_8));
        var signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(pair.getPrivate());
        signer.update(unsigned.getBytes(StandardCharsets.US_ASCII));
        return unsigned + "." + encode(signer.sign());
    }

    private static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void reject(String token) {
        var exception = assertThrows(AuthenticationException.class, () -> verifier.verify(token));
        assertEquals("Invalid bearer token", exception.getMessage());
        assertNull(exception.getCause());
    }
}
