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
 * <p>Only RS256 is accepted. By default every token must have an issuer, audience, subject,
 * and integral expiration time. Times use the supplied clock with no implicit
 * clock skew allowance. No key URL or other untrusted header triggers a network
 * request. The explicit {@link #boosterCompatible(RSAPublicKey, String)} profile follows
 * Booster's issuer-only configuration: audience is not checked, subject and expiry are
 * optional, present expiry/not-before dates are checked, and issued-at is informational.
 */
public final class JwtVerifier implements TokenVerifier {
    public static final int MAX_TOKEN_LENGTH = 8192;
    public static final String DEFAULT_ROLES_CLAIM = "custom:role";

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
    private final String rolesClaim;
    private final boolean boosterCompatible;

    public JwtVerifier(RSAPublicKey publicKey, String issuer, String audience) {
        this(publicKey, issuer, audience, Clock.systemUTC());
    }

    public JwtVerifier(RSAPublicKey publicKey, String issuer, String audience, Clock clock) {
        this(publicKey, issuer, audience, DEFAULT_ROLES_CLAIM, clock);
    }

    public JwtVerifier(RSAPublicKey publicKey, String issuer, String audience, String rolesClaim) {
        this(publicKey, issuer, audience, rolesClaim, Clock.systemUTC());
    }

    public JwtVerifier(RSAPublicKey publicKey, String issuer, String audience, String rolesClaim, Clock clock) {
        this(publicKey, issuer, audience, rolesClaim, clock, false);
    }

    /** Opt in to the TypeScript Booster issuer-only claim contract, retaining RSA signature validation. */
    public static JwtVerifier boosterCompatible(RSAPublicKey publicKey, String issuer) {
        return boosterCompatible(publicKey, issuer, DEFAULT_ROLES_CLAIM, Clock.systemUTC());
    }
    public static JwtVerifier boosterCompatible(RSAPublicKey publicKey, String issuer, String rolesClaim) {
        return boosterCompatible(publicKey, issuer, rolesClaim, Clock.systemUTC());
    }
    public static JwtVerifier boosterCompatible(RSAPublicKey publicKey, String issuer, String rolesClaim, Clock clock) {
        return new JwtVerifier(publicKey, issuer, null, rolesClaim, clock, true);
    }

    private JwtVerifier(RSAPublicKey publicKey, String issuer, String audience, String rolesClaim, Clock clock,
                        boolean boosterCompatible) {
        this.publicKey = Objects.requireNonNull(publicKey, "publicKey");
        if (publicKey.getModulus().bitLength() < 2048) {
            throw new IllegalArgumentException("JWT RSA key must contain at least 2048 bits");
        }
        this.issuer = requiredConfiguration(issuer, "issuer");
        this.audience = boosterCompatible ? null : requiredConfiguration(audience, "audience");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.rolesClaim = requiredConfiguration(rolesClaim, "roles claim");
        this.boosterCompatible = boosterCompatible;
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
                    || !boosterCompatible && !validAudience(claims.get("aud"))) {
                throw new AuthenticationException();
            }
            String subject = boosterCompatible ? optionalSubject(claims) : requiredText(claims, "sub");
            long now = clock.instant().getEpochSecond();
            if (boosterCompatible) {
                if (claims.has("exp") && numericTimestamp(claims, "exp").compareTo(java.math.BigDecimal.valueOf(now)) <= 0)
                    throw new AuthenticationException();
                if (claims.has("nbf") && numericTimestamp(claims, "nbf").compareTo(java.math.BigDecimal.valueOf(now)) > 0)
                    throw new AuthenticationException();
            } else {
                if (now >= requiredTimestamp(claims, "exp")) throw new AuthenticationException();
                if (claims.has("nbf") && now < requiredTimestamp(claims, "nbf")) throw new AuthenticationException();
                if (claims.has("iat") && now < requiredTimestamp(claims, "iat")) throw new AuthenticationException();
            }
            return new VerifiedToken(subject, roles(claims.get(rolesClaim)), JSON.convertValue(claims, CLAIMS), JSON.convertValue(header, CLAIMS));
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

    private Set<String> roles(JsonNode value) {
        if (value == null || value.isNull()) {
            return Set.of();
        }
        if (value.isTextual()) {
            if (boosterCompatible && value.textValue().isEmpty()) return Set.of();
            if (!boosterCompatible && (value.textValue().isBlank() || value.textValue().length() > 256)) throw new AuthenticationException();
            return Set.of(value.textValue());
        }
        if (boosterCompatible && (value.isBoolean() && !value.booleanValue() || value.isNumber() && value.decimalValue().signum() == 0)) return Set.of();
        if (!value.isArray() || !boosterCompatible && value.size() > 64) {
            throw new AuthenticationException();
        }
        var roles = new LinkedHashSet<String>();
        for (JsonNode entry : value) {
            if (!entry.isTextual() || !boosterCompatible && (entry.textValue().isBlank() || entry.textValue().length() > 256)) {
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

    private static String optionalSubject(JsonNode claims) {
        JsonNode subject = claims.get("sub");
        if (subject == null || subject.isNull()) return null;
        if (!subject.isTextual()) throw new AuthenticationException();
        return subject.textValue();
    }
    private static java.math.BigDecimal numericTimestamp(JsonNode claims, String name) {
        JsonNode value = claims.get(name);
        if (value == null || !value.isNumber() || !Double.isFinite(value.doubleValue())) throw new AuthenticationException();
        return value.decimalValue();
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
