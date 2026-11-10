package com.boostercloud.framework.core.services.tokenverifiers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.types.BoosterJson;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The {@code jwks-rsa} subset ({@link JwksClient}). */
class JwksClientTest {

  private JwtTestKeys keys;

  @BeforeEach
  void setUp() {
    keys = new JwtTestKeys();
  }

  @AfterEach
  void tearDown() {
    keys.close();
  }

  private static JwksClient client(Object document, AtomicLong clock) {
    return new JwksClient(new JwksClient.Options("http://unused", true, 1000), clock::get) {
      @Override
      protected String fetchJwks() {
        return BoosterJson.toJson(document);
      }
    };
  }

  @Test
  void fetchesAndCachesTheKeyByKid() {
    String uri = keys.startJwksServer();
    AtomicLong clock = new AtomicLong(0);
    JwksClient client = new JwksClient(new JwksClient.Options(uri, true, 1000), clock::get);

    SigningKey key = client.getSigningKey(JwtTestKeys.KID);
    client.getSigningKey(JwtTestKeys.KID);

    assertThat(key.kid()).isEqualTo(JwtTestKeys.KID);
    assertThat(key.alg()).isEqualTo("RS256");
    assertThat(key.publicKey()).isEqualTo(keys.keyPair().getPublic());
    assertThat(key.getPublicKey()).isEqualTo(keys.publicKeyPem());
    assertThat(keys.requests()).isEqualTo(1);
    clock.set(1001);
    client.getSigningKey(JwtTestKeys.KID);
    assertThat(keys.requests()).isEqualTo(2);
  }

  @Test
  void withoutCacheEveryCallFetches() {
    String uri = keys.startJwksServer();
    JwksClient client = new JwksClient(new JwksClient.Options(uri, false, 1000));

    client.getSigningKey(JwtTestKeys.KID);
    client.getSigningKey(JwtTestKeys.KID);

    assertThat(keys.requests()).isEqualTo(2);
  }

  @Test
  void reportsUnknownKidsAndAmbiguousRequests() {
    Map<String, Object> key = keys.jwks();
    JwksClient client = client(key, new AtomicLong());

    assertThatThrownBy(() -> client.getSigningKey("other"))
        .isInstanceOf(SigningKeyNotFoundError.class).hasMessage("Unable to find a signing key that matches 'other'");
    assertThat(client.getSigningKey(null).kid()).isEqualTo(JwtTestKeys.KID);

    @SuppressWarnings("unchecked")
    Map<String, Object> jwk = ((List<Map<String, Object>>) key.get("keys")).getFirst();
    JwksClient twoKeys = client(Map.of("keys", List.of(jwk, jwk)), new AtomicLong());
    assertThatThrownBy(() -> twoKeys.getSigningKey(null))
        .isInstanceOf(SigningKeyNotFoundError.class).hasMessage("No KID specified and JWKS endpoint returned more than 1 key");
  }

  @Test
  void reportsDocumentsWithoutUsableKeys() {
    assertThatThrownBy(() -> client(Map.of("keys", List.of()), new AtomicLong()).getSigningKeys())
        .isInstanceOf(JwksError.class).hasMessage("The JWKS endpoint did not contain any keys");
    assertThatThrownBy(() -> client(Map.of("keys", List.of(Map.of("kty", "RSA", "use", "enc", "n", "AQAB", "e", "AQAB"),
        Map.of("kty", "oct", "k", "c2VjcmV0"))), new AtomicLong()).getSigningKeys())
        .isInstanceOf(JwksError.class).hasMessage("The JWKS endpoint did not contain any signing keys");
  }

  @Test
  void reportsHttpErrors() throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/jwks", exchange -> {
      byte[] body = "{\"message\":\"Not here\"}".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(404, body.length);
      try (OutputStream output = exchange.getResponseBody()) {
        output.write(body);
      }
    });
    server.createContext("/plain", exchange -> {
      exchange.sendResponseHeaders(500, -1);
      exchange.close();
    });
    server.start();
    try {
      String base = "http://127.0.0.1:" + server.getAddress().getPort();
      assertThatThrownBy(() -> new JwksClient(new JwksClient.Options(base + "/jwks", true, 1000)).getSigningKey("k"))
          .isInstanceOf(JwksError.class).hasMessage("Not here");
      assertThatThrownBy(() -> new JwksClient(new JwksClient.Options(base + "/plain", true, 1000)).getSigningKey("k"))
          .isInstanceOf(JwksError.class).hasMessage("Http Error 500");
    } finally {
      server.stop(0);
    }
  }
}
