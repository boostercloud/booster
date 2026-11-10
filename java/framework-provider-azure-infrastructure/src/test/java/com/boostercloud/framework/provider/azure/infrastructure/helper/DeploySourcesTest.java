package com.boostercloud.framework.provider.azure.infrastructure.helper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeploySourcesTest {

  @TempDir Path root;

  @Test
  void reads_directories_jars_and_wildcards_from_a_class_path() throws IOException {
    Path classes = Files.createDirectories(root.resolve("target/classes"));
    Path lib = Files.createDirectories(root.resolve("target/lib"));
    Files.write(lib.resolve("b.jar"), new byte[] {1});
    Files.write(lib.resolve("a.jar"), new byte[] {1});
    Files.writeString(lib.resolve("notes.txt"), "x");
    Path single = Files.write(root.resolve("single.jar"), new byte[] {1});
    String classpath = String.join(File.pathSeparator, classes.toString(), lib + File.separator + "*",
        single.toString(), root.resolve("missing.jar").toString(), "");
    DeploySources sources = DeploySources.fromClasspath(root, classpath, List.of("assets"));
    assertThat(sources.classDirectories()).containsExactly(classes.toAbsolutePath().normalize());
    assertThat(sources.libraries()).containsExactly(lib.resolve("a.jar"), lib.resolve("b.jar"),
        single.toAbsolutePath().normalize());
    assertThat(sources.assets()).containsExactly(Path.of("assets"));
  }

  @Test
  void the_runtime_sources_are_the_class_path_of_this_jvm() {
    DeploySources sources = DeploySources.fromRuntime(root, null);
    assertThat(sources.classpath()).isNotEmpty();
    assertThat(sources.assets()).isEmpty();
  }
}
