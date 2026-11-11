package com.boostercloud.application.tester;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads PEM RSA keys with the JDK only. The JDK {@link KeyFactory} understands PKCS#8 and X.509 encodings; the
 * Booster test key is PKCS#1 ({@code BEGIN RSA PRIVATE KEY}), so this class decodes that ASN.1 structure itself.
 */
final class PemKeys {

  private static final Pattern PEM =
      Pattern.compile("-----BEGIN ([A-Z0-9 ]+)-----([A-Za-z0-9+/=\\s]+)-----END \\1-----");

  private PemKeys() {}

  /**
   * @param pem a PEM {@code RSA PRIVATE KEY} (PKCS#1) or {@code PRIVATE KEY} (PKCS#8)
   * @return the RSA private key
   * @throws IllegalArgumentException when the text is not such a key
   */
  static PrivateKey privateKey(String pem) {
    Block block = block(pem);
    KeySpec spec = switch (block.label()) {
      case "RSA PRIVATE KEY" -> pkcs1PrivateKey(block.der());
      case "PRIVATE KEY" -> new PKCS8EncodedKeySpec(block.der());
      default -> throw new IllegalArgumentException("Unsupported private key PEM block: " + block.label());
    };
    try {
      return KeyFactory.getInstance("RSA").generatePrivate(spec);
    } catch (GeneralSecurityException e) {
      throw new IllegalArgumentException("Invalid RSA private key: " + e.getMessage(), e);
    }
  }

  /**
   * @param pem a PEM {@code PUBLIC KEY} (X.509 SubjectPublicKeyInfo) or {@code RSA PUBLIC KEY} (PKCS#1)
   * @return the RSA public key
   * @throws IllegalArgumentException when the text is not such a key
   */
  static PublicKey publicKey(String pem) {
    Block block = block(pem);
    KeySpec spec = switch (block.label()) {
      case "PUBLIC KEY" -> new X509EncodedKeySpec(block.der());
      case "RSA PUBLIC KEY" -> {
        List<BigInteger> integers = sequenceOfIntegers(block.der());
        if (integers.size() < 2) {
          throw new IllegalArgumentException("Invalid PKCS#1 RSA public key");
        }
        yield new RSAPublicKeySpec(integers.get(0), integers.get(1));
      }
      default -> throw new IllegalArgumentException("Unsupported public key PEM block: " + block.label());
    };
    try {
      return KeyFactory.getInstance("RSA").generatePublic(spec);
    } catch (GeneralSecurityException e) {
      throw new IllegalArgumentException("Invalid RSA public key: " + e.getMessage(), e);
    }
  }

  private static RSAPrivateCrtKeySpec pkcs1PrivateKey(byte[] der) {
    // RSAPrivateKey ::= SEQUENCE { version, modulus, publicExponent, privateExponent, prime1, prime2,
    //                              exponent1, exponent2, coefficient, otherPrimeInfos OPTIONAL }
    List<BigInteger> integers = sequenceOfIntegers(der);
    if (integers.size() < 9) {
      throw new IllegalArgumentException("Invalid PKCS#1 RSA private key");
    }
    return new RSAPrivateCrtKeySpec(
        integers.get(1), integers.get(2), integers.get(3), integers.get(4), integers.get(5), integers.get(6),
        integers.get(7), integers.get(8));
  }

  private record Block(String label, byte[] der) {}

  private static Block block(String pem) {
    if (pem == null) {
      throw new IllegalArgumentException("The key is missing");
    }
    Matcher matcher = PEM.matcher(pem);
    if (!matcher.find()) {
      throw new IllegalArgumentException("The key is not a PEM encoded key");
    }
    try {
      return new Block(matcher.group(1), Base64.getMimeDecoder().decode(matcher.group(2).strip()));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("The key is not a valid PEM encoded key: " + e.getMessage(), e);
    }
  }

  /** Reads the leading INTEGER elements of a DER SEQUENCE (stops at the first element that is not an INTEGER). */
  private static List<BigInteger> sequenceOfIntegers(byte[] der) {
    DerReader reader = new DerReader(der);
    DerReader sequence = reader.next(0x30);
    List<BigInteger> integers = new ArrayList<>();
    while (sequence.hasMore() && sequence.peekTag() == 0x02) {
      integers.add(new BigInteger(sequence.next(0x02).remaining()));
    }
    return integers;
  }

  /** Minimal DER reader (definite lengths only). */
  private static final class DerReader {
    private final byte[] bytes;
    private int position;
    private final int end;

    DerReader(byte[] bytes) {
      this(bytes, 0, bytes.length);
    }

    private DerReader(byte[] bytes, int start, int end) {
      this.bytes = bytes;
      this.position = start;
      this.end = end;
    }

    boolean hasMore() {
      return position < end;
    }

    int peekTag() {
      return bytes[position] & 0xFF;
    }

    DerReader next(int expectedTag) {
      if (!hasMore() || peekTag() != expectedTag) {
        throw new IllegalArgumentException("Invalid DER structure: expected tag " + expectedTag);
      }
      position++;
      int length = readLength();
      if (length < 0 || position + length > end) {
        throw new IllegalArgumentException("Invalid DER structure: bad length");
      }
      DerReader child = new DerReader(bytes, position, position + length);
      position += length;
      return child;
    }

    byte[] remaining() {
      byte[] copy = new byte[end - position];
      System.arraycopy(bytes, position, copy, 0, copy.length);
      position = end;
      return copy;
    }

    private int readLength() {
      if (!hasMore()) {
        throw new IllegalArgumentException("Invalid DER structure: truncated");
      }
      int first = bytes[position++] & 0xFF;
      if (first < 0x80) {
        return first;
      }
      int count = first & 0x7F;
      if (count == 0 || count > 4 || position + count > end) {
        throw new IllegalArgumentException("Invalid DER structure: unsupported length");
      }
      int length = 0;
      for (int i = 0; i < count; i++) {
        length = (length << 8) | (bytes[position++] & 0xFF);
      }
      return length;
    }
  }
}
