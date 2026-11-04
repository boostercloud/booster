package io.boostercloud.booster.transport.auth;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Verifies compact, signed JWTs using a configured RSA public key.
 *
 * <p>Only RS256 is accepted. Every token must have an issuer, audience, subject,
 * and integral expiration time. Times use the supplied clock with no implicit
 * clock skew allowance. No key URL or other untrusted header triggers a network
 * request. Key distribution and rotation belong to the application configuration.
 */
public final class JwtVerifier {
    public static final int MAX_TOKEN_LENGTH = 8192;

    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(32)
                    .maxStringLength(MAX_TOKEN_LENGTH).maxNumberLength(32).build())
            .build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Base64.Decoder BASE64 = Base64.getUrlDecoder();
    private static final TypeReference<Map<String, Object>> CLAIMS = new TypeReference<>() {};

    private final RSAPublicKey publicKey;
    private final String issuer;
    private final String audience;
    private final Clock clock;

    public JwtVerifier(RSAPublicKey publicKey, String issuer, String audience) {
        this(publicKey, issuer, audience, Clock.systemUTC());
    }

    public JwtVerifier(RSAPublicKey publicKey, String issuer, String audience, Clock clock) {
        this.publicKey = Objects.requireNonNull(publicKey, "publicKey");
        if (publicKey.getModulus().bitLength() < 2048) {
            throw new IllegalArgumentException("JWT RSA key must contain at least 2048 bits");
        }
        this.issuer = requiredConfiguration(issuer, "issuer");
        this.audience = requiredConfiguration(audience, "audience");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Accepts the compact JWT itself, without the HTTP Bearer prefix. */
    public VerifiedToken verify(String token) {
        try {
            if (token == null || token.isEmpty() || token.length() > MAX_TOKEN_LENGTH) {
                throw new AuthenticationException();
            }
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) {
                throw new AuthenticationException();
            }
            for (String part : parts) {
                if (part.isEmpty() || !part.matches("[A-Za-z0-9_-]+")) {
                    throw new AuthenticationException();
                }
            }
            JsonNode header = JSON.readTree(BASE64.decode(parts[0]));
            if (!header.isObject() || !header.path("alg").isTextual()
                    || !"RS256".equals(header.path("alg").textValue())
                    || header.has("crit") || header.has("b64")) {
                throw new AuthenticationException();
            }
            if (header.has("typ") && (!header.path("typ").isTextual()
                    || !"JWT".equalsIgnoreCase(header.path("typ").textValue()))) {
                throw new AuthenticationException();
            }
            byte[] signatureBytes = BASE64.decode(parts[2]);
            if (signatureBytes.length != (publicKey.getModulus().bitLength() + 7) / 8) {
                throw new AuthenticationException();
            }
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!signature.verify(signatureBytes)) {
                throw new AuthenticationException();
            }

            JsonNode claims = JSON.readTree(BASE64.decode(parts[1]));
            if (!claims.isObject() || !issuer.equals(requiredText(claims, "iss"))
                    || !validAudience(claims.get("aud"))) {
                throw new AuthenticationException();
            }
            String subject = requiredText(claims, "sub");
            long now = clock.instant().getEpochSecond();
            if (now >= requiredTimestamp(claims, "exp")) {
                throw new AuthenticationException();
            }
            if (claims.has("nbf") && now < requiredTimestamp(claims, "nbf")) {
                throw new AuthenticationException();
            }
            if (claims.has("iat") && now < requiredTimestamp(claims, "iat")) {
                throw new AuthenticationException();
            }
            return new VerifiedToken(subject, roles(claims.get("roles")), JSON.convertValue(claims, CLAIMS));
        } catch (Exception failure) {
            // Never include token text, cryptographic details, or claim values in public errors.
            throw new AuthenticationException();
        }
    }

    private boolean validAudience(JsonNode value) {
        if (value == null) {
            return false;
        }
        if (value.isTextual()) {
            return audience.equals(value.textValue());
        }
        if (!value.isArray() || value.isEmpty()) {
            return false;
        }
        boolean matched = false;
        for (JsonNode entry : value) {
            if (!entry.isTextual() || entry.textValue().isBlank()) {
                return false;
            }
            matched |= audience.equals(entry.textValue());
        }
        return matched;
    }

    private static Set<String> roles(JsonNode value) {
        if (value == null) {
            return Set.of();
        }
        if (!value.isArray() || value.size() > 64) {
            throw new AuthenticationException();
        }
        var roles = new LinkedHashSet<String>();
        for (JsonNode entry : value) {
            if (!entry.isTextual() || entry.textValue().isBlank() || entry.textValue().length() > 256) {
                throw new AuthenticationException();
            }
            roles.add(entry.textValue());
        }
        return roles;
    }

    private static String requiredText(JsonNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new AuthenticationException();
        }
        return value.textValue();
    }

    private static long requiredTimestamp(JsonNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            throw new AuthenticationException();
        }
        return value.longValue();
    }

    private static String requiredConfiguration(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("JWT " + name + " must be configured");
        }
        return value;
    }
}
