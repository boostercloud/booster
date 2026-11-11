package com.boostercloud.framework.core.services.tokenverifiers;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** PEM formats accepted by {@link PemKeys} (Node {@code crypto.createPublicKey}). */
class PemKeysTest {

  private final KeyPair keyPair = JwtTestKeys.generate();

  @Test
  void parsesSpkiPublicKeysAndRoundTrips() {
    String pem = PemKeys.toPem(keyPair.getPublic());

    assertThat(pem).startsWith("-----BEGIN PUBLIC KEY-----\n").endsWith("\n-----END PUBLIC KEY-----\n");
    assertThat(PemKeys.parsePublicKey(pem)).contains(keyPair.getPublic());
  }

  @Test
  void parsesPkcs8PrivateKeysAsTheirPublicHalf() {
    String pem = "-----BEGIN PRIVATE KEY-----\n"
        + Base64.getMimeEncoder().encodeToString(keyPair.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----";

    assertThat(PemKeys.parsePublicKey(pem)).contains(keyPair.getPublic());
  }

  @Test
  void parsesPkcs1PublicKeys() {
    RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
    byte[] der = sequence(integer(publicKey.getModulus()), integer(publicKey.getPublicExponent()));
    String pem = "-----BEGIN RSA PUBLIC KEY-----\n" + Base64.getMimeEncoder().encodeToString(der) + "\n-----END RSA PUBLIC KEY-----";

    assertThat(PemKeys.parsePublicKey(pem)).contains(keyPair.getPublic());
  }

  @Test
  void rejectsEverythingElse() {
    assertThat(PemKeys.parsePublicKey(null)).isEmpty();
    assertThat(PemKeys.parsePublicKey("a shared secret")).isEmpty();
    assertThat(PemKeys.parsePublicKey("-----BEGIN PUBLIC KEY-----\nAAAA\n-----END PUBLIC KEY-----")).isEmpty();
  }

  private static byte[] integer(BigInteger value) {
    return tlv(0x02, value.toByteArray());
  }

  private static byte[] sequence(byte[]... elements) {
    java.io.ByteArrayOutputStream content = new java.io.ByteArrayOutputStream();
    for (byte[] element : elements) {
      content.writeBytes(element);
    }
    return tlv(0x30, content.toByteArray());
  }

  private static byte[] tlv(int tag, byte[] value) {
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    out.write(tag);
    if (value.length < 0x80) {
      out.write(value.length);
    } else if (value.length < 0x100) {
      out.write(0x81);
      out.write(value.length);
    } else {
      out.write(0x82);
      out.write(value.length >> 8);
      out.write(value.length & 0xff);
    }
    out.writeBytes(value);
    return out.toByteArray();
  }
}
