package com.boostercloud.framework.core.services.tokenverifiers;

import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.concepts.DecodedToken;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The subset of {@code jsonwebtoken} 9's {@code jwt.verify(token, key, { algorithms, issuer, complete: true })} that
 * Booster uses, with the JDK's crypto: the same checks, in the same order, with the same error classes and messages.
 *
 * <ol>
 *   <li>{@code jwt must be provided} (empty token); {@code jwt malformed} (not three dot-separated parts);
 *       {@code invalid token} (not a base64url JWS or a header/payload that is not a JSON object);</li>
 *   <li>the key: a {@link KeyResolver} failure is {@code error in secret or public key callback: <message>};
 *       {@code jwt signature is required}; {@code secret or public key must be provided};
 *       {@code secretOrPublicKey is not valid key material} (a value that is neither text nor a key; text that is not a
 *       public key is a "secret", as in Node);</li>
 *   <li>{@code invalid algorithm} (header {@code alg} not allowed); {@code secretOrPublicKey must be an asymmetric key
 *       when using RS256} (a secret); the key type must match the algorithm;</li>
 *   <li>{@code invalid signature};</li>
 *   <li>{@code invalid nbf value}; {@link NotBeforeError} {@code jwt not active} ({@code nbf > now});
 *       {@code invalid exp value}; {@link TokenExpiredError} {@code jwt expired} ({@code now >= exp}); no clock
 *       tolerance, {@code now} in whole seconds from {@link Dates};</li>
 *   <li>{@code jwt issuer invalid. expected: <issuer>}.</li>
 * </ol>
 */
final class JwtVerifier {

  private static final Pattern JWS = Pattern.compile("^[a-zA-Z0-9\\-_]+?\\.[a-zA-Z0-9\\-_]+?\\.([a-zA-Z0-9\\-_]+)?$");

  private JwtVerifier() {}

  static DecodedToken verify(String token, Object keyOrResolver, String issuer, List<String> algorithms) {
    if (token == null || token.isEmpty()) {
      throw new JsonWebTokenError("jwt must be provided");
    }
    String[] parts = token.split("\\.", -1);
    if (parts.length != 3) {
      throw new JsonWebTokenError("jwt malformed");
    }
    if (!JWS.matcher(token).matches()) {
      throw new JsonWebTokenError("invalid token");
    }
    Map<String, Object> header = decodeObject(parts[0]);
    Map<String, Object> payload = decodeObject(parts[1]);

    Object secretOrPublicKey = keyOrResolver;
    if (keyOrResolver instanceof KeyResolver resolver) {
      try {
        secretOrPublicKey = resolver.resolve(header);
      } catch (RuntimeException e) {
        throw new JsonWebTokenError("error in secret or public key callback: " + e.getMessage(), e);
      }
    }
    boolean hasSignature = !parts[2].trim().isEmpty();
    boolean hasKey = secretOrPublicKey != null && !(secretOrPublicKey instanceof String text && text.isEmpty());
    if (!hasSignature && hasKey) {
      throw new JsonWebTokenError("jwt signature is required");
    }
    if (!hasKey) {
      throw new JsonWebTokenError("secret or public key must be provided");
    }
    PublicKey publicKey = switch (secretOrPublicKey) {
      case PublicKey key -> key;
      case String text -> PemKeys.parsePublicKey(text).orElse(null);
      case byte[] bytes -> PemKeys.parsePublicKey(new String(bytes, StandardCharsets.UTF_8)).orElse(null);
      default -> throw new JsonWebTokenError("secretOrPublicKey is not valid key material");
    };

    Object alg = header.get("alg");
    if (!(alg instanceof String algorithm) || !algorithms.contains(algorithm)) {
      throw new JsonWebTokenError("invalid algorithm");
    }
    if (publicKey == null) {
      throw new JsonWebTokenError("secretOrPublicKey must be an asymmetric key when using " + algorithm);
    }
    validateAsymmetricKey(algorithm, publicKey);
    if (!verifySignature(parts, publicKey)) {
      throw new JsonWebTokenError("invalid signature");
    }

    long now = Dates.nowEpochSeconds();
    if (payload.containsKey("nbf")) {
      if (!(payload.get("nbf") instanceof Number nbf)) {
        throw new JsonWebTokenError("invalid nbf value");
      }
      if (nbf.doubleValue() > now) {
        throw new NotBeforeError("jwt not active", Instant.ofEpochMilli((long) (nbf.doubleValue() * 1000)));
      }
    }
    if (payload.containsKey("exp")) {
      if (!(payload.get("exp") instanceof Number exp)) {
        throw new JsonWebTokenError("invalid exp value");
      }
      if (now >= exp.doubleValue()) {
        throw new TokenExpiredError("jwt expired", Instant.ofEpochMilli((long) (exp.doubleValue() * 1000)));
      }
    }
    if (issuer != null && !Objects.equals(payload.get("iss"), issuer)) {
      throw new JsonWebTokenError("jwt issuer invalid. expected: " + issuer);
    }
    return new DecodedToken(header, payload);
  }

  private static Map<String, Object> decodeObject(String part) {
    try {
      byte[] json = Base64.getUrlDecoder().decode(part);
      Object parsed = BoosterJson.parse(new String(json, StandardCharsets.UTF_8));
      if (parsed instanceof Map<?, ?>) {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) parsed;
        return map;
      }
    } catch (RuntimeException e) {
      // falls through to "invalid token"
    }
    throw new JsonWebTokenError("invalid token");
  }

  private static void validateAsymmetricKey(String algorithm, PublicKey key) {
    String keyType = switch (key.getAlgorithm()) {
      case "RSA" -> "rsa";
      case "EC" -> "ec";
      case "EdDSA", "Ed25519", "Ed448" -> "ed25519";
      default -> key.getAlgorithm().toLowerCase(java.util.Locale.ROOT);
    };
    List<String> allowed = switch (keyType) {
      case "rsa" -> List.of("RS256", "PS256", "RS384", "PS384", "RS512", "PS512");
      case "ec" -> List.of("ES256", "ES384", "ES512");
      case "ed25519" -> List.of("EdDSA");
      default -> throw new IllegalStateException("Unknown key type \"" + keyType + "\".");
    };
    if (!allowed.contains(algorithm)) {
      throw new IllegalStateException("\"alg\" parameter for \"" + keyType + "\" key type must be one of: "
          + String.join(", ", allowed) + ".");
    }
  }

  /** Only {@code RS256} is ever allowed by Booster ({@code algorithms: ['RS256']}). */
  private static boolean verifySignature(String[] parts, PublicKey publicKey) {
    try {
      Signature signature = Signature.getInstance("SHA256withRSA");
      signature.initVerify(publicKey);
      signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
      return signature.verify(Base64.getUrlDecoder().decode(parts[2]));
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      return false;
    }
  }
}
