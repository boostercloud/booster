package com.boostercloud.rocket.fileuploads.azure.storage;

import com.boostercloud.framework.provider.azure.http.UriEncoding;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * User delegation SAS tokens for a blob, the part of {@code @azure/storage-blob} the rocket uses
 * ({@code generateBlobSASQueryParameters(values, userDelegationKey, accountName)} with
 * {@code version: '2018-11-09'}), following the public "Create a user delegation SAS" specification of the Blob
 * service for that version:
 *
 * <pre>
 * StringToSign = signedPermissions \n signedStart \n signedExpiry \n /blob/&lt;account&gt;/&lt;container&gt;/&lt;blob&gt; \n
 *                signedKeyObjectId \n signedKeyTenantId \n signedKeyStart \n signedKeyExpiry \n signedKeyService \n
 *                signedKeyVersion \n signedIP \n signedProtocol \n signedVersion \n signedResource \n
 *                signedSnapshotTime \n rscc \n rscd \n rsce \n rscl \n rsct
 * sig = base64(HMAC-SHA256(base64decode(key.Value), UTF-8(StringToSign)))
 * </pre>
 *
 * <p>Dates are ISO 8601 truncated to seconds ({@code 2026-11-10T10:00:00Z}, the SDK's {@code truncatedISO8061Date});
 * the query string has the SDK's parameter order ({@code sv, spr, st, se, skoid, sktid, skt, ske, sks, skv, sr, sp,
 * sig}) with every value {@code encodeURIComponent}-encoded and empty values left out.
 */
public final class BlobSas {

  /** The SAS version TypeScript pins ({@code version: '2018-11-09'}). */
  public static final String SAS_VERSION = "2018-11-09";
  /** {@code SASProtocol.HttpsAndHttp}. */
  public static final String HTTPS_AND_HTTP = "https,http";
  /** The signed resource of a blob ({@code sr=b}). */
  public static final String BLOB_RESOURCE = "b";
  /** The canonical order of blob permissions ({@code BlobSASPermissions.toString()}). */
  private static final String BLOB_PERMISSIONS = "racwdxtmeiy";

  private BlobSas() {}

  /**
   * {@code BlobSASPermissions.parse(permissions).toString()}.
   *
   * @param permissions permission letters ({@code r}, {@code w}...)
   * @return the letters in canonical order, without duplicates
   * @throws IllegalArgumentException {@code Invalid permission: <char>} (the SDK's {@code RangeError})
   */
  public static String blobPermissions(String permissions) {
    boolean[] present = new boolean[BLOB_PERMISSIONS.length()];
    for (int i = 0; i < permissions.length(); i++) {
      char c = permissions.charAt(i);
      int index = BLOB_PERMISSIONS.indexOf(c);
      if (index < 0) {
        throw new IllegalArgumentException("Invalid permission: " + c);
      }
      present[index] = true;
    }
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < present.length; i++) {
      if (present[i]) {
        result.append(BLOB_PERMISSIONS.charAt(i));
      }
    }
    return result.toString();
  }

  /**
   * {@code truncatedISO8061Date(date, false)}.
   *
   * @param instant a time
   * @return {@code yyyy-MM-ddTHH:mm:ssZ}
   */
  public static String truncatedIso(Instant instant) {
    return instant.truncatedTo(ChronoUnit.SECONDS).toString();
  }

  /**
   * {@code getCanonicalName(accountName, containerName, blobName)}.
   *
   * @param accountName the storage account
   * @param containerName the container
   * @param blobName the blob, or {@code null} for the container
   * @return {@code /blob/<account>/<container>[/<blob>]}
   */
  public static String canonicalName(String accountName, String containerName, String blobName) {
    String name = "/blob/" + accountName + "/" + containerName;
    return blobName == null || blobName.isEmpty() ? name : name + "/" + blobName;
  }

  /**
   * The string to sign of a 2018-11-09 user delegation SAS for a blob (no IP range, snapshot or response headers).
   *
   * @param permissions the canonical permissions
   * @param startsOn the start
   * @param expiresOn the expiry
   * @param accountName the storage account
   * @param containerName the container
   * @param blobName the blob
   * @param key the user delegation key
   * @param protocol the allowed protocols ({@value #HTTPS_AND_HTTP})
   * @param version the SAS version ({@value #SAS_VERSION})
   * @return the lines joined with {@code \n}
   */
  public static String stringToSign(String permissions, Instant startsOn, Instant expiresOn, String accountName,
      String containerName, String blobName, UserDelegationKey key, String protocol, String version) {
    return String.join("\n",
        permissions,
        startsOn == null ? "" : truncatedIso(startsOn),
        expiresOn == null ? "" : truncatedIso(expiresOn),
        canonicalName(accountName, containerName, blobName),
        nullToEmpty(key.signedObjectId()),
        nullToEmpty(key.signedTenantId()),
        key.signedStartsOn() == null ? "" : truncatedIso(key.signedStartsOn()),
        key.signedExpiresOn() == null ? "" : truncatedIso(key.signedExpiresOn()),
        nullToEmpty(key.signedService()),
        nullToEmpty(key.signedVersion()),
        "",
        nullToEmpty(protocol),
        version,
        BLOB_RESOURCE,
        "",
        "",
        "",
        "",
        "",
        "");
  }

  /**
   * {@code UserDelegationKeyCredential.computeHMACSHA256(stringToSign)}.
   *
   * @param stringToSign the string to sign
   * @param base64Key the user delegation key value
   * @return the base64 signature
   */
  public static String sign(String stringToSign, String base64Key) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(Base64.getDecoder().decode(base64Key), "HmacSHA256"));
      return Base64.getEncoder().encodeToString(mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HMAC-SHA256 is not available", e);
    }
  }

  /**
   * {@code generateBlobSASQueryParameters(values, userDelegationKey, accountName).toString()} for a blob, version
   * {@value #SAS_VERSION}, protocol {@value #HTTPS_AND_HTTP}.
   *
   * @param containerName the container
   * @param blobName the blob
   * @param permissions the permission letters
   * @param startsOn the start
   * @param expiresOn the expiry
   * @param key the user delegation key
   * @param accountName the storage account
   * @return the SAS query string (without {@code ?})
   */
  public static String generateBlobSasQueryParameters(String containerName, String blobName, String permissions,
      Instant startsOn, Instant expiresOn, UserDelegationKey key, String accountName) {
    String verifiedPermissions = blobPermissions(permissions);
    String signature = sign(stringToSign(verifiedPermissions, startsOn, expiresOn, accountName, containerName,
        blobName, key, HTTPS_AND_HTTP, SAS_VERSION), key.value());
    List<String> queries = new ArrayList<>();
    append(queries, "sv", SAS_VERSION);
    append(queries, "spr", HTTPS_AND_HTTP);
    append(queries, "st", startsOn == null ? null : truncatedIso(startsOn));
    append(queries, "se", expiresOn == null ? null : truncatedIso(expiresOn));
    append(queries, "skoid", key.signedObjectId());
    append(queries, "sktid", key.signedTenantId());
    append(queries, "skt", key.signedStartsOn() == null ? null : truncatedIso(key.signedStartsOn()));
    append(queries, "ske", key.signedExpiresOn() == null ? null : truncatedIso(key.signedExpiresOn()));
    append(queries, "sks", key.signedService());
    append(queries, "skv", key.signedVersion());
    append(queries, "sr", BLOB_RESOURCE);
    append(queries, "sp", verifiedPermissions);
    append(queries, "sig", signature);
    return String.join("&", queries);
  }

  /** {@code tryAppendQueryParameter}: skips empty values. */
  private static void append(List<String> queries, String key, String value) {
    if (value == null || value.isEmpty()) {
      return;
    }
    queries.add(UriEncoding.encodeURIComponent(key) + "=" + UriEncoding.encodeURIComponent(value));
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
