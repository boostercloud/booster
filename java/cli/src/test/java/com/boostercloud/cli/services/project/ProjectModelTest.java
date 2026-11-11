package com.boostercloud.cli.services.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.cli.testing.TestProjects;
import java.nio.file.Path;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The pom reader, the project layout and the static discovery of the configured environments. */
class ProjectModelTest {
  @TempDir Path dir;

  @Nested
  class Pom {
    @Test
    void readsCoordinatesPropertiesAndDependencies() {
      TestProjects.write(
          dir.resolve("pom.xml"),
          """
          <project xmlns="http://maven.apache.org/POM/4.0.0">
            <parent><groupId>org.parent</groupId><artifactId>p</artifactId><version>9.9.9</version></parent>
            <artifactId>app</artifactId>
            <properties><a>1</a><b>${a}.2</b><c>${project.version}</c></properties>
            <dependencies>
              <dependency><groupId>x</groupId><artifactId>y</artifactId><version>${b}</version><scope>test</scope></dependency>
            </dependencies>
            <build><sourceDirectory>src/java</sourceDirectory></build>
          </project>
          """);
      PomFile pom = PomFile.read(dir.resolve("pom.xml"));
      assertThat(pom.groupId()).isEqualTo("org.parent");
      assertThat(pom.version()).isEqualTo("9.9.9");
      assertThat(pom.artifactId()).isEqualTo("app");
      assertThat(pom.property("b")).contains("1.2");
      assertThat(pom.property("c")).contains("9.9.9");
      assertThat(pom.property("missing")).isEmpty();
      assertThat(pom.interpolate("${unknown}-${a}")).isEqualTo("${unknown}-1");
      assertThat(pom.dependencies()).containsExactly(new PomFile.Dependency("x", "y", "1.2", "test"));
      assertThat(pom.sourceDirectory()).contains("src/java");
    }

    @Test
    void rejectsDoctypesAndOtherDocuments() {
      TestProjects.write(dir.resolve("pom.xml"), "<!DOCTYPE project [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><project>&x;</project>");
      assertThatThrownBy(() -> PomFile.read(dir.resolve("pom.xml"))).hasMessageContaining("is not a valid XML file");
      TestProjects.write(dir.resolve("pom.xml"), "<settings/>");
      assertThatThrownBy(() -> PomFile.read(dir.resolve("pom.xml"))).hasMessageContaining("is not a Maven pom");
      assertThatThrownBy(() -> PomFile.read(dir.resolve("missing.xml"))).hasMessageStartingWith("Cannot find the file");
    }
  }

  @Nested
  class Layout {
    @Test
    void derivesLocationsFromThePom() {
      TestProjects.create(dir, "4.1.0");
      ProjectLayout layout = ProjectLayout.of(dir);
      assertThat(layout.mainClass()).isEqualTo("com.example.mock.Main");
      assertThat(layout.basePackage()).isEqualTo("com.example.mock");
      assertThat(layout.packageName("events")).isEqualTo("com.example.mock.events");
      assertThat(layout.packageDirectory("events")).isEqualTo(Path.of("src/main/java/com/example/mock/events"));
      assertThat(layout.mainSourceFile()).isEqualTo(TestProjects.source(dir, "Main"));
      assertThat(layout.runtimeClasspath())
          .containsExactly(dir.resolve("target/classes").toString(), dir.resolve("target/lib/*").toString());
    }

    @Test
    void honoursCustomSourceAndBuildDirectories() {
      TestProjects.write(
          dir.resolve("pom.xml"),
          "<project><artifactId>a</artifactId><properties><booster.mainClass>Main</booster.mainClass></properties>"
              + "<build><sourceDirectory>${project.basedir}/java</sourceDirectory><directory>out</directory></build></project>");
      ProjectLayout layout = ProjectLayout.of(dir);
      assertThat(layout.sourceDirectory()).isEqualTo(Path.of("java"));
      assertThat(layout.basePackage()).isEmpty();
      assertThat(layout.packageName("events")).isEqualTo("events");
      assertThat(layout.classesDirectory()).isEqualTo(dir.toAbsolutePath().normalize().resolve("out/classes"));
    }

    @Test
    void rejectsInvalidMainClasses() {
      TestProjects.write(
          dir.resolve("pom.xml"), "<project><properties><booster.mainClass>com.my-app.Main</booster.mainClass></properties></project>");
      assertThatThrownBy(() -> ProjectLayout.of(dir)).hasMessageContaining("is not a valid class name: com.my-app.Main");
    }
  }

  @Nested
  class Environments {
    @Test
    void findsLiteralEnvironmentsAndAppNames() {
      ConfiguredEnvironments environments =
          ConfiguredEnvironments.parse(
              """
              class Config implements BoosterConfigurator {
                public void configure() {
                  Booster.configure("local", config -> {
                    config.appName = "my-app";
                    config.providerPackage = "x";
                  });
                  com.boostercloud.framework.core.Booster.configure("production", (BoosterConfig config) -> {
                    config.appName = "my-app-prod";
                    if (config.appName == "x") {}
                  });
                  other.configure("ignored", c -> {});
                }
              }
              """);
      assertThat(environments.names()).containsExactly("local", "production");
      assertThat(environments.hasDynamicNames()).isFalse();
      assertThat(environments.appNames()).containsEntry("local", "my-app").containsEntry("production", "my-app-prod");
    }

    @Test
    void reportsNonLiteralNames() {
      ConfiguredEnvironments environments = ConfiguredEnvironments.parse("class C { void f() { Booster.configure(ENV, c -> {}); } }");
      assertThat(environments.names()).isEmpty();
      assertThat(environments.hasDynamicNames()).isTrue();
    }

    @Test
    void understandsStaticImports() {
      ConfiguredEnvironments environments =
          ConfiguredEnvironments.parse(
              "import static com.boostercloud.framework.core.Booster.configure;\nclass C { void f() { configure(\"dev\", c -> {}); } }");
      assertThat(environments.names()).containsExactly("dev");
    }

    @Test
    void ignoresCommentsAndStrings() {
      ConfiguredEnvironments environments =
          ConfiguredEnvironments.parse("// Booster.configure(\"a\", c -> {});\nclass C { String s = \"Booster.configure(\\\"b\\\"\"; }");
      assertThat(environments.names()).isEmpty();
    }

    @Test
    void scansEverySourceFile() {
      TestProjects.create(dir, "4.1.0");
      TestProjects.write(
          TestProjects.source(dir, "config/Staging"),
          "class Staging { void f() { Booster.configure(\"staging\", c -> { c.appName = \"st\"; }); } }");
      ConfiguredEnvironments environments = ConfiguredEnvironments.scan(dir.resolve("src/main/java"));
      assertThat(environments.names()).containsExactlyInAnyOrder("local", "production", "staging");
      assertThat(environments.appNames()).containsEntry("staging", "st").containsEntry("local", "mock-app");
    }
  }
}
