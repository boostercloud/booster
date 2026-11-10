package com.boostercloud.framework.core.decorators;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.BoosterTestSupport;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.concepts.AuthMetadata;
import com.boostercloud.framework.types.concepts.RoleMetadata;
import com.boostercloud.framework.types.concepts.SignUpMethod;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Port of {@code test/decorators/role.test.ts}. */
class RoleTest extends BoosterTestSupport {

  @Role
  public static class SomeRole {}

  @Role(auth = @RoleAuth(signUpMethods = {"email", "phone"}, skipConfirmation = true))
  public static class AuthRole {}

  @Test
  void registersARoleWithEmptyAuth() {
    Decorators.registerClass(SomeRole.class);

    assertThat(Booster.config().roles.get("SomeRole")).isEqualTo(new RoleMetadata(new AuthMetadata(null, null)));
    assertThat(BoosterJson.toRawMap(Booster.config().roles.get("SomeRole"))).isEqualTo(Map.of("auth", Map.of()));
  }

  @Test
  void registersTheAuthSettings() {
    Decorators.registerClass(AuthRole.class);

    assertThat(Booster.config().roles.get("AuthRole"))
        .isEqualTo(new RoleMetadata(new AuthMetadata(List.of(SignUpMethod.EMAIL, SignUpMethod.PHONE), true)));
    assertThat(BoosterJson.toRawMap(Booster.config().roles.get("AuthRole")))
        .isEqualTo(Map.of("auth", Map.of("signUpMethods", List.of("email", "phone"), "skipConfirmation", true)));
  }

  @Test
  void theLastRegistrationWins() {
    Decorators.registerClass(AuthRole.class);
    Decorators.registerRole(AuthRole.class, null);

    assertThat(Booster.config().roles.get("AuthRole")).isEqualTo(RoleMetadata.defaults());
  }
}
