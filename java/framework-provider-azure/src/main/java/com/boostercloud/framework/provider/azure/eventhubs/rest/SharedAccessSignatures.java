package com.boostercloud.framework.provider.azure.eventhubs.rest;

import com.boostercloud.framework.provider.azure.http.UriEncoding;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Shared access signature tokens of Service Bus / Event Hubs ("Authenticate access to Event Hubs resources using
 * shared access signatures"):
 *
 * <pre>
 * stringToSign = encodeURIComponent(resourceUri) + "\n" + expiry
 * signature    = base64(HMAC-SHA256(utf8(key), utf8(stringToSign)))
 * token        = "SharedAccessSignature sr=" + encodeURIComponent(resourceUri) + "&amp;sig=" + encodeURIComponent(signature)
 *              + "&amp;se=" + expiry + "&amp;skn=" + keyName
 * </pre>
 *
 * The key is used as UTF-8 text, not base64-decoded; {@code expiry} is in epoch seconds.
 */
public final class SharedAccessSignatures {

  /** Validity of generated tokens (what {@code @azure/core-amqp} uses). */
  public static final long TOKEN_VALIDITY_SECONDS = 3600;

  private SharedAccessSignatures() {}

  /**
   * @param resourceUri the resource URI
   * @param keyName the policy name
   * @param key the policy key
   * @param expiryEpochSeconds expiry
   * @return the {@code Authorization} header value
   */
  public static String token(String resourceUri, String keyName, String key, long expiryEpochSeconds) {
    String audience = UriEncoding.encodeURIComponent(resourceUri);
    String signature = sign(audience + "\n" + expiryEpochSeconds, key);
    return "SharedAccessSignature sr=" + audience + "&sig=" + UriEncoding.encodeURIComponent(signature) + "&se="
        + expiryEpochSeconds + "&skn=" + keyName;
  }

  /**
   * @param payload the string to sign
   * @param key the key
   * @return the base64 HMAC-SHA256 signature
   */
  public static String sign(String payload, String key) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Could not sign the Event Hubs request", e);
    }
  }
}
