package com.boostercloud.integration.harness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.integration.helper.FileHelper;
import java.io.File;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * What {@link AppProcess} launches and with which environment, checked without launching anything (the end-to-end
 * suites launch real processes), plus {@link FreePorts}.
 */
class HarnessTest {

  @Test
  void theApplicationIsLaunchedLikeTheCliLaunchesAProject() {
    AppProcess app = AppProcess.builder("harness-test").ports(4100, 4101).build();

    assertThat(app.command()).containsExactly(E2eSettings.javaExecutable().toString(), "-cp",
        E2eSettings.classesDirectory() + File.pathSeparator + E2eSettings.libDirectory().resolve("*"),
        E2eSettings.mainClass(), "start", "--port", "4100");
    assertThat(E2eSettings.mainClass()).isEqualTo("com.boostercloud.integration.Main");
    assertThat(app.environment()).containsEntry("BOOSTER_ENV", "local")
        .containsEntry("BOOSTER_INTERNAL_LOCAL_PORT", "4100")
        .containsEntry("LOCAL_ENVIRONMENT_WEBSOCKET_SERVER_PORT", "4101");
  }

  @Test
  void itsUrlsAndDirectoriesFollowTheNameAndThePorts() {
    AppProcess app = AppProcess.builder("harness-test").ports(4100, 4101).build();

    assertThat(app.graphqlURL()).isEqualTo("http://localhost:4100/graphql");
    assertThat(app.websocketURL()).isEqualTo("ws://localhost:4101/websocket");
    assertThat(app.healthURL()).isEqualTo("http://localhost:4100/sensor/health/");
    assertThat(app.projectDirectory())
        .isEqualTo(E2eSettings.projectsDirectory().resolve("harness-test-integration-sandbox"));
    assertThat(app.boosterDirectory()).isEqualTo(app.projectDirectory().resolve(".booster"));
    assertThat(app.logFile()).isEqualTo(E2eSettings.logsDirectory().resolve("harness-test.log"));
    assertThat(app.isAlive()).isFalse();
    assertThat(app.exitCode()).isNull();
    assertThat(app.starts()).isZero();
  }

  @Test
  void theBuilderTakesAProjectDirectoryExtraVariablesAndJvmOptions(@org.junit.jupiter.api.io.TempDir Path directory) {
    AppProcess app = AppProcess.builder("custom").projectDirectory(directory).environment("azure")
        .env("EXTRA", "1").jvmOption("-Xmx256m").readinessTimeout(Duration.ofSeconds(5)).build();

    assertThat(app.projectDirectory()).isEqualTo(directory.toAbsolutePath().normalize());
    assertThat(app.environment()).containsEntry("BOOSTER_ENV", "azure").containsEntry("EXTRA", "1");
    assertThat(app.command()).contains("-Xmx256m");
    assertThat(app.command().indexOf("-Xmx256m")).isLessThan(app.command().indexOf("-cp"));
    assertThat(app.httpPort()).isNotEqualTo(app.webSocketPort());
  }

  @Test
  void anApplicationThatNeverStartedCannotBeStopped() {
    AppProcess app = AppProcess.builder("never-started").ports(4100, 4101).build();

    assertThatThrownBy(app::stop).isInstanceOf(IllegalStateException.class).hasMessageContaining("never started");
    assertThatThrownBy(app::kill).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(app::pid).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> AppProcess.builder("bad name")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void freePortsAreDistinctAndNeverHandedOutTwice() throws Exception {
    Set<Integer> seen = new HashSet<>();
    for (int i = 0; i < 20; i++) {
      List<Integer> ports = FreePorts.allocate(2);
      assertThat(ports).doesNotHaveDuplicates();
      for (int port : ports) {
        assertThat(seen.add(port)).as("port %d handed out twice", port).isTrue();
        assertThat(port).isNotIn(3000, 65529);
      }
    }
    int port = FreePorts.allocate();
    assertThat(FreePorts.isFree(port)).isTrue();
    try (ServerSocket taken = new ServerSocket(port)) {
      assertThat(FreePorts.isFree(taken.getLocalPort())).isFalse();
    }
  }

  @Test
  void thePidFileHoldsTheDecimalPidWithoutNewLine(@org.junit.jupiter.api.io.TempDir Path sandbox) {
    FileHelper.storePIDFor(sandbox, 4242);

    assertThat(FileHelper.readFileContent(FileHelper.pidForSandboxPath(sandbox))).isEqualTo("4242");
    assertThat(FileHelper.readPIDFor(sandbox)).isEqualTo(4242);
    assertThat(FileHelper.pidForSandboxPath(sandbox)).isEqualTo(sandbox.resolve("local_provider.pid"));
  }
}
