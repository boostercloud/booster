package com.boostercloud.framework.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.types.concepts.Authorize;
import com.boostercloud.framework.types.concepts.CommandAuthorizer;
import com.boostercloud.framework.types.concepts.CommandRoleAccess;
import com.boostercloud.framework.types.concepts.HealthAuthorizer;
import com.boostercloud.framework.types.concepts.HealthRoleAccess;
import com.boostercloud.framework.types.envelope.CommandEnvelope;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.framework.types.errors.NotAuthorizedError;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Port of {@code test/booster-authorizer.test.ts}, plus {@code build}. */
class BoosterAuthorizerTest {

  static class Admin {}

  static class Developer {}

  static class Custom implements CommandAuthorizer {
    @Override
    public void authorize(UserEnvelope currentUser, CommandEnvelope commandEnvelope) {}
  }

  @Test
  void allowAccessAllows() {
    assertThatCode(BoosterAuthorizer::allowAccess).doesNotThrowAnyException();
  }

  @Test
  void denyAccessDenies() {
    assertThatThrownBy(BoosterAuthorizer::denyAccess).isInstanceOf(NotAuthorizedError.class)
        .hasMessage("Access denied for this resource");
  }

  @Test
  void authorizeRolesAllowsUsersWithOneOfTheRoles() {
    assertThatCode(() -> BoosterAuthorizer.authorizeRoles(List.of(Admin.class),
        UserEnvelope.builder().roles(List.of("Admin", "Developer")).build())).doesNotThrowAnyException();
  }

  @Test
  void authorizeRolesDeniesOtherUsersAnonymousUsersAndEmptyRoleLists() {
    assertThatThrownBy(() -> BoosterAuthorizer.authorizeRoles(List.of(Admin.class),
        UserEnvelope.builder().roles(List.of("Reader")).build())).isInstanceOf(NotAuthorizedError.class);
    assertThatThrownBy(() -> BoosterAuthorizer.authorizeRoles(List.of(Admin.class), null))
        .isInstanceOf(NotAuthorizedError.class);
    assertThatThrownBy(() -> BoosterAuthorizer.authorizeRoles(List.of(),
        UserEnvelope.builder().roles(List.of("Admin")).build())).isInstanceOf(NotAuthorizedError.class);
  }

  @Test
  void buildChoosesTheAuthorizer() {
    assertThat(BoosterAuthorizer.build((CommandRoleAccess) null)).isSameAs(BoosterAuthorizer.DENY_ACCESS);
    assertThat(BoosterAuthorizer.build(new CommandRoleAccess(null))).isSameAs(BoosterAuthorizer.DENY_ACCESS);
    assertThat(BoosterAuthorizer.build(new CommandRoleAccess(Authorize.all()))).isSameAs(BoosterAuthorizer.ALLOW_ACCESS);
    Custom custom = new Custom();
    assertThat(BoosterAuthorizer.build(new CommandRoleAccess(Authorize.custom(custom)))).isSameAs(custom);
    CommandAuthorizer roles = BoosterAuthorizer.build(new CommandRoleAccess(Authorize.roles(Admin.class, Developer.class)));
    assertThatCode(() -> roles.authorize(UserEnvelope.builder().roles(List.of("Developer")).build(), null))
        .doesNotThrowAnyException();
    HealthAuthorizer health = BoosterAuthorizer.build(HealthRoleAccess.all());
    assertThat(health).isSameAs(BoosterAuthorizer.ALLOW_ACCESS);
  }

  @Test
  void convertsAnnotationValues() {
    assertThat(BoosterAuthorizer.fromAnnotation(new Class<?>[0], CommandAuthorizer.class)).isNull();
    assertThat(BoosterAuthorizer.fromAnnotation(new Class<?>[] {All.class}, CommandAuthorizer.class)).isEqualTo(Authorize.all());
    assertThat(BoosterAuthorizer.fromAnnotation(new Class<?>[] {Admin.class, Developer.class}, CommandAuthorizer.class))
        .isEqualTo(Authorize.roles(Admin.class, Developer.class));
    assertThat(BoosterAuthorizer.fromAnnotation(new Class<?>[] {Custom.class}, CommandAuthorizer.class))
        .isInstanceOfSatisfying(Authorize.Custom.class, custom -> assertThat(custom.authorizer()).isInstanceOf(Custom.class));
    assertThatThrownBy(() -> BoosterAuthorizer.fromAnnotation(new Class<?>[] {All.class, Admin.class}, CommandAuthorizer.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("All must be the only class");
  }
}
