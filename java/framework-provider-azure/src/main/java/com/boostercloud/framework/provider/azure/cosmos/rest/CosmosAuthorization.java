package com.boostercloud.framework.provider.azure.cosmos.rest;

import com.boostercloud.framework.provider.azure.http.UriEncoding;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Master-key authorization of the Cosmos DB REST API ("Access control on Cosmos DB resources"):
 *
 * <pre>
 * stringToSign = verb.toLowerCase() + "\n" + resourceType.toLowerCase() + "\n" + resourceLink + "\n"
 *              + date.toLowerCase() + "\n" + "" + "\n"
 * signature    = base64(HMAC-SHA256(base64decode(masterKey), utf8(stringToSign)))
 * header       = encodeURIComponent("type=master&amp;ver=1.0&amp;sig=" + signature)
 * </pre>
 *
 * where {@code date} is the {@code x-ms-date} header (RFC 1123, two-digit day, {@code GMT}) and {@code resourceLink}
 * the case-sensitive path of the resource ({@code dbs/<db>/colls/<coll>} for feed operations,
 * {@code dbs/<db>/colls/<coll>/docs/<id>} for documents).
 */
public final class CosmosAuthorization {

  /** {@code x-ms-date} format: {@code Tue, 01 Nov 1994 08:12:31 GMT} (what JavaScript's {@code toUTCString} gives). */
  public static final DateTimeFormatter X_MS_DATE =
      DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).withZone(ZoneOffset.UTC);

  private CosmosAuthorization() {}

  /**
   * @param instant a time
   * @return the {@code x-ms-date} header value
   */
  public static String formatDate(Instant instant) {
    return X_MS_DATE.format(instant);
  }

  /**
   * @param verb the HTTP verb
   * @param resourceType {@code docs}, {@code colls}, {@code pkranges}, {@code dbs}...
   * @param resourceLink the resource link
   * @param date the {@code x-ms-date} header value
   * @return the string to sign
   */
  public static String stringToSign(String verb, String resourceType, String resourceLink, String date) {
    return verb.toLowerCase(Locale.ROOT) + "\n" + resourceType.toLowerCase(Locale.ROOT) + "\n" + resourceLink + "\n"
        + date.toLowerCase(Locale.ROOT) + "\n" + "" + "\n";
  }

  /**
   * @param verb the HTTP verb
   * @param resourceType the resource type
   * @param resourceLink the resource link
   * @param date the {@code x-ms-date} header value
   * @param masterKey the base64 master key
   * @return the {@code authorization} header value (URL-encoded)
   */
  public static String authorizationHeader(
      String verb, String resourceType, String resourceLink, String date, String masterKey) {
    String signature = sign(stringToSign(verb, resourceType, resourceLink, date), masterKey);
    return UriEncoding.encodeURIComponent("type=master&ver=1.0&sig=" + signature);
  }

  /**
   * @param payload the string to sign
   * @param masterKey the base64 master key
   * @return the base64 HMAC-SHA256 signature
   */
  public static String sign(String payload, String masterKey) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(Base64.getDecoder().decode(masterKey), "HmacSHA256"));
      return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Could not sign the Cosmos DB request", e);
    }
  }
}
