package com.boostercloud.rocket.fileuploads.azure.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.common.PromisesError;
import com.boostercloud.framework.common.RocketLoader;
import com.boostercloud.framework.provider.azure.infrastructure.ApplicationBuild;
import com.boostercloud.framework.provider.azure.infrastructure.ApplicationBuilder;
import com.boostercloud.framework.provider.azure.infrastructure.InfrastructureContext;
import com.boostercloud.framework.provider.azure.infrastructure.helper.DeploySources;
import com.boostercloud.framework.provider.azure.infrastructure.helper.Params;
import com.boostercloud.framework.provider.azure.infrastructure.rockets.InfrastructureRocket;
import com.boostercloud.framework.provider.azure.infrastructure.rockets.RocketZipResource;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.rocket.fileuploads.core.BoosterRocketFiles;
import com.boostercloud.rocket.fileuploads.types.RocketFilesUserConfiguration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Azure infrastructure rocket inside a real synthesis ({@code ApplicationBuilder.buildApplication()} of the Azure
 * infrastructure, nothing deployed): golden-file contract tests of the {@code cdk.tf.json} and of the rocket package
 * ({@code function.json} files). Regenerate the golden files with {@code -Dbooster.golden.update=true} and review the
 * diff.
 */
class AzureRocketSynthTest {

  private static final Path GOLDEN = Path.of("src/test/resources/golden");
  private static final RocketFilesUserConfiguration CLIENT =
      new RocketFilesUserConfiguration("ClientST", "rocketfiles", List.of("client1", "client2"));
  private static final RocketFilesUserConfiguration CMS =
      new RocketFilesUserConfiguration("cmsst", "rocketfiles", List.of("cms1", "cms2"));

  @TempDir
  Path root;

  @BeforeEach
  void setUp() {
    EnvironmentVariables.clearOverrides();
    Map<String, String> settings = new LinkedHashMap<>();
    settings.put("REGION", "East US");
    settings.put("BOOSTER_ENV", "production");
    settings.put("BOOSTER_APP_NAME", "");
    settings.put("AZURE_MAX_CONTAINER_THROUGHPUT", "1000");
    settings.put("AZURE_MAX_DATABASE_THROUGHPUT", "1000");
    settings.put("BOOSTER_AZURE_SERVICE_PLAN_BASIC", "false");
    settings.put("BOOSTER_USE_WAF", "false");
    settings.put("BOOSTER_AZURE_JAVA_VERSION", "21");
    settings.put("BOOSTER_APP_CLASS", "");
    settings.forEach(EnvironmentVariables::set);
  }

  @AfterEach
  void tearDown() {
    EnvironmentVariables.clearOverrides();
  }

  private BoosterConfig config(List<RocketFilesUserConfiguration> storages) {
    BoosterConfig config = new BoosterConfig("production");
    config.appName = "test";
    config.logger = Logger.NOOP;
    config.enableSubscriptions = false;
    config.env.put("CUSTOM_SETTING", "custom value");
    config.rockets = List.of(new BoosterRocketFiles(config, storages).rocketForAzure());
    return config;
  }

  private ApplicationBuild build(BoosterConfig config) {
    List<InfrastructureRocket> rockets = config.rockets.stream()
        .map(descriptor -> RocketLoader.loadRocket(descriptor, InfrastructureRocket.class))
        .toList();
    InfrastructureContext context = InfrastructureContext.builder()
        .projectRoot(root)
        .applicationClass("com.example.files.Main")
        .deploySources(sources(root))
        .httpTransport(request -> {
          throw new IllegalStateException("The synthesis must not call Azure: " + request.uri());
        })
        .configuration(Params.Configuration.of("app-id", "app-secret", "tenant-id", "subscription-id", "East US"))
        .build();
    return new ApplicationBuilder(config, rockets, context).buildApplication();
  }

  private static DeploySources sources(Path root) {
    try {
      Path classes = root.resolve("target/classes");
      Files.createDirectories(classes.resolve("com/example/files"));
      Files.write(classes.resolve("com/example/files/Main.class"), new byte[] {(byte) 0xCA, (byte) 0xFE, 1});
      Path lib = root.resolve("target/lib");
      Files.createDirectories(lib);
      Files.write(lib.resolve("booster-rocket-file-uploads-4.1.0.jar"), new byte[] {'P', 'K', 1});
      return new DeploySources(root, List.of(classes, lib.resolve("booster-rocket-file-uploads-4.1.0.jar")), List.of());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void assertGolden(String goldenName, String actual) throws IOException {
    Path golden = GOLDEN.resolve(goldenName);
    if (Boolean.getBoolean("booster.golden.update")) {
      Files.createDirectories(GOLDEN);
      Files.writeString(golden, actual, StandardCharsets.UTF_8);
    }
    assertThat(actual).isEqualTo(Files.readString(golden, StandardCharsets.UTF_8));
  }

  @Test
  void synthesizes_the_storages_the_rocket_function_app_and_the_roles() throws IOException {
    ApplicationBuild build = build(config(List.of(CLIENT, CMS)));

    String stack = Files.readString(build.stackDirectory().resolve("cdk.tf.json"), StandardCharsets.UTF_8)
        .replace(root.toAbsolutePath().normalize().toString(), "<PROJECT_ROOT>");
    assertGolden("file-uploads.cdk.tf.json", stack);
    assertThat(stack)
        .contains("\"clientst__serviceUri\": \"https://${azurerm_storage_account.stproductionclientstrfst.name}"
            + ".blob.core.windows.net\"")
        .contains("\\\"ROCKET_FUNCTION_APP_NAMES\\\" = \\\"testproductionrgfilesfunc\\\"")
        .contains("\\\"ROCKET_PACKAGE_MAPPING\\\" = \\\"rocket-file-uploads:testproductionrgfilesfunc\\\"");
  }

  @Test
  void packages_one_blob_trigger_function_per_storage_for_the_rocket_function_app() throws IOException {
    ApplicationBuild build = build(config(List.of(CLIENT, CMS)));

    assertThat(build.rocketsZipResources()).singleElement().satisfies(resource -> {
      assertThat(resource.functionAppName()).isEqualTo("testproductionrgfilesfunc");
      assertThat(resource.zip().fileName()).isEqualTo("rocket_testproductionrgfilesfunc.zip");
    });
    RocketZipResource resource = build.rocketsZipResources().getFirst();
    Map<String, String> entries = new TreeMap<>();
    try (ZipFile zip = new ZipFile(resource.zip().path().toFile())) {
      for (ZipEntry entry : zip.stream().toList()) {
        if (entry.getName().endsWith(".json")) {
          entries.put(entry.getName(), new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8));
        } else {
          entries.put(entry.getName(), "");
        }
      }
    }
    StringBuilder listing = new StringBuilder();
    entries.forEach((name, content) -> listing.append("=== ").append(name).append('\n').append(content)
        .append(content.isEmpty() ? "" : "\n"));
    assertGolden("file-uploads-rocket-package.txt", listing.toString());
    assertThat(entries).containsKeys("fileupload_clientst/function.json", "fileupload_cmsst/function.json",
        "host.json", "test.jar");
  }

  @Test
  void the_rocket_is_loaded_from_the_descriptor_package() {
    BoosterConfig config = config(List.of(CLIENT));
    InfrastructureRocket rocket = RocketLoader.loadRocket(config.rockets.getFirst(), InfrastructureRocket.class);

    assertThat(rocket).isInstanceOf(AzureRocketFiles.class);
    assertThat(rocket.defines(InfrastructureRocket.Hook.MOUNT_FUNCTIONS_V4)).isTrue();
    assertThat(rocket.defines(InfrastructureRocket.Hook.GET_FUNCTION_APP_NAME)).isTrue();
    assertThat(rocket.defines(InfrastructureRocket.Hook.MOUNT_CODE)).isFalse();
  }

  @Test
  void a_storage_name_too_long_for_its_construct_id_fails_the_synthesis() {
    BoosterConfig config = config(List.of(new RocketFilesUserConfiguration("abcdefghijklmnopqrstu", "c",
        List.of("d"))));

    assertThatThrownBy(() -> build(config)).isInstanceOf(PromisesError.class)
        .hasMessageContaining("Error. Suffix could not be greater than 24");
  }

  @Test
  void a_container_name_too_long_for_its_construct_id_fails_the_synthesis() {
    BoosterConfig config = config(List.of(new RocketFilesUserConfiguration("st", "abcdefghijklmnopqrstu",
        List.of("d"))));

    assertThatThrownBy(() -> build(config)).hasMessageContaining("Error. Suffix could not be greater than 24");
  }

  @Test
  void invalid_names_fail_when_the_rocket_package_is_built() {
    BoosterConfig config = config(List.of(new RocketFilesUserConfiguration("st", "Container", List.of("d"))));

    assertThatThrownBy(() -> build(config))
        .hasMessageContaining("Invalid containerName: \"Container\". Must match /^[a-z0-9_-]+$/.");
  }
}
