package com.boostercloud.application.tester;

import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.concepts.DecodedToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAKey;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Creates valid RS256 JWTs for tests, signed with a real private key. Port of {@code token-helper.ts}, which signs
 * with {@code jsonwebtoken}; this class reproduces what {@code jwt.sign(payload, key, options)} produces for the
 * options Booster uses, with the JDK only ({@code SHA256withRSA}).
 *
 * <p>The default constructor loads the bundled key {@value #PRIVATE_KEY_RESOURCE}, a byte-identical copy of
 * {@code packages/application-tester/keys/private.key} (PEM PKCS#1). Its public half, a copy of
 * {@code packages/framework-integration-tests/assets/certs/public.key}, is {@link #publicKeyPem()}: configure the
 * application under test with {@code PublicKeyTokenVerifier("booster", publicKeyPem, "booster:role")}.
 *
 * <p>A token for {@code forUser("admin@example.com", "Admin", expiresIn 3600, customClaims {canLogSomething: true})}:
 *
 * <pre>
 * header  {"alg":"RS256","typ":"JWT","kid":"booster"}
 * payload {"id":"admin@example.com","email":"admin@example.com","canLogSomething":true,"booster:role":"Admin",
 *          "iat":1794268800,"exp":1794272400,"iss":"booster","sub":"admin@example.com"}
 * </pre>
 *
 * Claims, in this order: {@code id} and {@code email} (both the email), the custom claims (which may override them),
 * {@code booster:role} (a single string, only when the role is neither {@code null} nor empty, overriding a custom
 * claim of that name), {@code iat} (seconds, from {@link Dates#nowEpochSeconds()} unless a non-zero custom
 * {@code iat} is given), {@code nbf} = {@code iat + notBefore} (only when {@code notBefore} is neither {@code null}
 * nor {@code 0}), {@code exp} = {@code iat + expiresIn} (whenever {@code expiresIn} is not {@code null}, so
 * {@code 0} yields an expired token), {@code iss} = {@code booster}, {@code sub} = the email. Without
 * {@code expiresIn} the token never expires.
 *
 * <p>The validations of {@code jsonwebtoken} are kept, with its messages, as {@link IllegalArgumentException}s:
 * {@code "iat"/"exp"/"nbf" should be a number of seconds} (custom claims of the wrong type), {@code Bad
 * "options.expiresIn" option the payload already has an "exp" property.} (and the {@code notBefore}/{@code nbf}
 * variant), {@code "subject" must be a string} (a {@code null} email), {@code Bad "options.issuer" option. The
 * payload already has an "iss" property.} (and the {@code subject}/{@code sub} variant), and
 * {@code secretOrPrivateKey has a minimum key size of 2048 bits for RS256}.
 */
public final class TokenHelper {

  /** Classpath resource of the bundled private key (PEM PKCS#1). */
  public static final String PRIVATE_KEY_RESOURCE = "/com/boostercloud/application/tester/keys/private.key";

  /** Classpath resource of the bundled public key (PEM X.509 SubjectPublicKeyInfo). */
  public static final String PUBLIC_KEY_RESOURCE = "/com/boostercloud/application/tester/keys/public.key";

  /** The JOSE {@code alg} of every token. */
  public static final String ALGORITHM = "RS256";

  /** The JOSE {@code kid} of every token. */
  public static final String KEY_ID = "booster";

  /** The {@code iss} claim of every token. */
  public static final String ISSUER = "booster";

  /** The claim that carries the role. */
  public static final String ROLE_CLAIM = "booster:role";

  private static final int MINIMUM_RSA_KEY_SIZE = 2048;
  private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

  private final PrivateKey privateKey;
  private final LongSupplier nowEpochSeconds;

  /**
   * Loads the bundled private key ({@value #PRIVATE_KEY_RESOURCE}).
   *
   * @throws IllegalStateException when the resource is missing (TypeScript: {@code ENOENT})
   */
  public TokenHelper() {
    this(readResource(PRIVATE_KEY_RESOURCE));
  }

  /**
   * Signs with another key.
   *
   * @param privateKeyPem a PEM RSA private key, PKCS#1 ({@code BEGIN RSA PRIVATE KEY}) or PKCS#8
   *     ({@code BEGIN PRIVATE KEY})
   * @throws IllegalArgumentException when the text is not such a key
   */
  public TokenHelper(String privateKeyPem) {
    this(PemKeys.privateKey(privateKeyPem), Dates::nowEpochSeconds);
  }

  TokenHelper(PrivateKey privateKey, LongSupplier nowEpochSeconds) {
    this.privateKey = Objects.requireNonNull(privateKey, "privateKey");
    this.nowEpochSeconds = Objects.requireNonNull(nowEpochSeconds, "nowEpochSeconds");
  }

  /**
   * @param privateKeyFile a PEM RSA private key file
   * @return a helper signing with that key
   * @throws UncheckedIOException when the file cannot be read
   */
  public static TokenHelper fromPrivateKeyFile(Path privateKeyFile) {
    try {
      return new TokenHelper(Files.readString(privateKeyFile, StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * {@code forUser(email)}: a token without role and options.
   *
   * @param email the user email ({@code id}, {@code email} and {@code sub})
   * @return the signed JWT
   */
  public String forUser(String email) {
    return forUser(email, null, null);
  }

  /**
   * {@code forUser(email, role)}.
   *
   * @param email the user email ({@code id}, {@code email} and {@code sub})
   * @param role the {@code booster:role} claim; {@code null} or empty for none
   * @return the signed JWT
   */
  public String forUser(String email, String role) {
    return forUser(email, role, null);
  }

  /**
   * {@code forUser(email, role, tokenOptions)}.
   *
   * @param email the user email ({@code id}, {@code email} and {@code sub})
   * @param role the {@code booster:role} claim; {@code null} or empty for none
   * @param tokenOptions expiration, not-before and custom claims; {@code null} for none
   * @return the signed JWT ({@code header.payload.signature}, base64url without padding)
   * @throws IllegalArgumentException for the {@code jsonwebtoken} validation errors listed in the class description
   */
  public String forUser(String email, String role, TokenOptions tokenOptions) {
    TokenOptions options = tokenOptions == null ? TokenOptions.none() : tokenOptions;
    Long expiresIn = options.expiresIn();
    Long notBefore = options.notBefore() != null && options.notBefore() != 0 ? options.notBefore() : null;

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", email);
    payload.put("email", email);
    if (options.customClaims() != null) {
      payload.putAll(options.customClaims());
    }
    if (role != null && !role.isEmpty()) {
      payload.put(ROLE_CLAIM, role);
    }

    checkKeySize();
    validateRegisteredClaims(payload);
    if (payload.containsKey("exp") && expiresIn != null) {
      throw new IllegalArgumentException(
          "Bad \"options.expiresIn\" option the payload already has an \"exp\" property.");
    }
    if (payload.containsKey("nbf") && notBefore != null) {
      throw new IllegalArgumentException(
          "Bad \"options.notBefore\" option the payload already has an \"nbf\" property.");
    }
    if (email == null) {
      throw new IllegalArgumentException("\"subject\" must be a string");
    }

    Number timestamp = isTruthyNumber(payload.get("iat")) ? (Number) payload.get("iat") : nowEpochSeconds.getAsLong();
    payload.put("iat", timestamp);
    if (notBefore != null) {
      payload.put("nbf", plusSeconds(timestamp, notBefore));
    }
    if (expiresIn != null) {
      payload.put("exp", plusSeconds(timestamp, expiresIn));
    }
    putRegisteredClaim(payload, "issuer", "iss", ISSUER);
    putRegisteredClaim(payload, "subject", "sub", email);

    Map<String, Object> header = new LinkedHashMap<>();
    header.put("alg", ALGORITHM);
    header.put("typ", "JWT");
    header.put("kid", KEY_ID);
    String signingInput = encode(BoosterJson.toJson(header)) + "." + encode(BoosterJson.toJson(payload));
    return signingInput + "." + BASE64_URL.encodeToString(sign(signingInput));
  }

  /**
   * @return the bundled public key in PEM ({@value #PUBLIC_KEY_RESOURCE}), the counterpart of the default key
   */
  public static String publicKeyPem() {
    return readResource(PUBLIC_KEY_RESOURCE);
  }

  /**
   * @return the bundled public key
   */
  public static PublicKey publicKey() {
    return PemKeys.publicKey(publicKeyPem());
  }

  /**
   * Decodes a JWT <b>without verifying it</b> (a test convenience, Java only).
   *
   * @param token a compact JWT
   * @return its header and payload as raw maps
   * @throws IllegalArgumentException when the text is not a JWT
   */
  public static DecodedToken decode(String token) {
    String[] parts = token == null ? new String[0] : token.split("\\.", -1);
    if (parts.length != 3) {
      throw new IllegalArgumentException("jwt malformed");
    }
    try {
      Base64.Decoder decoder = Base64.getUrlDecoder();
      Map<String, Object> header = BoosterJson.parseObject(new String(decoder.decode(parts[0]), StandardCharsets.UTF_8));
      Map<String, Object> payload =
          BoosterJson.parseObject(new String(decoder.decode(parts[1]), StandardCharsets.UTF_8));
      return new DecodedToken(header, payload);
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("invalid token", e);
    }
  }

  private void checkKeySize() {
    if (privateKey instanceof RSAKey rsaKey && rsaKey.getModulus().bitLength() < MINIMUM_RSA_KEY_SIZE) {
      throw new IllegalArgumentException(
          "secretOrPrivateKey has a minimum key size of " + MINIMUM_RSA_KEY_SIZE + " bits for " + ALGORITHM);
    }
  }

  /** {@code validatePayload}: the registered time claims must be numbers. */
  private static void validateRegisteredClaims(Map<String, Object> payload) {
    for (Map.Entry<String, Object> claim : payload.entrySet()) {
      String name = claim.getKey();
      if ((name.equals("iat") || name.equals("exp") || name.equals("nbf")) && !(claim.getValue() instanceof Number)) {
        throw new IllegalArgumentException("\"" + name + "\" should be a number of seconds");
      }
    }
  }

  private static void putRegisteredClaim(Map<String, Object> payload, String option, String claim, Object value) {
    if (payload.containsKey(claim)) {
      throw new IllegalArgumentException(
          "Bad \"options." + option + "\" option. The payload already has an \"" + claim + "\" property.");
    }
    payload.put(claim, value);
  }

  private static boolean isTruthyNumber(Object value) {
    return value instanceof Number number && number.doubleValue() != 0 && !Double.isNaN(number.doubleValue());
  }

  private static Number plusSeconds(Number timestamp, long seconds) {
    return switch (timestamp) {
      case Integer i -> i.longValue() + seconds;
      case Long l -> l + seconds;
      case Short s -> s.longValue() + seconds;
      case Byte b -> b.longValue() + seconds;
      case BigInteger big -> big.add(BigInteger.valueOf(seconds));
      default -> timestamp.doubleValue() + seconds;
    };
  }

  private byte[] sign(String signingInput) {
    try {
      Signature signature = Signature.getInstance("SHA256withRSA");
      signature.initSign(privateKey);
      signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
      return signature.sign();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Unable to sign the token: " + e.getMessage(), e);
    }
  }

  private static String encode(String json) {
    return BASE64_URL.encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }

  private static String readResource(String resource) {
    try (InputStream stream = TokenHelper.class.getResourceAsStream(resource)) {
      if (stream == null) {
        throw new IllegalStateException("ENOENT: no such file or directory, open '" + resource + "'");
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
