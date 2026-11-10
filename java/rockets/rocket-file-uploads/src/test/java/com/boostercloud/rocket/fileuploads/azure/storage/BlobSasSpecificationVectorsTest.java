package com.boostercloud.rocket.fileuploads.azure.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.provider.azure.infrastructure.azure.HttpTransport;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.rocket.fileuploads.azure.handlers.BlobService;
import com.boostercloud.rocket.fileuploads.types.RocketFilesUserConfiguration;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * User delegation SAS tokens compared with vectors computed by an independent implementation:
 * {@code src/test/resources/typescript/generate.mjs} writes the 2018-11-09 string-to-sign of the public specification
 * ("Create a user delegation SAS") in JavaScript, signs it with Node's {@code crypto} and encodes the query with
 * {@code encodeURIComponent} in the order of {@code SASQueryParameters.toString()} of {@code @azure/storage-blob}.
 * The vectors cover millisecond truncation (86 and 87 second windows), month and year boundaries, both permissions in
 * canonical order, unencoded blob names with spaces, accents, {@code +}, {@code %}, sub-delimiters and astral
 * characters, and keys of several versions.
 */
class BlobSasSpecificationVectorsTest {

  record Vector(String account, String container, String blob, String permissions, Instant start, Instant expiry,
      UserDelegationKey key, String stringToSign, String signature, String query) {}

  @SuppressWarnings("unchecked")
  static Stream<Vector> vectors() throws IOException {
    return Files.readAllLines(Path.of("src/test/resources/typescript/sas-vectors.jsonl"), StandardCharsets.UTF_8)
        .stream().filter(line -> !line.isBlank()).map(line -> {
          Map<String, Object> raw = (Map<String, Object>) BoosterJson.parse(line);
          Map<String, Object> key = (Map<String, Object>) raw.get("key");
          return new Vector((String) raw.get("account"), (String) raw.get("container"), (String) raw.get("blob"),
              (String) raw.get("permissions"), Instant.parse((String) raw.get("start")),
              Instant.parse((String) raw.get("expiry")),
              new UserDelegationKey((String) key.get("signedObjectId"), (String) key.get("signedTenantId"),
                  Instant.parse((String) key.get("signedStartsOn")), Instant.parse((String) key.get("signedExpiresOn")),
                  (String) key.get("signedService"), (String) key.get("signedVersion"), (String) key.get("value")),
              (String) raw.get("stringToSign"), (String) raw.get("signature"), (String) raw.get("query"));
        });
  }

  @ParameterizedTest
  @MethodSource("vectors")
  void the_sas_is_the_one_of_the_specification(Vector vector) {
    String permissions = BlobSas.blobPermissions(vector.permissions());

    assertThat(BlobSas.stringToSign(permissions, vector.start(), vector.expiry(), vector.account(), vector.container(),
        vector.blob(), vector.key(), BlobSas.HTTPS_AND_HTTP, BlobSas.SAS_VERSION)).isEqualTo(vector.stringToSign());
    assertThat(BlobSas.sign(vector.stringToSign(), vector.key().value())).isEqualTo(vector.signature());
    assertThat(BlobSas.generateBlobSasQueryParameters(vector.container(), vector.blob(), vector.permissions(),
        vector.start(), vector.expiry(), vector.key(), vector.account())).isEqualTo(vector.query());
  }

  /**
   * The whole {@code getBlobSasUrl} path against the vectors: the service clock gives the start, the expiry is the
   * start plus 86 400 <b>milliseconds</b> (the vectors' expiries), the key comes from the service, and the URL keeps
   * the blob path unencoded ({@code <service>/<container>/<directory>/<file>?<sas>}).
   */
  @ParameterizedTest
  @MethodSource("vectors")
  void blob_service_urls_carry_the_specification_sas(Vector vector) {
    assertThat(vector.expiry().toEpochMilli() - vector.start().toEpochMilli()).isEqualTo(86_400);
    String keyResponse = "<?xml version=\"1.0\" encoding=\"utf-8\"?><UserDelegationKey><SignedOid>"
        + vector.key().signedObjectId() + "</SignedOid><SignedTid>" + vector.key().signedTenantId()
        + "</SignedTid><SignedStart>" + BlobSas.truncatedIso(vector.key().signedStartsOn()) + "</SignedStart>"
        + "<SignedExpiry>" + BlobSas.truncatedIso(vector.key().signedExpiresOn()) + "</SignedExpiry><SignedService>"
        + vector.key().signedService() + "</SignedService><SignedVersion>" + vector.key().signedVersion()
        + "</SignedVersion><Value>" + vector.key().value() + "</Value></UserDelegationKey>";
    HttpTransport transport = request -> new HttpTransport.Response(200, Map.of(),
        keyResponse.getBytes(StandardCharsets.UTF_8));
    BlobStorageContext context = new BlobStorageContext(transport, scope -> "token",
        account -> URI.create("https://" + account.toLowerCase() + ".blob.core.windows.net"),
        Clock.fixed(vector.start(), ZoneOffset.UTC), BlobStorageContext.DEFAULT_API_VERSION);
    BoosterConfig config = new BoosterConfig("dev");
    config.appName = "app";
    int slash = vector.blob().lastIndexOf('/');
    String directory = vector.blob().substring(0, slash);
    String fileName = vector.blob().substring(slash + 1);

    String url = new BlobService(config, new RocketFilesUserConfiguration(vector.account(), vector.container(),
        List.of("**")), context).getBlobSasUrl(vector.container(), directory, fileName, vector.permissions(),
        BlobService.DEFAULT_EXPIRES_ON_SECONDS);

    assertThat(url).isEqualTo("https://" + vector.account().toLowerCase() + ".blob.core.windows.net/"
        + vector.container() + "/" + vector.blob() + "?" + vector.query());
  }
}
