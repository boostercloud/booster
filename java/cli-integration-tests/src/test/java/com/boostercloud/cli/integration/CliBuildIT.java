package com.boostercloud.cli.integration;

import static com.boostercloud.cli.integration.helper.Expectations.assertSucceeds;
import static com.boostercloud.cli.integration.helper.FileHelper.fileExists;
import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.cli.integration.helper.Boost;
import com.boostercloud.cli.integration.helper.CliResult;
import com.boostercloud.cli.integration.helper.CliTestEnvironment;
import com.boostercloud.cli.integration.helper.FileHelper;
import com.boostercloud.cli.integration.helper.JavaSources;
import com.boostercloud.cli.integration.helper.Sandbox;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Port of {@code cli.build.integration.ts}.
 *
 * <ul>
 *   <li>{@code Build}: {@code boost build} compiles the test application (TypeScript checks {@code dist/*.js} and
 *       {@code *.d.ts}; here the class files and the runtime class path {@code target/lib} that {@code boost start}
 *       uses).</li>
 *   <li>{@code Compile fallback}: TypeScript runs the project's {@code compile} npm script instead of the default
 *       compiler when there is one. The Java CLI has no script lookup; its counterpart is the project's Maven Wrapper,
 *       which the CLI runs instead of {@code mvn} when the project has one. The wrapper of the sandbox writes
 *       {@code eureka} (like the TypeScript script) and then runs Maven.</li>
 *   <li>Java only: a compilation error makes the command fail with the compiler output in {@code errors.log}, and a
 *       directory that is not a Booster project is rejected.</li>
 * </ul>
 */
@DisplayName("Build")
class CliBuildIT {
  static final String[] EXPECTED_OUTPUT = {"boost build", "Checking project structure", "Building project",
      "Build complete"};

  @Nested
  @DisplayName("Build")
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class Build {
    Path buildSandboxDir;

    @BeforeAll
    void createSandbox() {
      buildSandboxDir = Sandbox.createSandboxProject("build");
    }

    @AfterAll
    void removeSandbox() {
      Sandbox.removeSandboxProject(buildSandboxDir);
    }

    @Test
    @DisplayName("Valid build > should build the project")
    void shouldBuildTheProject() {
      CliResult result = Boost.command(buildSandboxDir, "build");

      assertSucceeds(result, EXPECTED_OUTPUT);
      assertThat(fileExists(Sandbox.classFile(buildSandboxDir, "", "Main"))).isTrue();
      assertThat(fileExists(Sandbox.classFile(buildSandboxDir, "roles", "Admin"))).isTrue();
      assertThat(fileExists(Sandbox.classFile(buildSandboxDir, "config", "Config"))).isTrue();
      assertThat(fileExists(Sandbox.classFile(buildSandboxDir, "commands", "ChangeCartItem"))).isTrue();
      assertThat(fileExists(Sandbox.classFile(buildSandboxDir, "readmodels", "CartReadModel"))).isTrue();
      assertThat(
              fileExists(
                  buildSandboxDir.resolve(
                      "target/lib/booster-framework-core-" + CliTestEnvironment.boosterVersion() + ".jar")))
          .isTrue();
      assertThat(
              fileExists(
                  buildSandboxDir.resolve(
                      "target/lib/booster-framework-provider-local-infrastructure-"
                          + CliTestEnvironment.boosterVersion()
                          + ".jar")))
          .isTrue();
      assertThat(buildSandboxDir.resolve("errors.log")).doesNotExist();
    }
  }

  @Nested
  @DisplayName("Compile fallback")
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  @DisabledOnOs(value = OS.WINDOWS, disabledReason = "the sandbox wrapper is a POSIX shell script")
  class CompileFallback {
    Path compileSandboxDir;

    @BeforeAll
    void createSandbox() throws Exception {
      compileSandboxDir = Sandbox.createSandboxProject("compile");

      // Add a Maven Wrapper to the project (TypeScript: a 'compile' script in the package.json)
      Path wrapper = compileSandboxDir.resolve("mvnw");
      FileHelper.writeFileContent(
          wrapper,
          """
          #!/bin/sh
          echo "eureka" > eureka
          exec mvn "$@"
          """);
      Files.setPosixFilePermissions(wrapper, PosixFilePermissions.fromString("rwxr-xr-x"));
    }

    @AfterAll
    void removeSandbox() {
      Sandbox.removeSandboxProject(compileSandboxDir);
    }

    @Test
    @DisplayName("when a Maven Wrapper is present > should call the wrapper instead")
    void shouldCallTheWrapperInstead() {
      CliResult result = Boost.command(compileSandboxDir, "build");

      assertSucceeds(result, EXPECTED_OUTPUT);
      assertThat(fileExists(compileSandboxDir.resolve("eureka"))).isTrue();
      assertThat(fileExists(Sandbox.classFile(compileSandboxDir, "", "Main"))).isTrue();
    }
  }

  @Nested
  @DisplayName("Invalid build (Java)")
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class InvalidBuild {
    Path brokenSandboxDir;

    @BeforeAll
    void createSandbox() {
      brokenSandboxDir = Sandbox.createSandboxProject("broken-build");
      JavaSources.edit(
          Sandbox.sourceFile(brokenSandboxDir, "commands", "ChangeCartItem"),
          "register.events(",
          "register.eventz(");
    }

    @AfterAll
    void removeSandbox() {
      Sandbox.removeSandboxProject(brokenSandboxDir);
    }

    @Test
    @DisplayName("with a compilation error > should fail with the compiler output in errors.log")
    void compilationError() {
      CliResult result = Boost.command(brokenSandboxDir, "build");

      assertThat(result.exitCode()).as(result.describe()).isEqualTo(1);
      assertThat(result.stdout()).contains("Building project");
      assertThat(result.stdout()).doesNotContain("Build complete");
      assertThat(result.stderr()).contains("(You can see the full error logs in ./errors.log)");
      assertThat(FileHelper.readFileContent(brokenSandboxDir.resolve("errors.log")))
          .contains("boost build")
          .contains("ChangeCartItem.java")
          .contains("eventz");
    }

    @Test
    @DisplayName("outside a Booster project > should fail")
    void outsideABoosterProject() {
      Path notAProject = FileHelper.sandboxPathFor("not-a-project");
      FileHelper.removeFolders(java.util.List.of(notAProject));
      FileHelper.createFolder(notAProject);
      try {
        CliResult result = Boost.command(notAProject, "build");

        assertThat(result.exitCode()).as(result.describe()).isEqualTo(1);
        // As in TypeScript the project is recognised before the command runs: nothing is printed on stdout
        assertThat(result.stdout()).doesNotContain("Building project");
        assertThat(result.stderr())
            .contains(
                "Error: There was an error when recognizing the application. Make sure you are in the root path of a"
                    + " Booster project");
      } finally {
        Sandbox.removeSandboxProject(notAProject);
      }
    }
  }
}
