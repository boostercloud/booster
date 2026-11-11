package com.boostercloud.application.tester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.concepts.DecodedToken;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TokenHelperTest {

  private static final long NOW = 1794268800L;

  /** Tokens computed with Node's crypto (RSA-SHA256 over the JSON.stringify output) and the same key. */
  private static final String GOLDEN_ADMIN_TOKEN =
      "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6ImJvb3N0ZXIifQ."
          + "eyJpZCI6ImFkbWluQGV4YW1wbGUuY29tIiwiZW1haWwiOiJhZG1pbkBleGFtcGxlLmNvbSIsImNhbkxvZ1NvbWV0aGluZyI6dHJ1ZSwi"
          + "Ym9vc3Rlcjpyb2xlIjoiQWRtaW4iLCJpYXQiOjE3OTQyNjg4MDAsImV4cCI6MTc5NDI3MjQwMCwiaXNzIjoiYm9vc3RlciIsInN1YiI6"
          + "ImFkbWluQGV4YW1wbGUuY29tIn0."
          + "veWO9jNY52VFydCq4kVAQHqit7NnAdiFuZOfb7Q8ydwIRYRLcI3h0OopbNm7r_ne4DcYzsERfW-yxzrMCfD0fO0ObDydefV-g8KxHhN5"
          + "VLUUfQ7gZY6wXWuUkh6ncUQHGCuO0T6AoC4MFpuozYw78UaOWeYH8rXNK61UIy_xD1NLbQddjYJJmHpa4MLIX2-cWowqAzw3BwUh_p7k"
          + "HTNGjfwxIfurUZ9EBVbZwE-RjHMLRxqTBZZxJBWlO0RrLoMauhahDtVbEElcGX16KiBhDtcKdqXYCbF2cAogV1dTMEQt2fO0JmS8wDUa"
          + "NQ3P7WrNVtqszZMPwUg4iuDza0Z2rQ";

  private static final String GOLDEN_PLAIN_TOKEN =
      "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6ImJvb3N0ZXIifQ."
          + "eyJpZCI6ImFAYi5jb20iLCJlbWFpbCI6ImFAYi5jb20iLCJpYXQiOjE3OTQyNjg4MDAsImlzcyI6ImJvb3N0ZXIiLCJzdWIiOiJhQGIu"
          + "Y29tIn0."
          + "PBhIN5Dh-cWiDcPfIykrfSN16uaxAsj1J-K63o5F-AgfE0kMLFOjgfn30B9x6F4G2SFVB-Y-VY6lHEzbC2kiVv1gLe05hGTzU7RpF-v7"
          + "GjJ0fTo0QIdxTZvc3wNb37Rcz_nIStq_wbopAHkGnhHxfIn0ucRd7OvFOmAruQpFruS_ICXMMxMdHkSIjHqh7uPCj-lqEENRlhW4nR_D"
          + "UBaSbVxh_AWBC9yHXEAfb4h7xfLL33RNMB6fjeoknXhk9jVxzzYuZXJe_pSM-PE112dP4_wkqUk0B0QVDEhtMMyBtzuqWqh-j0NFLVre"
          + "ahTpis6tI0qmb-5C4NN9PFqCqyMv5Q";

  private final TokenHelper helper = fixedClockHelper();

  private static TokenHelper fixedClockHelper() {
    String pem = TokenHelper.publicKeyPem(); // touch the resources first
    assertThat(pem).startsWith("-----BEGIN PUBLIC KEY-----");
    return new TokenHelper(PemKeys.privateKey(bundledPrivateKeyPem()), () -> NOW);
  }

  private static String bundledPrivateKeyPem() {
    try (var stream = TokenHelper.class.getResourceAsStream(TokenHelper.PRIVATE_KEY_RESOURCE)) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String headerJson(String token) {
    return new String(Base64.getUrlDecoder().decode(token.split("\\.")[0]), StandardCharsets.UTF_8);
  }

  private static String payloadJson(String token) {
    return new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
  }

  private static boolean verifies(String token) throws Exception {
    String[] parts = token.split("\\.");
    Signature signature = Signature.getInstance("SHA256withRSA");
    signature.initVerify(TokenHelper.publicKey());
    signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
    return signature.verify(Base64.getUrlDecoder().decode(parts[2]));
  }

  @Nested
  @DisplayName("the produced token")
  class ProducedToken {

    @Test
    @DisplayName("is byte-identical to the jsonwebtoken output for the documented example")
    void matchesTheGoldenToken() {
      String token = helper.forUser(
          "admin@example.com", "Admin",
          TokenOptions.builder().expiresIn(3600).customClaim("canLogSomething", true).build());

      assertThat(token).isEqualTo(GOLDEN_ADMIN_TOKEN);
    }

    @Test
    @DisplayName("without role nor options only carries id, email, iat, iss and sub")
    void minimalToken() {
      assertThat(helper.forUser("a@b.com")).isEqualTo(GOLDEN_PLAIN_TOKEN);
      assertThat(payloadJson(helper.forUser("a@b.com")))
          .isEqualTo("{\"id\":\"a@b.com\",\"email\":\"a@b.com\",\"iat\":1794268800,\"iss\":\"booster\",\"sub\":\"a@b.com\"}");
    }

    @Test
    @DisplayName("has the RS256 header with kid booster")
    void header() {
      assertThat(headerJson(helper.forUser("a@b.com", "Admin")))
          .isEqualTo("{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"booster\"}");
    }

    @Test
    @DisplayName("is signed with the bundled key and verifies with the bundled public key")
    void signature() throws Exception {
      assertThat(verifies(new TokenHelper().forUser("user@example.com", "UserWithEmail"))).isTrue();
      assertThat(verifies(helper.forUser("user@example.com"))).isTrue();
    }

    @Test
    @DisplayName("uses the current time for iat by default")
    void currentTime() {
      long before = System.currentTimeMillis() / 1000;
      Map<String, Object> payload = TokenHelper.decode(new TokenHelper().forUser("a@b.com")).payload();
      long after = System.currentTimeMillis() / 1000;
      assertThat(((Number) payload.get("iat")).longValue()).isBetween(before, after);
      assertThat(payload).doesNotContainKeys("exp", "nbf");
    }
  }

  @Nested
  @DisplayName("roles")
  class Roles {

    @Test
    @DisplayName("adds the role as a single string in booster:role")
    void role() {
      assertThat(TokenHelper.decode(helper.forUser("a@b.com", "UserWithEmail")).payload())
          .containsEntry("booster:role", "UserWithEmail");
    }

    @Test
    @DisplayName("adds no role claim for a null or empty role")
    void noRole() {
      assertThat(TokenHelper.decode(helper.forUser("a@b.com", null)).payload()).doesNotContainKey("booster:role");
      assertThat(TokenHelper.decode(helper.forUser("a@b.com", "")).payload()).doesNotContainKey("booster:role");
    }

    @Test
    @DisplayName("overrides a custom booster:role claim, keeping its position")
    void roleOverridesCustomClaim() {
      Map<String, Object> claims = new LinkedHashMap<>();
      claims.put("booster:role", "Custom");
      claims.put("other", 1);
      String token = helper.forUser("a@b.com", "Admin", TokenOptions.builder().customClaims(claims).build());

      assertThat(payloadJson(token)).isEqualTo(
          "{\"id\":\"a@b.com\",\"email\":\"a@b.com\",\"booster:role\":\"Admin\",\"other\":1,\"iat\":1794268800,"
              + "\"iss\":\"booster\",\"sub\":\"a@b.com\"}");
    }
  }

  @Nested
  @DisplayName("expiresIn and notBefore")
  class Times {

    @Test
    @DisplayName("expiresIn 0 is honoured and produces an expired token (exp == iat)")
    void expiresInZero() {
      Map<String, Object> payload =
          TokenHelper.decode(helper.forUser("a@b.com", "UserWithEmail", TokenOptions.builder().expiresIn(0).build()))
              .payload();
      assertThat(payload).containsEntry("iat", (int) NOW).containsEntry("exp", (int) NOW);
    }

    @Test
    @DisplayName("expiresIn is relative to iat")
    void expiresIn() {
      Map<String, Object> payload =
          TokenHelper.decode(helper.forUser("a@b.com", null, TokenOptions.builder().expiresIn(3600).build())).payload();
      assertThat(((Number) payload.get("exp")).longValue()).isEqualTo(NOW + 3600);
    }

    @Test
    @DisplayName("notBefore 0 is ignored")
    void notBeforeZero() {
      assertThat(TokenHelper.decode(helper.forUser("a@b.com", null, TokenOptions.builder().notBefore(0).build()))
              .payload())
          .doesNotContainKey("nbf");
    }

    @Test
    @DisplayName("notBefore is relative to iat and placed before exp")
    void notBefore() {
      long notBefore = NOW + 999999;
      String token =
          helper.forUser("a@b.com", "UserWithEmail", TokenOptions.builder().expiresIn(0).notBefore(notBefore).build());
      assertThat(payloadJson(token)).isEqualTo(
          "{\"id\":\"a@b.com\",\"email\":\"a@b.com\",\"booster:role\":\"UserWithEmail\",\"iat\":1794268800,"
              + "\"nbf\":" + (NOW + notBefore) + ",\"exp\":1794268800,\"iss\":\"booster\",\"sub\":\"a@b.com\"}");
    }

    @Test
    @DisplayName("a non-zero custom iat is the base of exp and nbf and keeps its position")
    void customIat() {
      Map<String, Object> claims = new LinkedHashMap<>();
      claims.put("iat", 100);
      String token = helper.forUser(
          "a@b.com", null, TokenOptions.builder().customClaims(claims).expiresIn(10).notBefore(5).build());
      assertThat(payloadJson(token)).isEqualTo(
          "{\"id\":\"a@b.com\",\"email\":\"a@b.com\",\"iat\":100,\"nbf\":105,\"exp\":110,\"iss\":\"booster\","
              + "\"sub\":\"a@b.com\"}");
    }

    @Test
    @DisplayName("a custom iat of 0 is replaced by the current time")
    void zeroIat() {
      String token = helper.forUser("a@b.com", null, TokenOptions.builder().customClaim("iat", 0).build());
      assertThat(TokenHelper.decode(token).payload()).containsEntry("iat", (int) NOW);
    }

    @Test
    @DisplayName("a custom exp is kept when expiresIn is not given")
    void customExp() {
      String token = helper.forUser("a@b.com", null, TokenOptions.builder().customClaim("exp", 5).build());
      assertThat(TokenHelper.decode(token).payload()).containsEntry("exp", 5);
    }
  }

  @Nested
  @DisplayName("custom claims")
  class CustomClaims {

    @Test
    @DisplayName("are merged after id and email, which they can override")
    void overrideIdAndEmail() {
      Map<String, Object> claims = new LinkedHashMap<>();
      claims.put("email", "other@example.com");
      claims.put("magicWord", "opensesame");
      claims.put("nested", Map.of("a", List.of(1, 2)));
      claims.put("nothing", null);
      String token = helper.forUser("a@b.com", null, TokenOptions.builder().customClaims(claims).build());
      assertThat(payloadJson(token)).isEqualTo(
          "{\"id\":\"a@b.com\",\"email\":\"other@example.com\",\"magicWord\":\"opensesame\",\"nested\":{\"a\":[1,2]},"
              + "\"nothing\":null,\"iat\":1794268800,\"iss\":\"booster\",\"sub\":\"a@b.com\"}");
    }

    @Test
    @DisplayName("of the wrong type for iat, exp or nbf are rejected like jsonwebtoken")
    void invalidTimeClaims() {
      assertThatThrownBy(() -> helper.forUser("a@b.com", null, TokenOptions.builder().customClaim("iat", "x").build()))
          .isInstanceOf(IllegalArgumentException.class).hasMessage("\"iat\" should be a number of seconds");
      assertThatThrownBy(() -> helper.forUser("a@b.com", null, TokenOptions.builder().customClaim("exp", null).build()))
          .hasMessage("\"exp\" should be a number of seconds");
      assertThatThrownBy(() -> helper.forUser("a@b.com", null, TokenOptions.builder().customClaim("nbf", true).build()))
          .hasMessage("\"nbf\" should be a number of seconds");
    }

    @Test
    @DisplayName("conflicting with options are rejected like jsonwebtoken")
    void conflicts() {
      assertThatThrownBy(() -> helper.forUser(
              "a@b.com", null, TokenOptions.builder().customClaim("exp", 1).expiresIn(0).build()))
          .hasMessage("Bad \"options.expiresIn\" option the payload already has an \"exp\" property.");
      assertThatThrownBy(() -> helper.forUser(
              "a@b.com", null, TokenOptions.builder().customClaim("nbf", 1).notBefore(3).build()))
          .hasMessage("Bad \"options.notBefore\" option the payload already has an \"nbf\" property.");
      assertThatThrownBy(() -> helper.forUser("a@b.com", null, TokenOptions.builder().customClaim("iss", "x").build()))
          .hasMessage("Bad \"options.issuer\" option. The payload already has an \"iss\" property.");
      assertThatThrownBy(() -> helper.forUser("a@b.com", null, TokenOptions.builder().customClaim("sub", "x").build()))
          .hasMessage("Bad \"options.subject\" option. The payload already has an \"sub\" property.");
    }

    @Test
    @DisplayName("nbf in the claims is accepted when notBefore is 0 (it is ignored)")
    void nbfWithZeroNotBefore() {
      String token =
          helper.forUser("a@b.com", null, TokenOptions.builder().customClaim("nbf", 7).notBefore(0).build());
      assertThat(TokenHelper.decode(token).payload()).containsEntry("nbf", 7);
    }
  }

  @Nested
  @DisplayName("errors")
  class Errors {

    @Test
    @DisplayName("a null email is rejected because the subject must be a string")
    void nullEmail() {
      assertThatThrownBy(() -> helper.forUser(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("\"subject\" must be a string");
    }

    @Test
    @DisplayName("keys smaller than 2048 bits are rejected")
    void smallKey() throws Exception {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(1024);
      PrivateKey small = generator.generateKeyPair().getPrivate();
      TokenHelper smallKeyHelper = new TokenHelper(small, () -> NOW);
      assertThatThrownBy(() -> smallKeyHelper.forUser("a@b.com"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("secretOrPrivateKey has a minimum key size of 2048 bits for RS256");
    }

    @Test
    @DisplayName("text that is not a PEM key is rejected")
    void notAKey() {
      assertThatThrownBy(() -> new TokenHelper("not a key")).isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> new TokenHelper("-----BEGIN CERTIFICATE-----\nAAAA\n-----END CERTIFICATE-----"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("CERTIFICATE");
    }
  }

  @Nested
  @DisplayName("keys")
  class Keys {

    @Test
    @DisplayName("accepts PKCS#8 keys and key files")
    void pkcs8(@TempDir Path directory) throws Exception {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      KeyPair pair = generator.generateKeyPair();
      String pem = "-----BEGIN PRIVATE KEY-----\n"
          + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(pair.getPrivate().getEncoded())
          + "\n-----END PRIVATE KEY-----\n";
      Path file = directory.resolve("key.pem");
      Files.writeString(file, pem);

      String token = TokenHelper.fromPrivateKeyFile(file).forUser("a@b.com");

      String[] parts = token.split("\\.");
      Signature signature = Signature.getInstance("SHA256withRSA");
      signature.initVerify(pair.getPublic());
      signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
      assertThat(signature.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
    }

    @Test
    @DisplayName("the bundled key pair is the one of the TypeScript packages")
    void sameKeysAsTypeScript() throws Exception {
      Path packages = locatePackages();
      org.junit.jupiter.api.Assumptions.assumeTrue(packages != null, "TypeScript sources not available");
      assertThat(bundledPrivateKeyPem())
          .isEqualTo(Files.readString(packages.resolve("application-tester/keys/private.key")));
      assertThat(TokenHelper.publicKeyPem())
          .isEqualTo(Files.readString(packages.resolve("framework-integration-tests/assets/certs/public.key")));
    }

    @Test
    @DisplayName("decode returns header and payload without verifying")
    void decode() {
      DecodedToken decoded = TokenHelper.decode(helper.forUser("a@b.com", "Admin"));
      assertThat(decoded.kid()).isEqualTo("booster");
      assertThat(decoded.sub()).isEqualTo("a@b.com");
      assertThat(decoded.email()).isEqualTo("a@b.com");
      assertThat(BoosterJson.toJson(decoded.header())).isEqualTo("{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"booster\"}");
      assertThatThrownBy(() -> TokenHelper.decode("a.b")).hasMessage("jwt malformed");
      assertThatThrownBy(() -> TokenHelper.decode("a.b.c")).hasMessage("invalid token");
    }

    private static Path locatePackages() {
      Path current = Path.of("").toAbsolutePath();
      while (current != null) {
        Path candidate = current.resolve("packages/application-tester/keys/private.key");
        if (Files.exists(candidate)) {
          return current.resolve("packages");
        }
        current = current.getParent();
      }
      return null;
    }
  }
}
