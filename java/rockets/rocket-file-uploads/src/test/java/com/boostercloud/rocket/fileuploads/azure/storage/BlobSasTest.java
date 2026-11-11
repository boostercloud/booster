package com.boostercloud.rocket.fileuploads.azure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * The user delegation SAS of the Blob service (version 2018-11-09). The signature is a known answer computed with
 * OpenSSL ({@code openssl dgst -sha256 -mac HMAC}) from the string-to-sign of the public specification.
 */
class BlobSasTest {

  static final String KEY = "Ym9vc3Rlci1yb2NrZXQtZmlsZXMtdGVzdC1rZXktMzI=";
  static final Instant START = Instant.parse("2026-11-10T10:00:00.123Z");
  static final Instant EXPIRY = Instant.parse("2026-11-10T10:01:26.523Z");
  static final UserDelegationKey DELEGATION_KEY = new UserDelegationKey("11111111-2222-3333-4444-555555555555",
      "66666666-7777-8888-9999-000000000000", Instant.parse("2026-11-10T10:00:00Z"),
      Instant.parse("2026-11-10T10:01:26Z"), "b", "2025-11-05", KEY);

  @Test
  void the_string_to_sign_has_the_twenty_fields_of_the_2018_11_09_specification() {
    String stringToSign = BlobSas.stringToSign("w", START, EXPIRY, "clientst", "rocketfiles", "client1/myfile.txt",
        DELEGATION_KEY, "https,http", "2018-11-09");

    assertThat(stringToSign).isEqualTo("w\n2026-11-10T10:00:00Z\n2026-11-10T10:01:26Z\n"
        + "/blob/clientst/rocketfiles/client1/myfile.txt\n11111111-2222-3333-4444-555555555555\n"
        + "66666666-7777-8888-9999-000000000000\n2026-11-10T10:00:00Z\n2026-11-10T10:01:26Z\nb\n2025-11-05\n\n"
        + "https,http\n2018-11-09\nb\n\n\n\n\n\n");
    assertThat(stringToSign.split("\n", -1)).hasSize(20);
  }

  @Test
  void the_signature_is_the_hmac_sha256_of_the_decoded_key() {
    String stringToSign = BlobSas.stringToSign("w", START, EXPIRY, "clientst", "rocketfiles", "client1/myfile.txt",
        DELEGATION_KEY, "https,http", "2018-11-09");

    assertThat(BlobSas.sign(stringToSign, KEY)).isEqualTo("mmblP+o2ZUvTYimtN1QpvRIvMVMdQMUhmu/0jKzj8yg=");
  }

  @Test
  void the_query_string_has_the_sdk_order_and_encoding() {
    String query = BlobSas.generateBlobSasQueryParameters("rocketfiles", "client1/myfile.txt", "w", START, EXPIRY,
        DELEGATION_KEY, "clientst");

    assertThat(query).isEqualTo("sv=2018-11-09&spr=https%2Chttp&st=2026-11-10T10%3A00%3A00Z"
        + "&se=2026-11-10T10%3A01%3A26Z&skoid=11111111-2222-3333-4444-555555555555"
        + "&sktid=66666666-7777-8888-9999-000000000000&skt=2026-11-10T10%3A00%3A00Z&ske=2026-11-10T10%3A01%3A26Z"
        + "&sks=b&skv=2025-11-05&sr=b&sp=w&sig=mmblP%2Bo2ZUvTYimtN1QpvRIvMVMdQMUhmu%2F0jKzj8yg%3D");
  }

  @Test
  void permissions_are_validated_and_ordered_like_blob_sas_permissions() {
    assertThat(BlobSas.blobPermissions("wr")).isEqualTo("rw");
    assertThat(BlobSas.blobPermissions("rr")).isEqualTo("r");
    assertThat(BlobSas.blobPermissions("ydwcar")).isEqualTo("racwdy");
    assertThatThrownBy(() -> BlobSas.blobPermissions("rz")).hasMessage("Invalid permission: z");
  }

  @Test
  void helpers() {
    assertThat(BlobSas.truncatedIso(Instant.parse("2026-11-10T10:00:00.999Z"))).isEqualTo("2026-11-10T10:00:00Z");
    assertThat(BlobSas.canonicalName("acct", "c", null)).isEqualTo("/blob/acct/c");
    assertThat(BlobSas.canonicalName("acct", "c", "d/f")).isEqualTo("/blob/acct/c/d/f");
  }
}
