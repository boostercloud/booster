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
import com.boostercloud.framework.types.BoosterJson;
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
    return build(config, root);
  }

  private ApplicationBuild build(BoosterConfig config, Path root) {
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

  // --- parity with what the TypeScript rocket produces (src/test/resources/typescript) ----------------------------

  @SuppressWarnings("unchecked")
  private static Map<String, Map<String, Object>> resources(ApplicationBuild build) throws IOException {
    Map<String, Object> stack = (Map<String, Object>) BoosterJson.parse(
        Files.readString(build.stackDirectory().resolve("cdk.tf.json"), StandardCharsets.UTF_8));
    Map<String, Map<String, Object>> byAddress = new TreeMap<>();
    ((Map<String, Object>) stack.get("resource")).forEach((type, byId) ->
        ((Map<String, Object>) byId).forEach((id, resource) -> {
          Map<String, Object> attributes = new LinkedHashMap<>((Map<String, Object>) resource);
          attributes.remove("//");
          byAddress.put(type + "." + id, attributes);
        }));
    return byAddress;
  }

  /**
   * The documented differences, applied to a Java resource so it can be compared with the TypeScript one: the
   * identity connection keys are literal account names (TypeScript: the name token), the rocket's function app runs
   * the Java worker and gets {@code BOOSTER_APP_CLASS}.
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> asTypeScriptWouldWriteIt(Map<String, Map<String, Object>> javaResources,
      String address, Map<String, Object> attributes) {
    Map<String, Object> result = new LinkedHashMap<>(attributes);
    if (address.equals("azurerm_windows_function_app.testproductionrffunc")) {
      Map<String, Object> settings = new LinkedHashMap<>();
      ((Map<String, Object>) attributes.get("app_settings")).forEach((key, value) -> {
        if (key.endsWith("__serviceUri")) {
          String accountName = key.substring(0, key.length() - "__serviceUri".length());
          String account = javaResources.entrySet().stream()
              .filter(entry -> entry.getKey().startsWith("azurerm_storage_account.")
                  && accountName.equals(entry.getValue().get("name")))
              .map(Map.Entry::getKey).findFirst().orElseThrow();
          assertThat(value).isEqualTo("https://${" + account + ".name}.blob.core.windows.net");
          settings.put("${" + account + ".name}__serviceUri", value);
        } else if (key.equals("BOOSTER_APP_CLASS")) {
          assertThat(value).isEqualTo("com.example.files.Main");
        } else {
          settings.put(key, value);
        }
      });
      result.put("app_settings", settings);
      assertThat(attributes.get("site_config")).isEqualTo(Map.of("application_stack", Map.of("java_version", "21")));
      result.put("site_config", Map.of("application_stack", Map.of("node_version", "~22")));
    }
    return result;
  }

  @Test
  @SuppressWarnings("unchecked")
  void the_terraform_is_what_the_typescript_synthesis_writes_attribute_by_attribute() throws IOException {
    BoosterConfig withoutRocket = config(List.of(CLIENT, CMS));
    withoutRocket.rockets = List.of();
    Map<String, Map<String, Object>> before = resources(build(withoutRocket, root.resolve("without")));
    Map<String, Map<String, Object>> after = resources(build(config(List.of(CLIENT, CMS)), root.resolve("with")));
    Map<String, Object> expected = (Map<String, Object>) BoosterJson.parse(Files.readString(
        Path.of("src/test/resources/typescript/file-uploads-resources.expected.json"), StandardCharsets.UTF_8));

    // added resources: those of the TypeScript rocket, compared attribute by attribute (opaque ids by content)
    List<Map<String, Object>> added = new java.util.ArrayList<>();
    after.forEach((address, attributes) -> {
      if (!before.containsKey(address)) {
        String type = address.substring(0, address.indexOf('.'));
        String id = address.substring(address.indexOf('.') + 1);
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("type", type);
        resource.put("id", type.equals("azurerm_storage_container") || type.equals("azurerm_role_assignment")
            ? null : id);
        resource.put("attributes", new TreeMap<>(asTypeScriptWouldWriteIt(after, address, attributes)));
        added.add(resource);
      }
    });
    List<Map<String, Object>> expectedAdded = ((List<Map<String, Object>>) expected.get("resources")).stream()
        .map(resource -> {
          Map<String, Object> copy = new LinkedHashMap<>();
          copy.put("type", resource.get("type"));
          copy.put("id", resource.get("id"));
          copy.put("attributes", new TreeMap<>((Map<String, Object>) resource.get("attributes")));
          return copy;
        })
        .toList();
    assertThat(added).containsExactlyInAnyOrderElementsOf(expectedAdded);

    // changed resources: the application's function app gets a system identity (and, from the framework, the rocket
    // function app names); nothing else of the framework's stack changes
    for (Map<String, Object> change : (List<Map<String, Object>>) expected.get("changedResources")) {
      String address = change.get("type") + "." + change.get("id");
      ((Map<String, Object>) change.get("attributes")).forEach((name, value) ->
          assertThat(after.get(address)).containsEntry(name, value));
    }
    before.forEach((address, attributes) -> {
      Map<String, Object> now = new LinkedHashMap<>(after.get(address));
      if (address.equals("azurerm_windows_function_app.testproductionfunc")) {
        now.remove("identity");
        assertThat(String.valueOf(now.remove("app_settings"))).contains(
            "\"ROCKET_FUNCTION_APP_NAMES\" = \"testproductionrgfilesfunc\"",
            "\"ROCKET_PACKAGE_MAPPING\" = \"rocket-file-uploads:testproductionrgfilesfunc\"");
        Map<String, Object> was = new LinkedHashMap<>(attributes);
        was.remove("identity");
        was.remove("app_settings");
        assertThat(now).as(address).isEqualTo(was);
      } else {
        assertThat(now).as(address).isEqualTo(attributes);
      }
    });
  }

  @Test
  @SuppressWarnings("unchecked")
  void the_blob_trigger_functions_are_those_the_typescript_functions_js_registers() throws IOException {
    // functions.js is the output of the TypeScript generator itself (typescript/generate.mjs)
    String functionsJs = Files.readString(Path.of("src/test/resources/typescript/functions.js"), StandardCharsets.UTF_8);
    java.util.regex.Matcher registration = java.util.regex.Pattern.compile(
        "app\\.storageBlob\\('([^']*)', \\{\\n  path: '([^']*)',\\n  connection: '([^']*)',\\n  handler: async "
            + "\\(blob, context\\) => \\{\\n    return await boosterRocketDispatcher\\(\\{ blob, context \\}\\)\\n  \\}\\n"
            + "\\}\\)").matcher(functionsJs);
    Map<String, List<String>> typescript = new TreeMap<>();
    while (registration.find()) {
      typescript.put(registration.group(1), List.of(registration.group(2), registration.group(3)));
    }
    assertThat(typescript).hasSize(2);

    ApplicationBuild build = build(config(List.of(CLIENT, CMS)));
    Map<String, List<String>> java = new TreeMap<>();
    try (ZipFile zip = new ZipFile(build.rocketsZipResources().getFirst().zip().path().toFile())) {
      for (ZipEntry entry : zip.stream().toList()) {
        if (entry.getName().endsWith("/function.json")) {
          Map<String, Object> function = (Map<String, Object>) BoosterJson.parse(
              new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8));
          Map<String, Object> binding = ((List<Map<String, Object>>) function.get("bindings")).getFirst();
          // app.storageBlob = a blobTrigger input binding; the handler receives the content as its first argument
          assertThat(binding).containsEntry("type", "blobTrigger").containsEntry("direction", "in")
              .containsEntry("name", "blob").containsEntry("dataType", "binary");
          assertThat(function).containsEntry("entryPoint",
              "com.boostercloud.framework.provider.azure.functions.BoosterAzureFunctions.rocketBlob");
          java.put(entry.getName().substring(0, entry.getName().indexOf('/')),
              List.of((String) binding.get("path"), (String) binding.get("connection")));
        }
      }
    }
    assertThat(java).isEqualTo(typescript);
  }

  @Test
  @SuppressWarnings("unchecked")
  void names_are_validated_with_the_typescript_messages() throws IOException {
    List<Map<String, Object>> cases = (List<Map<String, Object>>) BoosterJson.parse(Files.readString(
        Path.of("src/test/resources/typescript/function-name-errors.json"), StandardCharsets.UTF_8));
    BoosterConfig config = new BoosterConfig("production");
    config.appName = "test";
    assertThat(cases).isNotEmpty().allSatisfy(testCase -> {
      String containerName = (String) testCase.get("containerName");
      String storageName = (String) testCase.get("storageName");
      if (testCase.get("error") == null) {
        assertThat(com.boostercloud.rocket.fileuploads.azure.infrastructure.functions.RocketFilesFileUploadedFunction
            .generateFunctionDefinition(config, containerName, storageName).name()).isEqualTo("fileupload_" + storageName);
      } else {
        assertThatThrownBy(() -> com.boostercloud.rocket.fileuploads.azure.infrastructure.functions
            .RocketFilesFileUploadedFunction.generateFunctionDefinition(config, containerName, storageName))
            .hasMessage((String) testCase.get("error"));
      }
    });
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
  @SuppressWarnings("unchecked")
  void storages_whose_names_end_alike_get_distinct_construct_ids() throws IOException {
    // toTerraformName keeps the end of long names: "<storage>filesfunc" + rfrlb keeps the last 19 characters, so these
    // two storages would share their role assignment ids (TypeScript's token-based ids are always distinct)
    ApplicationBuild build = build(config(List.of(
        new RocketFilesUserConfiguration("firstcompanyfiles", "uploads", List.of("d")),
        new RocketFilesUserConfiguration("secondcompanyfiles", "uploads", List.of("d")),
        new RocketFilesUserConfiguration("acompanyuploadsfiles", "c", List.of("d")),
        new RocketFilesUserConfiguration("bcompanyuploadsfiles", "c", List.of("d")))));

    Map<String, Object> resources = (Map<String, Object>) ((Map<String, Object>) BoosterJson.parse(
        Files.readString(build.stackDirectory().resolve("cdk.tf.json")))).get("resource");
    Map<String, Object> roles = (Map<String, Object>) resources.get("azurerm_role_assignment");
    Map<String, Object> containers = (Map<String, Object>) resources.get("azurerm_storage_container");
    assertThat(roles).hasSize(16);
    assertThat(containers).hasSize(4);
    // every storage account gets the two roles for each function app, whatever the ids
    Map<String, Integer> rolesPerScope = new TreeMap<>();
    roles.values().forEach(role -> rolesPerScope.merge((String) ((Map<String, Object>) role).get("scope"), 1,
        Integer::sum));
    assertThat(rolesPerScope).hasSize(4).allSatisfy((scope, count) -> assertThat(count).isEqualTo(4));
    assertThat(roles).containsKeys("mpanyfilesfilesfuncrfrlb", "panyfilesfilesfunc2rfrlb",
        "loadsfilesfilesfuncrfrlb", "oadsfilesfilesfunc2rfrlb");
    // the container suffix rfsbc keeps the last 19 characters of these 20-character storage names
    assertThat(containers).containsKeys("companyuploadsfilesrfsbc", "ompanyuploadsfiles2rfsbc");
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
