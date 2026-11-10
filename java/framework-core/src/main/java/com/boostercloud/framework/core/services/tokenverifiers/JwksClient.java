package com.boostercloud.framework.core.services.tokenverifiers;

import com.boostercloud.framework.types.BoosterJson;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * The subset of the {@code jwks-rsa} 3 client used by Booster, with the JDK's HTTP client: fetches a JWKS document and
 * returns its signing keys ({@code use} {@code sig} or absent; RSA and EC keys), optionally caching each key by
 * {@code kid} for {@code cacheMaxAge} milliseconds (at most {@code cacheMaxEntries} keys, least recently used
 * evicted). Failures are not cached.
 *
 * <p>Errors: {@link JwksError} {@code The JWKS endpoint did not contain any keys}, {@code The JWKS endpoint did not
 * contain any signing keys}, {@code Http Error <status>} (or the {@code message} of the error body);
 * {@link SigningKeyNotFoundError} {@code Unable to find a signing key that matches '<kid>'} and
 * {@code No KID specified and JWKS endpoint returned more than 1 key}.
 */
public class JwksClient {

  /**
   * The client options ({@code jwksRSA({ jwksUri, cache, cacheMaxAge })}; the other values are the library defaults).
   *
   * @param jwksUri the JWKS document URL
   * @param cache whether keys are cached
   * @param cacheMaxAge how long a key is cached, in milliseconds
   * @param cacheMaxEntries how many keys are cached
   * @param timeout the HTTP timeout, in milliseconds
   */
  public record Options(String jwksUri, boolean cache, long cacheMaxAge, int cacheMaxEntries, long timeout) {

    /** {@code jwks-rsa}'s default {@code cacheMaxEntries}. */
    public static final int DEFAULT_CACHE_MAX_ENTRIES = 5;

    /** {@code jwks-rsa}'s default {@code timeout} (30 s). */
    public static final long DEFAULT_TIMEOUT = 30_000;

    /**
     * @param jwksUri the JWKS document URL
     * @param cache whether keys are cached
     * @param cacheMaxAge how long a key is cached, in milliseconds
     */
    public Options(String jwksUri, boolean cache, long cacheMaxAge) {
      this(jwksUri, cache, cacheMaxAge, DEFAULT_CACHE_MAX_ENTRIES, DEFAULT_TIMEOUT);
    }

    /**
     * @param jwksUri the JWKS document URL
     * @param cache whether keys are cached
     * @param cacheMaxAge how long a key is cached, in milliseconds
     * @param cacheMaxEntries how many keys are cached
     * @param timeout the HTTP timeout, in milliseconds
     */
    public Options {
      Objects.requireNonNull(jwksUri, "jwksUri");
    }
  }

  private record CachedKey(SigningKey key, long expiresAt) {}

  private final Options options;
  private final LongSupplier clock;
  private final Map<String, CachedKey> cache;
  private volatile HttpClient httpClient;

  /**
   * @param options the options
   */
  public JwksClient(Options options) {
    this(options, System::currentTimeMillis);
  }

  /**
   * @param options the options
   * @param clock milliseconds source of the cache (tests)
   */
  public JwksClient(Options options, LongSupplier clock) {
    this.options = Objects.requireNonNull(options, "options");
    this.clock = clock;
    this.cache = new LinkedHashMap<>(16, 0.75f, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<String, CachedKey> eldest) {
        return size() > options.cacheMaxEntries();
      }
    };
  }

  /**
   * @return the options
   */
  public Options options() {
    return options;
  }

  /**
   * {@code getSigningKey(kid)}.
   *
   * @param kid the key id ({@code null} accepts the only key of the document)
   * @return the key
   */
  public SigningKey getSigningKey(String kid) {
    String cacheKey = String.valueOf(kid);
    if (options.cache()) {
      synchronized (cache) {
        CachedKey cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt() > clock.getAsLong()) {
          return cached.key();
        }
      }
    }
    List<SigningKey> keys = getSigningKeys();
    boolean kidDefined = kid != null;
    if (!kidDefined && keys.size() > 1) {
      throw new SigningKeyNotFoundError("No KID specified and JWKS endpoint returned more than 1 key");
    }
    SigningKey key = keys.stream().filter(candidate -> !kidDefined || kid.equals(candidate.kid())).findFirst()
        .orElseThrow(() -> new SigningKeyNotFoundError("Unable to find a signing key that matches '" + kid + "'"));
    if (options.cache()) {
      synchronized (cache) {
        cache.put(cacheKey, new CachedKey(key, clock.getAsLong() + options.cacheMaxAge()));
      }
    }
    return key;
  }

  /**
   * {@code getSigningKeys()}: fetches the document and returns its usable signing keys, in document order.
   *
   * @return the keys
   */
  public List<SigningKey> getSigningKeys() {
    List<Map<String, Object>> keys = getKeys();
    if (keys.isEmpty()) {
      throw new JwksError("The JWKS endpoint did not contain any keys");
    }
    List<SigningKey> signingKeys = new ArrayList<>();
    for (Map<String, Object> jwk : keys) {
      Object use = jwk.get("use");
      if (use != null && !"sig".equals(use)) {
        continue;
      }
      PublicKey publicKey = toPublicKey(jwk);
      if (publicKey != null) {
        signingKeys.add(new SigningKey(nonEmptyString(jwk.get("kid")), nonEmptyString(jwk.get("alg")), publicKey));
      }
    }
    if (signingKeys.isEmpty()) {
      throw new JwksError("The JWKS endpoint did not contain any signing keys");
    }
    return signingKeys;
  }

  /**
   * Fetches the {@code keys} of the JWKS document.
   *
   * @return the JWKs
   */
  @SuppressWarnings("unchecked")
  protected List<Map<String, Object>> getKeys() {
    Object document = BoosterJson.parse(fetchJwks());
    if (document instanceof Map<?, ?> map && map.get("keys") instanceof List<?> keys) {
      List<Map<String, Object>> result = new ArrayList<>();
      for (Object key : keys) {
        if (key instanceof Map<?, ?> jwk) {
          result.add((Map<String, Object>) jwk);
        }
      }
      return result;
    }
    return List.of();
  }

  /**
   * GETs the JWKS document (overridable in tests).
   *
   * @return the response body
   * @throws JwksError when the request fails or the status is not 2xx
   */
  protected String fetchJwks() {
    HttpRequest request = HttpRequest.newBuilder(URI.create(options.jwksUri()))
        .timeout(Duration.ofMillis(options.timeout()))
        .header("Accept", "application/json")
        .GET()
        .build();
    HttpResponse<String> response;
    try {
      response = client().send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new JwksError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new JwksError("Interrupted while fetching " + options.jwksUri(), e);
    }
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new JwksError(errorMessage(response));
    }
    return response.body();
  }

  private HttpClient client() {
    HttpClient current = httpClient;
    if (current == null) {
      synchronized (this) {
        if (httpClient == null) {
          httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(options.timeout()))
              .followRedirects(HttpClient.Redirect.NORMAL).build();
        }
        current = httpClient;
      }
    }
    return current;
  }

  private static String errorMessage(HttpResponse<String> response) {
    try {
      if (BoosterJson.parse(response.body()) instanceof Map<?, ?> body && body.get("message") instanceof String message
          && !message.isEmpty()) {
        return message;
      }
    } catch (RuntimeException e) {
      // not JSON
    }
    return "Http Error " + response.statusCode();
  }

  private static PublicKey toPublicKey(Map<String, Object> jwk) {
    try {
      return switch (String.valueOf(jwk.get("kty"))) {
        case "RSA" -> KeyFactory.getInstance("RSA").generatePublic(
            new RSAPublicKeySpec(unsigned(jwk.get("n")), unsigned(jwk.get("e"))));
        case "EC" -> ecKey(jwk);
        default -> null;
      };
    } catch (GeneralSecurityException | RuntimeException e) {
      return null;
    }
  }

  private static PublicKey ecKey(Map<String, Object> jwk) throws GeneralSecurityException {
    String curve = switch (String.valueOf(jwk.get("crv"))) {
      case "P-256" -> "secp256r1";
      case "P-384" -> "secp384r1";
      case "P-521" -> "secp521r1";
      default -> throw new GeneralSecurityException("Unsupported curve");
    };
    AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
    parameters.init(new ECGenParameterSpec(curve));
    ECParameterSpec spec = parameters.getParameterSpec(ECParameterSpec.class);
    ECPoint point = new ECPoint(unsigned(jwk.get("x")), unsigned(jwk.get("y")));
    return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, spec));
  }

  private static BigInteger unsigned(Object base64Url) {
    if (!(base64Url instanceof String text)) {
      throw new IllegalArgumentException("Missing JWK parameter");
    }
    return new BigInteger(1, Base64.getUrlDecoder().decode(text));
  }

  private static String nonEmptyString(Object value) {
    return value instanceof String text && !text.isEmpty() ? text : null;
  }
}
