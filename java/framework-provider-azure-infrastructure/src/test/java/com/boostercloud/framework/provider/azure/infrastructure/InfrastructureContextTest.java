package com.boostercloud.framework.provider.azure.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.provider.azure.infrastructure.support.Fixtures;
import com.boostercloud.framework.provider.azure.infrastructure.support.TestEnvironment;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.EnvironmentVariables;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(TestEnvironment.class)
class InfrastructureContextTest {

  @TempDir Path root;

  @Test
  void the_application_class_comes_from_booster_app_class_first() {
    EnvironmentVariables.set("BOOSTER_APP_CLASS", "com.example.Main");
    assertThat(InfrastructureContext.resolveApplicationClass()).isEqualTo("com.example.Main");
  }

  @Test
  void otherwise_it_is_the_main_class_of_the_jvm_when_it_is_a_class() {
    String previous = System.getProperty("sun.java.command");
    try {
      System.setProperty("sun.java.command", "com.example.store.Main deploy");
      assertThat(InfrastructureContext.resolveApplicationClass()).isEqualTo("com.example.store.Main");
      System.setProperty("sun.java.command", "/opt/app.jar deploy");
      assertThat(InfrastructureContext.resolveApplicationClass()).isNull();
      System.setProperty("sun.java.command", "");
      assertThat(InfrastructureContext.resolveApplicationClass()).isNull();
    } finally {
      if (previous == null) {
        System.clearProperty("sun.java.command");
      } else {
        System.setProperty("sun.java.command", previous);
      }
    }
  }

  @Test
  void fails_clearly_when_the_application_class_is_unknown() {
    InfrastructureContext context = InfrastructureContext.builder().projectRoot(root).build();
    assertThatThrownBy(context::applicationClass)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Could not determine the application class the Azure function apps must start. Set the"
            + " BOOSTER_APP_CLASS environment variable to the class passed to Booster.run (or Booster.start)");
  }

  @Test
  void the_project_root_is_the_user_project_root_path_or_the_working_directory() {
    BoosterConfig config = Fixtures.config();
    assertThat(InfrastructureContext.defaults(config).projectRoot())
        .isEqualTo(Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize());
    config.setUserProjectRootPath(root.toString() + "/");
    assertThat(InfrastructureContext.defaults(config).projectRoot()).isEqualTo(root.toAbsolutePath().normalize());
  }

  @Test
  void builds_the_management_clients_with_the_configuration() {
    InfrastructureContext context = Fixtures.context(root, null, null, null);
    assertThat(context.credential()).isSameAs(context.credential());
    assertThat(context.armClient().url("/x", "1"))
        .hasToString("https://management.azure.com/subscriptions/subscription-id/x?api-version=1");
    assertThat(context.configuration()).isEqualTo(Fixtures.CONFIGURATION);
    assertThat(context.deploySources(Fixtures.config()).classpath()).hasSize(3);
  }
}
