package com.boostercloud.framework.core.services.tokenverifiers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import com.boostercloud.framework.types.concepts.DecodedToken;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** Port of {@code test/services/token-verifiers/jwks-uri-token-verifier.test.ts}. */
class JwksUriTokenVerifierTest {

  @Test
  void verifiesWithAKeyResolverBackedByTheJwksClient() {
    JwksClient client = mock(JwksClient.class);
    DecodedToken decoded = new DecodedToken(Map.of("kid", "k"), Map.of("sub", "s"));
    Map<String, Object> header = Map.of("kid", "k");
    try (MockedStatic<Utilities> utilities = mockStatic(Utilities.class)) {
      utilities.when(() -> Utilities.getJwksClient("https://example.com/jwks")).thenReturn(client);
      utilities.when(() -> Utilities.getKeyWithClient(client, header)).thenReturn("public-key");
      utilities.when(() -> Utilities.verifyJWT(eq("token"), eq("issuer"), any(KeyResolver.class))).thenAnswer(invocation -> {
        KeyResolver resolver = invocation.getArgument(2);
        assertThat(resolver.resolve(header)).isEqualTo("public-key");
        return decoded;
      });

      JwksUriTokenVerifier verifier = new JwksUriTokenVerifier("issuer", "https://example.com/jwks");
      assertThat(verifier.verify("token")).isSameAs(decoded);
      assertThat(verifier.verify("token")).isSameAs(decoded);

      utilities.verify(() -> Utilities.getJwksClient("https://example.com/jwks"));
      utilities.verify(() -> Utilities.getKeyWithClient(client, header), org.mockito.Mockito.times(2));
    }
  }

  @Test
  void exposesItsSettings() {
    JwksUriTokenVerifier verifier = new JwksUriTokenVerifier("issuer", "uri", "roles");

    assertThat(verifier.issuer()).isEqualTo("issuer");
    assertThat(verifier.jwksUri()).isEqualTo("uri");
    assertThat(verifier.rolesClaim()).isEqualTo("roles");
    assertThat(new JwksUriTokenVerifier("issuer", "uri").rolesClaim()).isEqualTo("custom:role");
  }
}
