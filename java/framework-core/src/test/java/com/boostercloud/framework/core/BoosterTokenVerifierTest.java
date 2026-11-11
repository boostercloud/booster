package com.boostercloud.framework.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.core.services.tokenverifiers.JwksUriTokenVerifier;
import com.boostercloud.framework.core.services.tokenverifiers.JwtTestKeys;
import com.boostercloud.framework.core.services.tokenverifiers.NotBeforeError;
import com.boostercloud.framework.core.services.tokenverifiers.TokenExpiredError;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.concepts.DecodedToken;
import com.boostercloud.framework.types.concepts.TokenVerifier;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.framework.types.errors.BoosterTokenExpiredError;
import com.boostercloud.framework.types.errors.BoosterTokenNotBeforeError;
import com.boostercloud.framework.types.errors.NotAuthorizedError;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Port of {@code test/booster-token-verifier.test.ts} (with an in-process JWKS server instead of {@code mock-jwks}). */
class BoosterTokenVerifierTest {

  static final String ISSUER = "auth0";
  static final String USER_ID = "0c1f7a3e-5d0b-4d9e-9d8e-2a5a1b0f6c33";
  static final String EMAIL = "jane@example.com";
  static final String PHONE = "+34600000000";

  private JwtTestKeys keys;
  private String jwksUri;
  private BoosterConfig config;
  private BoosterTokenVerifier boosterTokenVerifier;

  @BeforeEach
  void setUp() {
    keys = new JwtTestKeys();
    jwksUri = keys.startJwksServer();
    config = new BoosterConfig("test");
    config.tokenVerifiers = List.of(new JwksUriTokenVerifier(ISSUER, jwksUri));
    boosterTokenVerifier = new BoosterTokenVerifier(config);
  }

  @AfterEach
  void tearDown() {
    keys.close();
  }

  private static Map<String, Object> claims(Object role) {
    Map<String, Object> claims = new LinkedHashMap<>();
    claims.put("sub", USER_ID);
    claims.put("iss", ISSUER);
    claims.put("custom:role", role);
    claims.put("email", EMAIL);
    claims.put("phoneNumber", PHONE);
    return claims;
  }

  @Test
  void acceptsCustomClaims() {
    Map<String, Object> claims = claims("User");
    claims.put("extraParam", "claims");
    claims.put("anotherParam", 111);

    UserEnvelope user = boosterTokenVerifier.verify(keys.token(claims));

    assertThat(user.claims()).containsAllEntriesOf(claims);
    assertThat(user.header()).containsEntry("alg", "RS256");
    assertThat(user.roles()).containsExactly("User");
    assertThat(user.id()).isEqualTo(USER_ID);
    assertThat(user.username()).isEqualTo(EMAIL);
  }

  @Test
  void acceptsABearerTokenWithASingleRole() {
    UserEnvelope user = boosterTokenVerifier.verify("Bearer " + keys.token(claims("User")));

    assertThat(user.roles()).containsExactly("User");
  }

  @Test
  void anEmptyRoleIsNoRole() {
    assertThat(boosterTokenVerifier.verify(keys.token(claims(""))).roles()).isEmpty();
  }

  @Test
  void acceptsAListOfRoles() {
    assertThat(boosterTokenVerifier.verify(keys.token(claims(List.of("User", "Other")))).roles())
        .containsExactly("User", "Other");
  }

  @Test
  void rejectsRolesThatAreNotStrings() {
    assertThatThrownBy(() -> boosterTokenVerifier.verify(keys.token(claims(123))))
        .isInstanceOf(NotAuthorizedError.class)
        .hasMessageContaining("Error: Invalid role format 123. Valid format are Array<string> or string");
    assertThatThrownBy(() -> boosterTokenVerifier.verify(keys.token(claims(List.of("a", "b", 123)))))
        .isInstanceOf(NotAuthorizedError.class)
        .hasMessageContaining("Error: Invalid role format 123. Valid format are Array<string> or string");
  }

  @Test
  void rejectsAnotherIssuer() {
    Map<String, Object> claims = claims("User");
    claims.put("iss", "firebase");

    assertThatThrownBy(() -> boosterTokenVerifier.verify(keys.token(claims)))
        .isInstanceOf(NotAuthorizedError.class)
        .hasMessage("JsonWebTokenError: jwt issuer invalid. expected: auth0");
  }

  @Test
  void rejectsExpiredTokens() {
    Map<String, Object> claims = claims("User");
    claims.put("exp", 0);

    assertThatThrownBy(() -> boosterTokenVerifier.verify(keys.token(claims)))
        .isInstanceOf(BoosterTokenExpiredError.class)
        .hasMessage("TokenExpiredError: jwt expired");
  }

  @Test
  void rejectsTokensNotActiveYet() {
    Map<String, Object> claims = claims("User");
    claims.put("nbf", 4_102_444_800L);

    assertThatThrownBy(() -> boosterTokenVerifier.verify(keys.token(claims)))
        .isInstanceOf(BoosterTokenNotBeforeError.class)
        .hasMessage("NotBeforeError: jwt not active");
  }

  @Test
  void supportsVerifiersWithExtraChecks() {
    config.tokenVerifiers = List.of(new JwksUriTokenVerifier(ISSUER, jwksUri) {
      @Override
      public DecodedToken verify(String token) {
        DecodedToken decoded = super.verify(token);
        if (!"Admin".equals(decoded.payload().get("custom:role"))) {
          throw new NotAuthorizedError("Unauthorized");
        }
        return decoded;
      }
    });

    assertThatThrownBy(() -> boosterTokenVerifier.verify(keys.token(claims("User"))))
        .isInstanceOf(NotAuthorizedError.class)
        .hasMessageContaining("Unauthorized");
    assertThat(boosterTokenVerifier.verify(keys.token(claims("Admin"))).roles()).containsExactly("Admin");
  }

  @Test
  void supportsVerifiersCheckingTheHeader() {
    config.tokenVerifiers = List.of(new JwksUriTokenVerifier(ISSUER, jwksUri) {
      @Override
      public DecodedToken verify(String token) {
        DecodedToken decoded = super.verify(token);
        if (!"RS512".equals(decoded.header().get("alg"))) {
          throw new IllegalStateException("Invalid token encoding");
        }
        return decoded;
      }
    });

    assertThatThrownBy(() -> boosterTokenVerifier.verify(keys.token(claims("User"))))
        .hasMessage("Error: Invalid token encoding");
  }

  @Test
  void theFirstSuccessfulVerifierInConfigurationOrderWins() {
    TokenVerifier slowAdmin = verifier(() -> {
      sleep(100);
      return UserEnvelope.builder().username("first").build();
    });
    TokenVerifier fast = verifier(() -> UserEnvelope.builder().username("second").build());
    config.tokenVerifiers = List.of(verifier(() -> {
      throw new IllegalStateException("nope");
    }), slowAdmin, fast);

    assertThat(boosterTokenVerifier.verify("token").username()).isEqualTo("first");
  }

  @Test
  void expiredWinsOverNotBeforeWhichWinsOverOtherReasons() {
    TokenVerifier other = verifier(() -> {
      throw new IllegalStateException("other");
    });
    TokenVerifier notBefore = verifier(() -> {
      throw new NotBeforeError("jwt not active", Instant.EPOCH);
    });
    TokenVerifier expired = verifier(() -> {
      throw new TokenExpiredError("jwt expired", Instant.EPOCH);
    });

    config.tokenVerifiers = List.of(other, notBefore, expired, expired);
    assertThatThrownBy(() -> boosterTokenVerifier.verify("token"))
        .isInstanceOf(BoosterTokenExpiredError.class)
        .hasMessage("TokenExpiredError: jwt expired\nTokenExpiredError: jwt expired");
    config.tokenVerifiers = List.of(other, notBefore);
    assertThatThrownBy(() -> boosterTokenVerifier.verify("token"))
        .isInstanceOf(BoosterTokenNotBeforeError.class)
        .hasMessage("NotBeforeError: jwt not active");
    config.tokenVerifiers = List.of(other, new JwksUriTokenVerifier(ISSUER, jwksUri));
    String tokenSignedByAnotherKey = new JwtTestKeys().token(claims("User"));
    assertThatThrownBy(() -> boosterTokenVerifier.verify(tokenSignedByAnotherKey))
        .isInstanceOf(NotAuthorizedError.class)
        .hasMessage("Error: other\nJsonWebTokenError: invalid signature");
  }

  @Test
  void rejectsEverythingWithoutVerifiers() {
    config.tokenVerifiers = List.of();

    assertThatThrownBy(() -> boosterTokenVerifier.verify("token")).isInstanceOf(NotAuthorizedError.class).hasMessage("");
  }

  private interface UserSupplier {
    UserEnvelope get();
  }

  private static TokenVerifier verifier(UserSupplier supplier) {
    return new TokenVerifier() {
      @Override
      public DecodedToken verify(String token) {
        return new DecodedToken(Map.of(), Map.of());
      }

      @Override
      public UserEnvelope toUserEnvelope(DecodedToken decodedToken) {
        return supplier.get();
      }
    };
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
