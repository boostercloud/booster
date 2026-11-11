package com.boostercloud.rocket.fileuploads.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** {@code getUserConfiguration} (TypeScript has no test for it; the behaviour of {@code rocket-files-params.ts}). */
class RocketFilesParamsTest {

  private static final RocketFilesUserConfiguration CLIENT =
      new RocketFilesUserConfiguration("clientst", "rocketfiles", List.of("client1"));
  private static final RocketFilesUserConfiguration CMS =
      new RocketFilesUserConfiguration("cmsst", "rocketfiles", List.of("cms1"));

  private static RocketFilesConfiguration configuration(RocketFilesUserConfiguration... configurations) {
    return new RocketFilesConfiguration(List.of(configurations), RocketProviderPackageType.LOCAL);
  }

  @Test
  void the_function_id() {
    assertThat(RocketFilesParams.FUNCTION_ID).isEqualTo("rocket-files");
  }

  @Test
  void without_configurations_it_fails() {
    assertThatThrownBy(() -> RocketFilesParams.getUserConfiguration(configuration(), null))
        .hasMessage("Missing userConfiguration");
    assertThatThrownBy(() -> RocketFilesParams.getUserConfiguration(configuration(), "clientst"))
        .hasMessage("Missing userConfiguration");
  }

  @Test
  void several_configurations_need_a_name_and_empty_counts_as_missing() {
    assertThatThrownBy(() -> RocketFilesParams.getUserConfiguration(configuration(CLIENT, CMS), null))
        .hasMessage("Missing storageAccountName");
    assertThatThrownBy(() -> RocketFilesParams.getUserConfiguration(configuration(CLIENT, CMS), ""))
        .hasMessage("Missing storageAccountName");
  }

  @Test
  void a_single_configuration_is_used_without_name() {
    assertThat(RocketFilesParams.getUserConfiguration(configuration(CMS), null)).isEqualTo(CMS);
    assertThat(RocketFilesParams.getUserConfiguration(configuration(CMS), "")).isEqualTo(CMS);
  }

  @Test
  void names_match_ignoring_case_and_the_first_match_wins() {
    RocketFilesUserConfiguration second = new RocketFilesUserConfiguration("clientst", "other", List.of("x"));
    assertThat(RocketFilesParams.getUserConfiguration(configuration(CLIENT, CMS), "CMSst")).isEqualTo(CMS);
    assertThat(RocketFilesParams.getUserConfiguration(configuration(CLIENT, second), "clientST")).isEqualTo(CLIENT);
  }

  @Test
  void an_unknown_name_is_reported_as_given() {
    assertThatThrownBy(() -> RocketFilesParams.getUserConfiguration(configuration(CLIENT, CMS), "Foo"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Storage account name Foo not found");
    assertThatThrownBy(() -> RocketFilesParams.getUserConfiguration(configuration(CLIENT), "foo"))
        .hasMessage("Storage account name foo not found");
  }
}
