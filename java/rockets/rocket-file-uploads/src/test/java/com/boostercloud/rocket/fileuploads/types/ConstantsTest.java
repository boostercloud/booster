package com.boostercloud.rocket.fileuploads.types;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.types.EnvironmentVariables;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** {@code constants.ts}. */
class ConstantsTest {

  @AfterEach
  void tearDown() {
    EnvironmentVariables.unset(Constants.LOCAL_PORT_ENV);
  }

  @Test
  void the_azure_storage_name_is_the_configured_one() {
    assertThat(Constants.azureStorageName("my-app", "dev", "abc")).isEqualTo("abc");
    assertThat(Constants.azureStorageName("my-app", "dev", "ABC")).isEqualTo("ABC");
  }

  @Test
  void without_name_it_is_app_rf_environment_without_dashes_and_underscores_cut_to_24() {
    assertThat(Constants.azureStorageName("my-app", "dev", null)).isEqualTo("myapprfdev");
    assertThat(Constants.azureStorageName("my_App", "pro-d", "")).isEqualTo("myApprfprod");
    assertThat(Constants.azureStorageName("averyveryverylongapplication", "production", null))
        .isEqualTo("averyveryverylongapplica").hasSize(24);
    assertThat(Constants.azureStorageName(null, null, null)).isEqualTo("undefinedrfundefined");
  }

  @Test
  void the_local_port_comes_from_the_rocket_variable_or_3000() {
    EnvironmentVariables.unset(Constants.LOCAL_PORT_ENV);
    assertThat(Constants.LOCAL_PORT_ENV).isEqualTo("ROCKET_FILES_LOCAL_PORT");
    EnvironmentVariables.set(Constants.LOCAL_PORT_ENV, "");
    assertThat(Constants.localPort()).isEqualTo("3000");
    EnvironmentVariables.set(Constants.LOCAL_PORT_ENV, "4000");
    assertThat(Constants.localPort()).isEqualTo("4000");
  }
}
