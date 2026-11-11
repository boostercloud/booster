package com.boostercloud.framework.core.services.tokenverifiers;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Public keys from PEM text, the formats Node's {@code crypto.createPublicKey} accepts: {@code PUBLIC KEY} (SPKI, RSA
 * or EC), {@code RSA PUBLIC KEY} (PKCS#1), {@code CERTIFICATE} (X.509) and RSA private keys ({@code PRIVATE KEY},
 * {@code RSA PRIVATE KEY}), whose public half is used. JDK only.
 */
public final class PemKeys {

  private static final Pattern PEM = Pattern.compile(
      "-----BEGIN ([A-Z0-9 ]+)-----\\s*([A-Za-z0-9+/=\\s]+?)\\s*-----END \\1-----");

  private PemKeys() {}

  /**
   * @param pem PEM text
   * @return the public key, or empty when the text is not a supported key (Node then treats it as a secret)
   */
  public static Optional<PublicKey> parsePublicKey(String pem) {
    if (pem == null) {
      return Optional.empty();
    }
    Matcher matcher = PEM.matcher(pem);
    if (!matcher.find()) {
      return Optional.empty();
    }
    String type = matcher.group(1);
    byte[] der;
    try {
      der = Base64.getMimeDecoder().decode(matcher.group(2));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
    try {
      return Optional.ofNullable(switch (type) {
        case "PUBLIC KEY" -> spki(der);
        case "RSA PUBLIC KEY" -> pkcs1Public(der);
        case "CERTIFICATE" -> CertificateFactory.getInstance("X.509")
            .generateCertificate(new ByteArrayInputStream(der)).getPublicKey();
        case "PRIVATE KEY" -> pkcs8Private(der);
        case "RSA PRIVATE KEY" -> pkcs1Private(der);
        default -> null;
      });
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /**
   * @param publicKey a public key
   * @return its SPKI PEM text ({@code -----BEGIN PUBLIC KEY-----}, 64-character lines)
   */
  public static String toPem(PublicKey publicKey) {
    String body = Base64.getMimeEncoder(64, "\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
        .encodeToString(publicKey.getEncoded());
    return "-----BEGIN PUBLIC KEY-----\n" + body + "\n-----END PUBLIC KEY-----\n";
  }

  private static PublicKey spki(byte[] der) throws GeneralSecurityException {
    X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
    for (String algorithm : List.of("RSA", "EC", "Ed25519", "Ed448")) {
      try {
        return KeyFactory.getInstance(algorithm).generatePublic(spec);
      } catch (GeneralSecurityException e) {
        // try the next algorithm
      }
    }
    throw new GeneralSecurityException("Unsupported public key");
  }

  private static PublicKey pkcs1Public(byte[] der) throws GeneralSecurityException {
    List<BigInteger> integers = sequenceOfIntegers(der);
    if (integers.size() < 2) {
      throw new GeneralSecurityException("Invalid PKCS#1 public key");
    }
    return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(integers.get(0), integers.get(1)));
  }

  private static PublicKey pkcs1Private(byte[] der) throws GeneralSecurityException {
    List<BigInteger> integers = sequenceOfIntegers(der);
    if (integers.size() < 3) {
      throw new GeneralSecurityException("Invalid PKCS#1 private key");
    }
    return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(integers.get(1), integers.get(2)));
  }

  private static PublicKey pkcs8Private(byte[] der) throws GeneralSecurityException {
    if (KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der)) instanceof RSAPrivateCrtKey key) {
      return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(key.getModulus(), key.getPublicExponent()));
    }
    throw new GeneralSecurityException("Unsupported private key");
  }

  /** Reads {@code SEQUENCE { INTEGER, INTEGER, ... }} (DER), stopping at the first non-integer element. */
  private static List<BigInteger> sequenceOfIntegers(byte[] der) throws GeneralSecurityException {
    int[] position = {0};
    if (der.length == 0 || der[position[0]++] != 0x30) {
      throw new GeneralSecurityException("Not a DER sequence");
    }
    int end = readLength(der, position) + position[0];
    List<BigInteger> integers = new ArrayList<>();
    while (position[0] < end && position[0] < der.length && der[position[0]] == 0x02) {
      position[0]++;
      int length = readLength(der, position);
      if (position[0] + length > der.length) {
        throw new GeneralSecurityException("Truncated DER integer");
      }
      byte[] value = new byte[length];
      System.arraycopy(der, position[0], value, 0, length);
      integers.add(new BigInteger(value));
      position[0] += length;
    }
    return integers;
  }

  private static int readLength(byte[] der, int[] position) throws GeneralSecurityException {
    if (position[0] >= der.length) {
      throw new GeneralSecurityException("Truncated DER length");
    }
    int first = der[position[0]++] & 0xff;
    if (first < 0x80) {
      return first;
    }
    int bytes = first & 0x7f;
    if (bytes == 0 || bytes > 4 || position[0] + bytes > der.length) {
      throw new GeneralSecurityException("Unsupported DER length");
    }
    int length = 0;
    for (int i = 0; i < bytes; i++) {
      length = (length << 8) | (der[position[0]++] & 0xff);
    }
    return length;
  }
}
