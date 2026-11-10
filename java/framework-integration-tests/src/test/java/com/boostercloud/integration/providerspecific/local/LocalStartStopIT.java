package com.boostercloud.integration.providerspecific.local;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.provider.local.infrastructure.testhelper.LocalTestHelper;
import com.boostercloud.integration.harness.AppProcess;
import com.boostercloud.integration.harness.FreePorts;
import com.boostercloud.integration.harness.LocalProviderTestHelper;
import com.boostercloud.integration.helper.AppHelper;
import com.boostercloud.integration.helper.FileHelper;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Port of the local start/stop lifecycle ({@code integration/provider-specific/local/start} and {@code stop}, spec 17
 * §3.1): the start setup creates the sandbox {@code local-project-integration-sandbox} with the assets, starts the
 * application ({@code boost start -e local} in TypeScript; the CLI launch contract here) and stores its PID in
 * {@code local_provider.pid}; the stop setup reads the PID, sends SIGTERM through it and removes the sandbox.
 *
 * <p>TypeScript runs the two phases as separate mocha runs around the end-to-end suite and asserts {@code true} in
 * each. Here both phases run in this class, against an application of their own (the end-to-end suites use the shared
 * one), and the trivial assertions are kept; the additional tests check what the phases imply: the application answers
 * through {@code LocalTestHelper} (the TypeScript TODO), and after the stop the process ended gracefully, its ports
 * are free and the sandbox is gone.
 */
@DisplayName("local provider start and stop")
class LocalStartStopIT {

  /** {@code sandboxName} of {@code integration/provider-specific/local/constants.ts} plus a suffix of this suite. */
  static final String SANDBOX_NAME = "local-project-start-stop";

  private AppProcess serverProcess;
  private Path sandboxPath;

  /** {@code local/start/setup.ts}. */
  @BeforeAll
  void startLocalServer() {
    System.out.println("preparing sandboxed project...");
    serverProcess = AppProcess.builder(SANDBOX_NAME).assets(List.of("assets", "assetFile.txt")).build();
    sandboxPath = serverProcess.projectDirectory();
    assertThat(sandboxPath.getFileName().toString()).isEqualTo(FileHelper.sandboxPathFor(SANDBOX_NAME));

    System.out.println("starting local server in " + sandboxPath + "...");
    serverProcess.start();
    if (serverProcess.pid() <= 0) {
      throw new IllegalStateException("Pid not found");
    }
    // AppProcess stores the PID like storePIDFor(sandboxPath, serverProcess.pid)
    System.out.println("local server ready");
  }

  @AfterAll
  void cleanUp() {
    if (serverProcess != null) {
      serverProcess.close();
    }
  }

  @Nested
  @Order(1)
  @DisplayName("After start")
  class AfterStart {

    @Nested
    @DisplayName("the local provider")
    class TheLocalProvider {

      @Test
      @Order(1)
      @DisplayName("has been started successfully")
      void hasBeenStartedSuccessfully() {
        assertThat(true).isTrue();
      }

      @Test
      @Order(2)
      @DisplayName("answers through the local test helper")
      void answersThroughTheLocalTestHelper() {
        // The TypeScript TODO: await expect(LocalTestHelper.build(applicationName())).to.be.eventually.fulfilled
        LocalTestHelper helper = LocalTestHelper.build(AppHelper.applicationName(),
            LocalProviderTestHelper.options(serverProcess).withReadinessTimeout(Duration.ZERO));

        assertThat(helper.outputs().graphqlURL()).isEqualTo(serverProcess.graphqlURL());
        assertThat(helper.outputs().websocketURL()).isEqualTo(serverProcess.websocketURL());
        assertThat(helper.outputs().healthURL()).isEqualTo(serverProcess.healthURL());
      }

      @Test
      @Order(3)
      @DisplayName("keeps the PID and the assets in the sandbox")
      void keepsThePidAndTheAssetsInTheSandbox() {
        assertThat(FileHelper.readPIDFor(sandboxPath)).isEqualTo(serverProcess.pid());
        assertThat(FileHelper.fileExists(sandboxPath.resolve("assets").resolve("certs").resolve("public.key")))
            .isTrue();
        assertThat(FileHelper.fileExists(sandboxPath.resolve("assetFile.txt"))).isTrue();
      }
    }
  }

  @Nested
  @Order(2)
  @DisplayName("After stop")
  class AfterStop {
    private int exitCode;

    /** {@code local/stop/setup.ts}. */
    @BeforeAll
    void stopLocalServer() {
      long pid = FileHelper.readPIDFor(sandboxPath);
      System.out.println("stopping local server with pid " + pid + "...");
      // process.kill(pid): SIGTERM
      ProcessHandle.of(pid).ifPresent(ProcessHandle::destroy);
      exitCode = serverProcess.awaitExit(AppProcess.DEFAULT_STOP_TIMEOUT);
      System.out.println("removing sandbox project...");
      FileHelper.removeFolders(List.of(sandboxPath));
    }

    @Nested
    @DisplayName("the provider")
    class TheProvider {

      @Test
      @Order(1)
      @DisplayName("is stopped successfully")
      void isStoppedSuccessfully() {
        assertThat(true).isTrue();
      }

      @Test
      @Order(2)
      @DisplayName("ended through the graceful shutdown")
      void endedThroughTheGracefulShutdown() {
        assertThat(serverProcess.isAlive()).isFalse();
        assertThat(exitCode).isEqualTo(AppProcess.SIGTERM_EXIT_CODE);
        assertThat(serverProcess.output())
            .contains("Stopping the local server on http://localhost:" + serverProcess.httpPort())
            .contains("Local server stopped");
      }

      @Test
      @Order(3)
      @DisplayName("released its ports and the sandbox is removed")
      void releasedItsPortsAndTheSandboxIsRemoved() {
        assertThat(FreePorts.isFree(serverProcess.httpPort())).isTrue();
        assertThat(FreePorts.isFree(serverProcess.webSocketPort())).isTrue();
        assertThat(FileHelper.fileExists(sandboxPath)).isFalse();
      }
    }
  }
}
