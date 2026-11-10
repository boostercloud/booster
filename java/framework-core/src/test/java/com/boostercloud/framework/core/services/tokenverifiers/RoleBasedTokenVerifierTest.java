package com.boostercloud.framework.core.services.tokenverifiers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.types.concepts.DecodedToken;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Port of {@code test/services/token-verifiers/role-based-token-verifier.test.ts}. */
class RoleBasedTokenVerifierTest {

  static class TestVerifier extends RoleBasedTokenVerifier {
    TestVerifier(String rolesClaim) {
      super(rolesClaim);
    }

    @Override
    public DecodedToken verify(String token) {
      throw new UnsupportedOperationException();
    }
  }

  @Test
  void theDefaultRolesClaimIsCustomRole() {
    assertThat(RoleBasedTokenVerifier.DEFAULT_ROLES_CLAIM).isEqualTo("custom:role");
    assertThat(new TestVerifier(null).rolesClaim()).isEqualTo("custom:role");
  }

  @Test
  void buildsTheUserEnvelopeWithTheDefaultClaim() {
    Map<String, Object> header = Map.of("kid", "kid123");
    Map<String, Object> payload = Map.of("sub", "sub123", "email", "morenito19@example.com", "custom:role", List.of("ProUser"));

    UserEnvelope user = new TestVerifier(null).toUserEnvelope(new DecodedToken(header, payload));

    assertThat(user).isEqualTo(new UserEnvelope("sub123", "morenito19@example.com", List.of("ProUser"), payload, header));
    assertThat(user.claims()).isSameAs(payload);
  }

  @Test
  void usesAConfiguredRolesClaim() {
    Map<String, Object> header = Map.of("kid", "kid123");
    Map<String, Object> payload = Map.of("sub", "sub123", "email", "morenito19@example.com", "ekipaso", List.of("ProUser"));

    assertThat(new TestVerifier("ekipaso").toUserEnvelope(new DecodedToken(header, payload)))
        .isEqualTo(new UserEnvelope("sub123", "morenito19@example.com", List.of("ProUser"), payload, header));
  }

  @Test
  void theUsernameIsTheFirstTruthyOfEmailPhoneAndSub() {
    TestVerifier verifier = new TestVerifier(null);

    assertThat(verifier.toUserEnvelope(new DecodedToken(Map.of(), Map.of("sub", "s", "phone_number", "+1"))).username())
        .isEqualTo("+1");
    assertThat(verifier.toUserEnvelope(new DecodedToken(Map.of(), Map.of("sub", "s", "email", ""))).username())
        .isEqualTo("s");
    assertThat(verifier.toUserEnvelope(new DecodedToken(Map.of(), Map.of())).username()).isNull();
  }

  @Test
  void convertsTheRolesClaim() {
    assertThat(RoleBasedTokenVerifier.rolesFromTokenRole(null)).isEmpty();
    assertThat(RoleBasedTokenVerifier.rolesFromTokenRole("")).isEmpty();
    assertThat(RoleBasedTokenVerifier.rolesFromTokenRole(0)).isEmpty();
    assertThat(RoleBasedTokenVerifier.rolesFromTokenRole(false)).isEmpty();
    assertThat(RoleBasedTokenVerifier.rolesFromTokenRole("User")).containsExactly("User");
    assertThat(RoleBasedTokenVerifier.rolesFromTokenRole(List.of("User", "Other"))).containsExactly("User", "Other");
    assertThat(RoleBasedTokenVerifier.rolesFromTokenRole(List.of())).isEmpty();
    assertThat(RoleBasedTokenVerifier.rolesFromTokenRole(List.of("a", ""))).containsExactly("a", "");
    assertThatThrownBy(() -> RoleBasedTokenVerifier.rolesFromTokenRole(123))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Invalid role format 123. Valid format are Array<string> or string");
    assertThatThrownBy(() -> RoleBasedTokenVerifier.rolesFromTokenRole(Arrays.asList("a", "b", 1.5)))
        .hasMessage("Invalid role format 1.5. Valid format are Array<string> or string");
  }
}
