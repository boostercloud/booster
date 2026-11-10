package com.boostercloud.framework.provider.azure.webpubsub.rest;

import com.boostercloud.framework.types.BoosterJson;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The bearer token {@code @azure/web-pubsub} signs for every request with an access key
 * ({@code webPubSubKeyCredentialPolicy}): a JWT {@code {"alg":"HS256","typ":"JWT"}} whose payload is
 * {@code {"iat": now, "exp": now + 3600, "aud": <request URL without its query string>}} (what
 * {@code jsonwebtoken.sign({}, key, { audience, expiresIn: '1h', algorithm: 'HS256' })} produces), signed with
 * HMAC-SHA256 using the UTF-8 bytes of the access key.
 */
public final class WebPubSubTokens {

  /** Token lifetime ({@code expiresIn: '1h'}). */
  public static final long EXPIRES_IN_SECONDS = 3600;

  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

  private WebPubSubTokens() {}

  /**
   * @param audience the request URL without query string
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
