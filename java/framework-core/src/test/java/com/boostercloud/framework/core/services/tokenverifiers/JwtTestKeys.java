package com.boostercloud.framework.core.services.tokenverifiers;

import com.boostercloud.framework.types.BoosterJson;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test replacement of {@code mock-jwks}: an RSA key pair, an RS256 token signer and an in-process HTTP server serving
 * the JWKS document.
 */
public final class JwtTestKeys implements AutoCloseable {

  /** The key id of the served key. */
  public static final String KID = "test-key-1";

  private final KeyPair keyPair;
  private HttpServer server;
  private final AtomicInteger requests = new AtomicInteger();

  /** Generates a 2048-bit RSA key pair. */
  public JwtTestKeys() {
    this.keyPair = generate();
  }

  /**
   * @return a new RSA key pair
   */
  public static KeyPair generate() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * @return the key pair
   */
  public KeyPair keyPair() {
    return keyPair;
  }

  /**
   * @return the public key as PEM
   */
  public String publicKeyPem() {
    return PemKeys.toPem(keyPair.getPublic());
  }

  /**
   * @param payload the claims
   * @return an RS256 token with {@code kid} {@link #KID}
   */
  public String token(Map<String, Object> payload) {
    Map<String, Object> header = new LinkedHashMap<>();
    header.put("alg", "RS256");
    header.put("typ", "JWT");
    header.put("kid", KID);
    return sign(header, payload, keyPair.getPrivate());
  }

  /**
   * @param header the header
   * @param payload the claims
   * @param privateKey the signing key
   * @return the signed token
   */
  public static String sign(Map<String, Object> header, Map<String, Object> payload, PrivateKey privateKey) {
    String signingInput = base64Url(BoosterJson.toJson(header)) + "." + base64Url(BoosterJson.toJson(payload));
    try {
      Signature signature = Signature.getInstance("SHA256withRSA");
      signature.initSign(privateKey);
      signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
      return signingInput + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * @param text a text
   * @return its unpadded base64url encoding
   */
  public static String base64Url(String text) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * @return the JWKS document with the public key
   */
  public Map<String, Object> jwks() {
    RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
    Map<String, Object> key = new LinkedHashMap<>();
    key.put("kty", "RSA");
    key.put("kid", KID);
    key.put("use", "sig");
    key.put("alg", "RS256");
    key.put("n", unsigned(publicKey.getModulus().toByteArray()));
    key.put("e", unsigned(publicKey.getPublicExponent().toByteArray()));
    return Map.of("keys", List.of(key));
  }

  private static String unsigned(byte[] bytes) {
    int offset = bytes.length > 1 && bytes[0] == 0 ? 1 : 0;
    byte[] trimmed = java.util.Arrays.copyOfRange(bytes, offset, bytes.length);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(trimmed);
  }

  /**
   * Starts the JWKS server.
   *
   * @return the JWKS URL
   */
  public String startJwksServer() {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    server.createContext("/.well-known/jwks.json", exchange -> {
      requests.incrementAndGet();
      byte[] body = BoosterJson.toJson(jwks()).getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      try (OutputStream output = exchange.getResponseBody()) {
        output.write(body);
      }
    });
    server.start();
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/.well-known/jwks.json";
  }

  /**
   * @return how many JWKS requests the server received
   */
  public int requests() {
    return requests.get();
  }

  @Override
  public void close() {
    if (server != null) {
      server.stop(0);
    }
  }
}
