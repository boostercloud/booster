package com.boostercloud.framework.provider.azure.infrastructure.helper;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.provider.azure.functions.BoosterAzureFunctions;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The offline check that the function apps' Java runtime ({@code application_stack.java_version}) can load the class
 * files of the package: a Java {@code N} worker loads class files up to version {@code N + 44}.
 */
class JavaRuntimeCompatibilityTest {

  @TempDir Path root;

  private static byte[] classFile(int major) {
    return new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0, (byte) (major >> 8), (byte) major};
  }

  private Path classes(String name, int major) throws IOException {
    Path directory = root.resolve("classes");
    Path file = directory.resolve(name);
    Files.createDirectories(file.getParent());
    Files.write(file, classFile(major));
    return directory;
  }

  private Path jar(String name, Object... entriesAndMajors) throws IOException {
    Path jar = root.resolve(name);
    try (OutputStream output = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(output)) {
      for (int i = 0; i < entriesAndMajors.length; i += 2) {
        zip.putNextEntry(new ZipEntry((String) entriesAndMajors[i]));
        zip.write(classFile((Integer) entriesAndMajors[i + 1]));
        zip.closeEntry();
      }
    }
    return jar;
  }

  @Test
  void the_booster_azure_functions_cannot_be_loaded_by_the_default_java_21_runtime() throws URISyntaxException {
    Path provider = Path.of(BoosterAzureFunctions.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    DeploySources sources = new DeploySources(root, List.of(provider), List.of());

    assertThat(JavaRuntimeCompatibility.incompatibility(sources, "21"))
        .hasValueSatisfying(message -> assertThat(message)
            .startsWith("The function apps run Java 21 (BOOSTER_AZURE_JAVA_VERSION), which loads class files up to"
                + " version 65, but ")
            .contains("has class file version 71 (Java 27)"));
    assertThat(JavaRuntimeCompatibility.incompatibility(sources, "27")).isEmpty();
  }

  @Test
  void reads_class_directories_and_jars_and_names_the_first_class_too_new() throws IOException {
    Path classes = classes("com/example/Main.class", 65);
    Path library = jar("lib.jar", "a/A.class", 61, "b/B.class", 71);

    DeploySources sources = new DeploySources(root, List.of(classes, library), List.of());

    assertThat(JavaRuntimeCompatibility.incompatibility(sources, "21")).hasValueSatisfying(message -> assertThat(message)
        .contains(library + "!b/B.class has class file version 71 (Java 27)")
        .contains("UnsupportedClassVersionError")
        .contains("BOOSTER_AZURE_SKIP_JAVA_VERSION_CHECK=true"));
    assertThat(JavaRuntimeCompatibility.incompatibility(sources, "27")).isEmpty();
    assertThat(JavaRuntimeCompatibility.incompatibility(
        new DeploySources(root, List.of(classes), List.of()), "17")).hasValueSatisfying(message -> assertThat(message)
        .contains(classes.resolve("com/example/Main.class") + " has class file version 65 (Java 21)"));
  }

  @Test
  void ignores_versioned_entries_module_descriptors_and_files_that_are_not_class_files() throws IOException {
    Path library = jar("mr.jar", "META-INF/versions/27/x/X.class", 71, "module-info.class", 71, "x/X.class", 52);
    Path classes = root.resolve("fake");
    Files.createDirectories(classes);
    Files.write(classes.resolve("Fake.class"), new byte[] {(byte) 0xCA, (byte) 0xFE, 1});
    Path notAZip = root.resolve("broken.jar");
    Files.write(notAZip, new byte[] {'P', 'K', 1});

    DeploySources sources = new DeploySources(root, List.of(classes, library, notAZip), List.of());

    assertThat(JavaRuntimeCompatibility.incompatibility(sources, "1.8")).isEmpty();
    assertThat(JavaRuntimeCompatibility.incompatibility(sources, "8")).isEmpty();
  }

  @Test
  void maps_java_versions_to_the_newest_class_file_version_they_load() throws IOException {
    assertThat(JavaRuntimeCompatibility.maxClassFileVersion("21")).hasValue(65);
    assertThat(JavaRuntimeCompatibility.maxClassFileVersion("17")).hasValue(61);
    assertThat(JavaRuntimeCompatibility.maxClassFileVersion("11")).hasValue(55);
    assertThat(JavaRuntimeCompatibility.maxClassFileVersion("1.8")).hasValue(52);
    assertThat(JavaRuntimeCompatibility.maxClassFileVersion(" 25 ")).hasValue(69);
    assertThat(JavaRuntimeCompatibility.maxClassFileVersion("latest")).isEmpty();
    // a version the check cannot read is left to Terraform and Azure to judge
    DeploySources sources = new DeploySources(root, List.of(classes("A.class", 71)), List.of());
    assertThat(JavaRuntimeCompatibility.incompatibility(sources, "latest")).isEmpty();
  }
}
