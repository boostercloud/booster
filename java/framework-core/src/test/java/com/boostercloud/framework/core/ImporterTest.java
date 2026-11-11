package com.boostercloud.framework.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.core.fixtures.app.App;
import com.boostercloud.framework.core.fixtures.app.commands.AppHealth;
import com.boostercloud.framework.core.fixtures.app.commands.CreateCart;
import com.boostercloud.framework.core.fixtures.app.config.AppConfig;
import com.boostercloud.framework.core.fixtures.app.domain.AbstractConfigurator;
import com.boostercloud.framework.core.fixtures.app.domain.Cart;
import com.boostercloud.framework.core.fixtures.app.domain.CartCreated;
import com.boostercloud.framework.core.fixtures.app.domain.CartReadModel;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Port of {@code test/importer.test.ts}: the scan order (the TypeScript directory walk), the skipped classes, the
 * configurators-first rule, the {@code require} cache, and jars.
 */
class ImporterTest extends BoosterTestSupport {

  @BeforeEach
  void setUp() {
    AppConfig.runs = 0;
  }

  @Test
  void findsTheClassesOfThePackageTreeInPathOrderSkippingAnonymousClasses() {
    assertThat(Importer.getImportClasses(App.class)).containsExactly(App.class, AppHealth.class, CreateCart.class,
        AppConfig.class, AbstractConfigurator.class, Cart.class, Cart.Item.class, CartCreated.class, CartReadModel.class);
  }

  @Test
  void runsTheConfiguratorsBeforeRegisteringTheAnnotatedClasses() {
    Importer.importUserProjectFiles(App.class);

    assertThat(AppConfig.runs).isEqualTo(1);
    assertThat(Booster.config().appName).isEqualTo("fixture-app");
    assertThat(Booster.configuredEnvironments()).containsExactly("test", "production");
    assertThat(Booster.config().commandHandlers).containsOnlyKeys("CreateCart");
    assertThat(Booster.config().entities).containsOnlyKeys("Cart");
    assertThat(Booster.config().reducers).containsOnlyKeys("CartCreated");
    assertThat(Booster.config().events).containsOnlyKeys("CartCreated");
    assertThat(Booster.config().readModels).containsOnlyKeys("CartReadModel");
    assertThat(Booster.config().projections.get("Cart")).hasSize(1);
    // The health sensor (sorted before config/) saw the settings of the configurator.
    assertThat(Booster.config().userHealthIndicators.get("booster").healthIndicatorConfiguration().enabled).isTrue();
  }

  @Test
  void importsEachClassOnce() {
    Importer.importUserProjectFiles(App.class);
    Importer.importUserProjectFiles(App.class);

    assertThat(AppConfig.runs).isEqualTo(1);
    assertThat(Booster.config().commandHandlers).containsOnlyKeys("CreateCart");
  }

  @Test
  void scansJars(@TempDir Path temp) throws Exception {
    Path sources = temp.resolve("src/com/example/jarapp");
    Files.createDirectories(sources.resolve("commands"));
    Files.writeString(sources.resolve("JarApp.java"), "package com.example.jarapp; public class JarApp {}");
    Files.writeString(sources.resolve("commands/Ping.java"), """
        package com.example.jarapp.commands;
        import com.boostercloud.framework.core.decorators.*;
        import com.boostercloud.framework.types.concepts.Register;
        @Command(authorize = All.class)
        public record Ping(String message) {
          public static void handle(Ping command, Register register) {}
        }
        """);
    Path classes = temp.resolve("classes");
    compile(temp.resolve("src"), classes);
    Path jar = temp.resolve("app.jar");
    jar(classes, jar);

    try (URLClassLoader loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, getClass().getClassLoader())) {
      Class<?> root = loader.loadClass("com.example.jarapp.JarApp");
      assertThat(Importer.getImportClasses(root)).extracting(Class::getName)
          .containsExactly("com.example.jarapp.JarApp", "com.example.jarapp.commands.Ping");

      Importer.importUserProjectFiles(root);
      assertThat(Booster.config().commandHandlers).containsOnlyKeys("Ping");
      assertThat(Booster.projectRootPath(root)).isEqualTo(temp.toAbsolutePath().normalize() + java.io.File.separator);
    }
  }

  private static void compile(Path sourceRoot, Path output) throws IOException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    List<String> files;
    try (Stream<Path> walk = Files.walk(sourceRoot)) {
      files = walk.filter(path -> path.toString().endsWith(".java")).map(Path::toString).toList();
    }
    Files.createDirectories(output);
    List<String> arguments = new java.util.ArrayList<>(List.of("-d", output.toString(), "-classpath",
        System.getProperty("java.class.path"), "-proc:none"));
    arguments.addAll(files);
    int result = compiler.run(null, null, null, arguments.toArray(String[]::new));
    assertThat(result).isZero();
  }

  private static void jar(Path classes, Path jar) throws IOException {
    try (OutputStream file = Files.newOutputStream(jar); JarOutputStream out = new JarOutputStream(file);
        Stream<Path> walk = Files.walk(classes)) {
      for (Path path : walk.filter(Files::isRegularFile).toList()) {
        out.putNextEntry(new JarEntry(classes.relativize(path).toString().replace('\\', '/')));
        out.write(Files.readAllBytes(path));
        out.closeEntry();
      }
      out.putNextEntry(new JarEntry("META-INF/notes.txt"));
      out.write("ignored".getBytes(StandardCharsets.UTF_8));
      out.closeEntry();
    }
  }
}
