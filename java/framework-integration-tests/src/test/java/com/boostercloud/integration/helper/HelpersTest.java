package com.boostercloud.integration.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.types.EnvironmentVariables;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The helpers ported from {@code integration/helper/*.ts} and {@code packages/cli/src/common/sandbox.ts}. */
class HelpersTest {

  @TempDir
  Path directory;

  @AfterEach
  void clearOverrides() {
    EnvironmentVariables.clearOverrides();
  }

  @Test
  void theApplicationNameCarriesTheSuffix() {
    EnvironmentVariables.set(AppHelper.BOOSTER_APP_SUFFIX, "abc1234");
    assertThat(AppHelper.applicationName()).isEqualTo("my-store-abc1234");
  }

  @Test
  void setEnvKeepsAnExistingSuffix() {
    EnvironmentVariables.set(AppHelper.BOOSTER_APP_SUFFIX, "given");
    AppHelper.setEnv();
    assertThat(EnvironmentVariables.get(AppHelper.BOOSTER_APP_SUFFIX)).contains("given");
  }

  @Test
  void theEnvironmentMustBeSetAndNotBlank() {
    EnvironmentVariables.set(EnvironmentVariables.BOOSTER_ENV, "  ");
    assertThatThrownBy(AppHelper::checkAndGetCurrentEnv).hasMessage(
        "Booster environment is missing. You need to provide an environment to configure your Booster project");
    EnvironmentVariables.set(EnvironmentVariables.BOOSTER_ENV, " local");
    assertThat(AppHelper.checkAndGetCurrentEnv()).isEqualTo(" local");
  }

  @Test
  void anUnknownProviderIsRejectedWithTheTypeScriptMessage() {
    EnvironmentVariables.set(EnvironmentVariables.BOOSTER_ENV, "local");
    EnvironmentVariables.set(AppHelper.TESTED_PROVIDER, "AWS");
    assertThatThrownBy(AppHelper::getProviderTestHelper).hasMessage(
        "Invalid provider to run tests. Environment variable TESTED_PROVIDER is AWS and the supported ones are "
            + "[AZURE,LOCAL]");
  }

  @Test
  void fixturesAreLoadedWithLiteralGlobalReplacements() throws Exception {
    Files.writeString(directory.resolve("fixture.txt"), "name: placeholder, again: placeholder, $1 (.*)");

    assertThat(FileHelper.loadFixture(directory, "fixture.txt", null))
        .isEqualTo("name: placeholder, again: placeholder, $1 (.*)");
    assertThat(FileHelper.loadFixture(directory, "fixture.txt",
        List.of(List.of("placeholder", "store"), List.of("(.*)", "$2"))))
        .isEqualTo("name: store, again: store, $1 $2");
  }

  @Test
  void filesAndFoldersAreCreatedListedAndRemoved() throws Exception {
    Path folder = directory.resolve("folder");
    FileHelper.createFolder(folder);
    FileHelper.createFolder(folder);
    FileHelper.writeFileContent(folder.resolve("b.txt"), "b");
    FileHelper.writeFileContent(folder.resolve("a.txt"), "a");
    Files.createDirectories(folder.resolve("nested/deeper"));
    Files.writeString(folder.resolve("nested/deeper/c.txt"), "c");

    assertThat(FileHelper.dirContents(folder)).containsExactly("a.txt", "b.txt", "nested");
    assertThat(FileHelper.fileExists(folder.resolve("a.txt"))).isTrue();

    FileHelper.removeFiles(List.of(folder.resolve("a.txt")));
    assertThat(FileHelper.fileExists(folder.resolve("a.txt"))).isFalse();
    assertThatThrownBy(() -> FileHelper.removeFiles(List.of(folder.resolve("a.txt"))))
        .isInstanceOf(UncheckedIOException.class);
    FileHelper.removeFiles(List.of(folder.resolve("a.txt")), true);

    FileHelper.removeFolders(List.of(folder, directory.resolve("missing")));
    assertThat(FileHelper.fileExists(folder)).isFalse();
    assertThat(FileHelper.sandboxPathFor("local-project")).isEqualTo("local-project-integration-sandbox");
  }

  @Test
  void aSandboxIsRecreatedWithTheAssets() throws Exception {
    Path project = directory.resolve("project");
    Files.createDirectories(project.resolve("assets/certs"));
    Files.writeString(project.resolve("assets/certs/public.key"), "key");
    Files.writeString(project.resolve("assetFile.txt"), "file");
    Path sandbox = directory.resolve("sandbox-integration-sandbox");
    Files.createDirectories(sandbox.resolve(".booster"));

    Sandbox.createSandboxProject(project, sandbox, List.of("assets", "assetFile.txt"));

    assertThat(Files.readString(sandbox.resolve("assets/certs/public.key"))).isEqualTo("key");
    assertThat(Files.readString(sandbox.resolve("assetFile.txt"))).isEqualTo("file");
    assertThat(sandbox.resolve(".booster")).doesNotExist();
    Sandbox.removeSandboxProject(sandbox);
    assertThat(sandbox).doesNotExist();
  }

  @Test
  void passwordsMeetTheUsualRules() {
    String password = AuthHelper.createPassword();
    assertThat(password).hasSize(17).endsWith("Passw0rd!");
    assertThat(password.substring(0, 8)).matches("[A-Za-z0-9]{8}");
  }
}
