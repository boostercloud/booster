package com.boostercloud.framework.provider.azure.webpubsub.rest;

import com.boostercloud.framework.types.BoosterJson;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The bearer token {@code @azure/web-pubsub} signs for every request with an access key
 * ({@code webPubSubKeyCredentialPolicy}): a JWT {@code {"alg":"HS256","typ":"JWT"}} whose payload is
 * {@code {"iat": now, "exp": now + 3600, "aud": <request URL>}} (what
 * {@code jsonwebtoken.sign({}, key, { audience, expiresIn: '1h', algorithm: 'HS256' })} produces), signed with
 * HMAC-SHA256 using the UTF-8 bytes of the access key. The audience is the whole request URL, query string
 * ({@code api-version}) included, with the port removed ({@code const url = new URL(request.url); url.port = '';
 * url.toString()}), see {@link #audience(URI)}.
 */
public final class WebPubSubTokens {

  /** Token lifetime ({@code expiresIn: '1h'}). */
  public static final long EXPIRES_IN_SECONDS = 3600;

  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

  private WebPubSubTokens() {}

  /**
   * The {@code aud} claim of a request: {@code new URL(request.url)} with {@code port = ''}, serialised. Scheme and
   * host are lower-cased (WHATWG URL serialisation); the path and the query are kept as sent.
   *
   * @param requestUrl the request URL, query string included
   * @return the audience
   */
  public static String audience(URI requestUrl) {
    String path = requestUrl.getRawPath() == null || requestUrl.getRawPath().isEmpty() ? "/" : requestUrl.getRawPath();
    String query = requestUrl.getRawQuery() == null ? "" : "?" + requestUrl.getRawQuery();
    return requestUrl.getScheme().toLowerCase(Locale.ROOT) + "://" + requestUrl.getHost().toLowerCase(Locale.ROOT)
        + path + query;
  }

  /**
   * @param audience the {@code aud} claim, see {@link #audience(URI)}
   * @param accessKey the access key
   * @param issuedAtEpochSeconds {@code iat}
   * @return the compact JWT
   */
  public static String accessToken(String audience, String accessKey, long issuedAtEpochSeconds) {
    Map<String, Object> header = new LinkedHashMap<>();
    header.put("alg", "HS256");
    header.put("typ", "JWT");
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("iat", issuedAtEpochSeconds);
    payload.put("exp", issuedAtEpochSeconds + EXPIRES_IN_SECONDS);
    payload.put("aud", audience);
    String signingInput = encode(BoosterJson.toJson(header)) + "." + encode(BoosterJson.toJson(payload));
    return signingInput + "." + URL_ENCODER.encodeToString(hmac(signingInput, accessKey));
  }

  private static String encode(String json) {
    return URL_ENCODER.encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }

  static byte[] hmac(String payload, String accessKey) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(accessKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Could not sign the Web PubSub token", e);
    }
  }
}
