package com.boostercloud.cli.services.packagemanager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.cli.services.filesystem.FileSystemService;
import com.boostercloud.cli.services.process.CommandFailedException;
import com.boostercloud.cli.testing.FakeProcessService;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Port of {@code test/services/package-manager/{npm,pnpm,yarn,rush,live}.test.ts}: the Maven package managers and
 * their inference from the current directory.
 */
class PackageManagerTest {
  private final Path cwd = Path.of("/work/project");
  private final FakeProcessService process = new FakeProcessService(cwd, Map::of);

  private static FileSystemService directoryWith(String... entries) {
    return new FileSystemService() {
      @Override
      public List<String> readDirectoryContents(Path directoryPath) {
        return List.of(entries);
      }

      @Override
      public String readFileContents(Path filePath) {
        return "";
      }
    };
  }

  @Nested
  class Maven {
    private final PackageManagerService maven = MavenPackageManager.make(process, false);

    @Test
    void runsArbitraryScripts() {
      maven.runScript("script", List.of("arg1", "arg2"));
      assertThat(process.lines()).containsExactly("mvn -B -q script arg1 arg2");
    }

    @Test
    void buildsWithoutTests() {
      maven.build(List.of());
      maven.build(List.of("-Pnative"));
      assertThat(process.lines()).containsExactly("mvn -B -q package -DskipTests", "mvn -B -q package -DskipTests -Pnative");
    }

    @Test
    void runsInTheCurrentDirectoryByDefault() {
      maven.runScript("clean", List.of());
      assertThat(process.calls().getFirst().cwd()).isEqualTo(cwd);
    }

    @Test
    void canSetTheProjectRootProperly() {
      Path projectRoot = Path.of("/other/root");
      maven.setProjectRoot(projectRoot);
      maven.runScript("script", List.of());
      assertThat(process.calls().getFirst().cwd()).isEqualTo(projectRoot);
      assertThat(process.lines()).containsExactly("mvn -B -q script");
    }

    @Test
    void canInstallProductionDependencies() {
      maven.installProductionDependencies();
      assertThat(process.lines()).containsExactly("mvn -B -q dependency:resolve -DincludeScope=runtime");
    }

    @Test
    void canInstallAllDependencies() {
      maven.installAllDependencies();
      assertThat(process.lines()).containsExactly("mvn -B -q dependency:resolve");
    }

    @Test
    void wrapsFailuresInTaggedErrors() {
      process.failWhen(cmd -> cmd.contains("clean"), 1, "boom");
      process.failWhen(cmd -> cmd.contains("dependency:resolve"), 1, "offline");
      assertThatThrownBy(() -> maven.runScript("clean", List.of()))
          .isInstanceOf(RunScriptError.class)
          .satisfies(e -> assertThat(((RunScriptError) e).error()).isInstanceOf(CommandFailedException.class));
      assertThatThrownBy(maven::installAllDependencies).isInstanceOf(InstallDependenciesError.class);
      assertThatThrownBy(maven::installProductionDependencies).isInstanceOf(InstallDependenciesError.class);
    }

    @Test
    void usesTheWindowsExecutableOnWindows() {
      MavenPackageManager.make(process, true).runScript("clean", List.of());
      assertThat(process.lines()).containsExactly("mvn.cmd -B -q clean");
    }
  }

  @Nested
  class MavenWrapper {
    @Test
    void runsTheProjectWrapper() {
      MavenWrapperPackageManager.make(process, false).build(List.of());
      MavenWrapperPackageManager.make(process, true).build(List.of());
      assertThat(process.lines()).containsExactly("./mvnw -B -q package -DskipTests", "mvnw.cmd -B -q package -DskipTests");
    }
  }

  @Nested
  class Inference {
    @Test
    void infersTheWrapperWhenAnMvnwFileIsPresent() {
      LivePackageManager.infer(process, directoryWith("pom.xml", "mvnw", "src"), false).runScript("script", List.of());
      assertThat(process.lines()).containsExactly("./mvnw -B -q script");
    }

    @Test
    void infersTheWindowsWrapperOnWindows() {
      LivePackageManager.infer(process, directoryWith("mvnw", "mvnw.cmd"), true).runScript("script", List.of());
      assertThat(process.lines()).containsExactly("mvnw.cmd -B -q script");
    }

    @Test
    void infersMavenByDefault() {
      LivePackageManager.infer(process, directoryWith("pom.xml", "src"), false).runScript("script", List.of());
      assertThat(process.lines()).containsExactly("mvn -B -q script");
    }
  }

  @Test
  void scopedRunTrimsEmptyArguments() {
    var ref = new java.util.concurrent.atomic.AtomicReference<Path>();
    Common.makeScopedRun(process, List.of("mvn"), ref).run("install", null, List.of("", "x"));
    assertThat(process.lines()).containsExactly("mvn install x");
    assertThat(ref.get()).isEqualTo(cwd);
  }
}
