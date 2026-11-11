package com.boostercloud.framework.core.services.tokenverifiers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

import com.boostercloud.framework.types.concepts.DecodedToken;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** Port of {@code test/services/token-verifiers/public-key-token-verifier.test.ts}. */
class PublicKeyTokenVerifierTest {

  @Test
  void verifiesWithTheResolvedKey() {
    DecodedToken decoded = new DecodedToken(Map.of(), Map.of());
    try (MockedStatic<Utilities> utilities = mockStatic(Utilities.class)) {
      utilities.when(() -> Utilities.verifyJWT("token", "https://example.com/jwks", (Object) "public key")).thenReturn(decoded);

      PublicKeyTokenVerifier verifier =
          new PublicKeyTokenVerifier("https://example.com/jwks", CompletableFuture.completedFuture("public key"));

      assertThat(verifier.verify("token")).isSameAs(decoded);
    }
  }

  @Test
  void verifiesRealTokensAndFailsWhenTheKeyCannotBeResolved() {
    JwtTestKeys keys = new JwtTestKeys();
    String token = keys.token(Map.of("sub", "s", "iss", "issuer"));

    assertThat(new PublicKeyTokenVerifier("issuer", keys.publicKeyPem(), null).verify(token).payload())
        .containsEntry("sub", "s");
    IllegalStateException failure = new IllegalStateException("no key");
    assertThatThrownBy(() -> new PublicKeyTokenVerifier("issuer", CompletableFuture.failedFuture(failure)).verify(token))
        .isSameAs(failure);
  }
}
