package com.boostercloud.integration.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.application.tester.TokenHelper;
import com.boostercloud.application.tester.TokenOptions;
import com.boostercloud.framework.core.services.tokenverifiers.PublicKeyTokenVerifier;
import com.boostercloud.framework.types.concepts.DecodedToken;
import com.boostercloud.integration.harness.E2eSettings;
import com.boostercloud.integration.helper.FileHelper;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * The token verifiers of {@code src/config/config.ts}: the bundled public key matches the application tester's
 * signing key, the plain verifier accepts its tokens and the custom one always ends with {@code Unauthorized}. Also
 * the injectable {@code test} command of {@code src/config/injectable.ts}.
 */
class TokenVerifiersTest {

  private static final String PUBLIC_KEY =
      FileHelper.readFileContent(E2eSettings.moduleDirectory().resolve(Config.PUBLIC_KEY));
  private final TokenHelper tokens = new TokenHelper();

  @Test
  void theAssetsKeyIsThePublicKeyOfTheApplicationTester() {
    assertThat(PUBLIC_KEY.strip()).isEqualTo(TokenHelper.publicKeyPem().strip());
    assertThat(PUBLIC_KEY).as("byte-identical to the TypeScript asset (no trailing newline)")
        .startsWith("-----BEGIN PUBLIC KEY-----\n").endsWith("-----END PUBLIC KEY-----");
  }

  @Test
  void thePublicKeyVerifierAcceptsTheTestTokensWithTheirRole() {
    PublicKeyTokenVerifier verifier =
        new PublicKeyTokenVerifier(Config.TOKEN_ISSUER, PUBLIC_KEY, Config.ROLES_CLAIM);

    DecodedToken decoded = verifier.verify(tokens.forUser("admin@example.com", "Admin"));

    assertThat(verifier.toUserEnvelope(decoded).roles()).containsExactly("Admin");
    assertThat(verifier.toUserEnvelope(decoded).username()).isEqualTo("admin@example.com");
  }

  @Test
  void theCustomVerifierRejectsValidTokensWithUnauthorized() {
    CustomPublicKeyTokenVerifier verifier =
        new CustomPublicKeyTokenVerifier(Config.TOKEN_ISSUER, PUBLIC_KEY, Config.ROLES_CLAIM);

    assertThatThrownBy(() -> verifier.verify(tokens.forUser("admin@example.com", "Admin")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Unauthorized");
  }

  @Test
  void theCustomVerifierRejectsInvalidTokensWithTheVerificationError() {
    CustomPublicKeyTokenVerifier verifier =
        new CustomPublicKeyTokenVerifier(Config.TOKEN_ISSUER, PUBLIC_KEY, Config.ROLES_CLAIM);
    String expired = tokens.forUser("admin@example.com", "Admin", TokenOptions.builder().expiresIn(0).build());

    assertThatThrownBy(() -> verifier.verify(expired)).hasMessage("jwt expired");
  }

  @Test
  void theInjectableTestCommandSaysHello() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    PrintStream original = System.out;
    System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
    try {
      Injectables.TEST_COMMAND.run(null, java.util.List.of());
    } finally {
      System.setOut(original);
    }

    assertThat(Injectables.TEST_COMMAND.name()).isEqualTo("test");
    assertThat(output.toString(StandardCharsets.UTF_8)).isEqualTo("Hello, world" + System.lineSeparator());
  }
}
