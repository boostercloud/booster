package com.boostercloud.framework.core.services.tokenverifiers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.core.BoosterTestSupport;
import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.concepts.DecodedToken;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Port of {@code test/services/token-verifiers/utilities.test.ts}, plus conformance tests of the {@code jsonwebtoken}
 * checks reproduced by {@code verifyJWT} (spec 05 §3.5.2).
 */
class UtilitiesTest extends BoosterTestSupport {

  private JwtTestKeys keys;

  @BeforeEach
  void setUp() {
    keys = new JwtTestKeys();
    Dates.useClock(Clock.fixed(Instant.ofEpochSecond(1_700_000_000L), ZoneOffset.UTC));
  }

  private String token(Map<String, Object> payload) {
    return keys.token(payload);
  }

  private static Map<String, Object> payload(Object... keyValues) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("sub", "user");
    payload.put("iss", "issuer");
    for (int i = 0; i < keyValues.length; i += 2) {
      payload.put((String) keyValues[i], keyValues[i + 1]);
    }
    return payload;
  }

  @Test
  void getJwksClientCachesKeysFor15Minutes() {
    JwksClient client = Utilities.getJwksClient("https://example.com/jwks");

    assertThat(client.options()).isEqualTo(new JwksClient.Options("https://example.com/jwks", true, 900_000));
  }

  @Test
  void getKeyWithClientFailsWithoutKid() {
    assertThatThrownBy(() -> Utilities.getKeyWithClient(mock(JwksClient.class), Map.of("alg", "RS256")))
        .isInstanceOf(IllegalStateException.class).hasMessage("JWT kid not found");
  }

  @Test
  void getKeyWithClientPassesTheClientErrorThrough() {
    JwksClient client = mock(JwksClient.class);
    RuntimeException failure = new JwksError("Error getting public key");
    when(client.getSigningKey("kid")).thenThrow(failure);

    assertThatThrownBy(() -> Utilities.getKeyWithClient(client, Map.of("kid", "kid"))).isSameAs(failure);
  }

  @Test
  void getKeyWithClientReturnsThePublicKey() {
    JwksClient client = mock(JwksClient.class);
    when(client.getSigningKey("kid")).thenReturn(new SigningKey("kid", null, keys.keyPair().getPublic()));

    assertThat(Utilities.getKeyWithClient(client, Map.of("kid", "kid"))).isEqualTo(keys.publicKeyPem());
  }

  @Test
  void verifyJWTRemovesTheBearerPrefixAndReturnsHeaderAndPayload() {
    Map<String, Object> payload = payload("custom", 1);

    DecodedToken decoded = Utilities.verifyJWT("Bearer " + token(payload), "issuer", keys.publicKeyPem());

    assertThat(decoded.payload()).isEqualTo(payload);
    assertThat(decoded.header()).containsEntry("alg", "RS256").containsEntry("kid", JwtTestKeys.KID);
  }

  @Test
  void verifyJWTAcceptsKeyResolversAndPublicKeys() {
    String token = token(payload());

    assertThat(Utilities.verifyJWT(token, "issuer", (KeyResolver) header -> keys.publicKeyPem()).payload()).containsEntry("sub", "user");
    assertThat(Utilities.verifyJWT(token, "issuer", keys.keyPair().getPublic()).payload()).containsEntry("sub", "user");
  }

  @Test
  void verifyJWTReportsMalformedTokens() {
    assertThatThrownBy(() -> Utilities.verifyJWT("", "issuer", keys.publicKeyPem()))
        .isExactlyInstanceOf(JsonWebTokenError.class).hasMessage("jwt must be provided");
    assertThatThrownBy(() -> Utilities.verifyJWT("a.b", "issuer", keys.publicKeyPem())).hasMessage("jwt malformed");
    assertThatThrownBy(() -> Utilities.verifyJWT("a+.b.c", "issuer", keys.publicKeyPem())).hasMessage("invalid token");
    assertThatThrownBy(() -> Utilities.verifyJWT(JwtTestKeys.base64Url("[1]") + ".e30.c2ln", "issuer", keys.publicKeyPem()))
        .hasMessage("invalid token");
  }

  @Test
  void verifyJWTReportsKeyProblems() {
    String token = token(payload());
    String unsigned = token.substring(0, token.lastIndexOf('.') + 1);

    assertThatThrownBy(() -> Utilities.verifyJWT(token, "issuer", (KeyResolver) header -> {
      throw new IllegalStateException("JWT kid not found");
    })).isExactlyInstanceOf(JsonWebTokenError.class).hasMessage("error in secret or public key callback: JWT kid not found");
    assertThatThrownBy(() -> Utilities.verifyJWT(unsigned, "issuer", keys.publicKeyPem())).hasMessage("jwt signature is required");
    assertThatThrownBy(() -> Utilities.verifyJWT(token, "issuer", "")).hasMessage("secret or public key must be provided");
    assertThatThrownBy(() -> Utilities.verifyJWT(token, "issuer", 42)).hasMessage("secretOrPublicKey is not valid key material");
    assertThatThrownBy(() -> Utilities.verifyJWT(token, "issuer", "a shared secret"))
        .hasMessage("secretOrPublicKey must be an asymmetric key when using RS256");
  }

  @Test
  void verifyJWTOnlyAcceptsRS256WithRsaKeys() throws Exception {
    Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
    String hs256 = JwtTestKeys.base64Url(com.boostercloud.framework.types.BoosterJson.toJson(header)) + "."
        + JwtTestKeys.base64Url("{\"iss\":\"issuer\"}") + ".c2ln";
    KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(256);
    KeyPair ec = generator.generateKeyPair();

    assertThatThrownBy(() -> Utilities.verifyJWT(hs256, "issuer", keys.publicKeyPem())).hasMessage("invalid algorithm");
    assertThatThrownBy(() -> Utilities.verifyJWT(token(payload()), "issuer", ec.getPublic()))
        .isExactlyInstanceOf(IllegalStateException.class)
        .hasMessage("\"alg\" parameter for \"ec\" key type must be one of: ES256, ES384, ES512.");
  }

  @Test
  void verifyJWTChecksTheSignature() {
    String token = token(payload());
    String tampered = token.substring(0, token.indexOf('.') + 1) + JwtTestKeys.base64Url("{\"iss\":\"issuer\",\"admin\":true}")
        + token.substring(token.lastIndexOf('.'));

    assertThatThrownBy(() -> Utilities.verifyJWT(tampered, "issuer", keys.publicKeyPem())).hasMessage("invalid signature");
    assertThatThrownBy(() -> Utilities.verifyJWT(token, "issuer", new JwtTestKeys().publicKeyPem())).hasMessage("invalid signature");
  }

  @Test
  void verifyJWTChecksNotBeforeExpirationAndIssuerInThatOrder() {
    long now = 1_700_000_000L;

    assertThatThrownBy(() -> Utilities.verifyJWT(token(payload("nbf", "soon")), "issuer", keys.publicKeyPem()))
        .hasMessage("invalid nbf value");
    assertThatThrownBy(() -> Utilities.verifyJWT(token(payload("nbf", now + 1, "exp", 0)), "issuer", keys.publicKeyPem()))
        .isInstanceOf(NotBeforeError.class).hasMessage("jwt not active");
    assertThat(Utilities.verifyJWT(token(payload("nbf", now)), "issuer", keys.publicKeyPem())).isNotNull();
    assertThatThrownBy(() -> Utilities.verifyJWT(token(payload("exp", "later")), "issuer", keys.publicKeyPem()))
        .hasMessage("invalid exp value");
    assertThatThrownBy(() -> Utilities.verifyJWT(token(payload("exp", now, "iss", "other")), "issuer", keys.publicKeyPem()))
        .isInstanceOfSatisfying(TokenExpiredError.class,
            error -> assertThat(error.expiredAt()).isEqualTo(Instant.ofEpochSecond(now)))
        .hasMessage("jwt expired");
    assertThat(Utilities.verifyJWT(token(payload("exp", now + 1)), "issuer", keys.publicKeyPem())).isNotNull();
    assertThatThrownBy(() -> Utilities.verifyJWT(token(payload("iss", "other")), "issuer", keys.publicKeyPem()))
        .isExactlyInstanceOf(JsonWebTokenError.class).hasMessage("jwt issuer invalid. expected: issuer");
  }
}
