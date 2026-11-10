package com.boostercloud.framework.provider.azure.infrastructure.helper;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.provider.azure.functions.BoosterAzureFunctions;
import com.boostercloud.framework.provider.azure.infrastructure.InfrastructureContext;
import com.boostercloud.framework.provider.azure.infrastructure.azure.HttpTransport;
import com.boostercloud.framework.provider.azure.infrastructure.support.FakeHttpTransport;
import com.boostercloud.framework.provider.azure.infrastructure.support.Fixtures;
import com.boostercloud.framework.provider.azure.infrastructure.support.RecordingCommandRunner;
import com.boostercloud.framework.provider.azure.infrastructure.support.RecordingSleeper;
import com.boostercloud.framework.provider.azure.infrastructure.support.TestEnvironment;
import com.boostercloud.framework.provider.azure.infrastructure.types.Binding;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionConfig;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionDefinition;
import com.boostercloud.framework.provider.azure.infrastructure.types.ZipResource;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

/** Contract tests of the function app packages: entries, {@code function.json}/{@code host.json} texts, upload. */
@ExtendWith(TestEnvironment.class)
class FunctionZipTest {

  private static final String DEFAULT_HOST_JSON = """
      {
        "version": "2.0",
        "extensionBundle": {
          "id": "Microsoft.Azure.Functions.ExtensionBundle",
          "version": "[4.*, 5.0.0)"
        }
      }""";

  @TempDir Path root;

  private FakeHttpTransport http;
  private InfrastructureContext context;

  @BeforeEach
  void setUp() {
    http = new FakeHttpTransport();
    context = Fixtures.context(root, new RecordingCommandRunner(), http, new RecordingSleeper());
  }

  private static Map<String, byte[]> entries(Path zip) throws IOException {
    Map<String, byte[]> entries = new LinkedHashMap<>();
    try (ZipFile file = new ZipFile(zip.toFile())) {
      for (ZipEntry entry : file.stream().toList()) {
        entries.put(entry.getName(), file.getInputStream(entry).readAllBytes());
      }
    }
    return entries;
  }

  private static List<String> jarEntries(byte[] jar) throws IOException {
    List<String> names = new ArrayList<>();
    try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(jar))) {
      for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
        names.add(entry.getName());
      }
    }
    return names;
  }

  private static String text(byte[] bytes) {
    return new String(bytes, StandardCharsets.UTF_8);
  }

  @Test
  void the_default_host_json_is_the_typescript_one() {
    assertThat(FunctionZip.DEFAULT_HOST_JSON).isEqualTo(DEFAULT_HOST_JSON);
  }

  @Test
  void builds_the_main_package_with_the_java_worker_layout() throws IOException {
    ZipResource zip = new FunctionZip(Fixtures.config(), context).copyZip("functionApp.zip", null);
    assertThat(zip).isEqualTo(new ZipResource(root.toAbsolutePath().normalize().resolve("functionApp.zip"),
        "functionApp.zip", "functions"));
    Map<String, byte[]> entries = entries(zip.path());
    assertThat(entries.keySet()).containsExactly(
        "my-store.jar",
        "lib/booster-framework-core-4.1.0.jar",
        "lib/booster-framework-provider-azure-4.1.0.jar",
        "assets/img/logo.txt",
        "graphql/function.json",
        "sensor-health/function.json",
        "eventHandler/function.json",
        "connect/function.json",
        "disconnect/function.json",
        "messages/function.json",
        "CartReadModel-subscriptions-notifier/function.json",
        "ProductReadModel-subscriptions-notifier/function.json",
        "scheduleFunction-CheckCart/function.json",
        "host.json");
    assertThat(jarEntries(entries.get("my-store.jar"))).containsExactly("META-INF/", "META-INF/MANIFEST.MF",
        "application.properties", "com/example/store/Cart.class", "com/example/store/Main.class");
    assertThat(entries.get("lib/booster-framework-core-4.1.0.jar")).containsExactly('P', 'K', 1);
    assertThat(text(entries.get("host.json"))).isEqualTo(DEFAULT_HOST_JSON);
    Map<String, Object> graphql = BoosterJson.parseObject(text(entries.get("graphql/function.json")));
    assertThat(graphql.get("scriptFile")).isEqualTo("../my-store.jar");
    assertThat(graphql.get("entryPoint")).isEqualTo(BoosterAzureFunctions.GRAPHQL_ENTRY_POINT);
    assertThat(text(entries.get("scheduleFunction-CheckCart/function.json"))).isEqualTo("""
        {
          "bindings": [
            {
              "type": "timerTrigger",
              "direction": "in",
              "name": "timer",
              "schedule": "0 0/5 * * * *"
            }
          ],
          "scriptFile": "../my-store.jar",
          "entryPoint": "com.boostercloud.framework.provider.azure.functions.BoosterAzureFunctions.scheduledCommand"
        }""");
  }

  @Test
  void packages_are_reproducible() throws IOException {
    FunctionZip functionZip = new FunctionZip(Fixtures.config(), context);
    byte[] first = Files.readAllBytes(functionZip.copyZip("a.zip", null).path());
    byte[] second = Files.readAllBytes(functionZip.copyZip("b.zip", null).path());
    assertThat(first).isEqualTo(second);
  }

  @Test
  void builds_the_consumer_package() throws IOException {
    ZipResource zip = new FunctionZip(Fixtures.streamingConfig(), context)
        .copyConsumerZip("consumerFunctionApp.zip", null);
    assertThat(zip.name()).isEqualTo("consumer-functions");
    assertThat(entries(zip.path()).keySet()).containsExactly("my-store.jar", "lib/booster-framework-core-4.1.0.jar",
        "lib/booster-framework-provider-azure-4.1.0.jar", "assets/img/logo.txt", "eventConsumer/function.json",
        "host.json");
    ZipResource withoutStreaming = new FunctionZip(Fixtures.config(), context).copyConsumerZip("c.zip", null);
    assertThat(entries(withoutStreaming.path()).keySet()).doesNotContain("eventConsumer/function.json");
  }

  @Test
  void builds_the_web_pub_sub_bootstrap_package_with_a_loadable_class() throws Exception {
    Files.createDirectories(root.resolve(".deploy-base/extra"));
    Files.writeString(root.resolve(".deploy-base/extra/file.txt"), "extra");
    Files.writeString(root.resolve(".deploy-base/host.json"), "ignored");
    Path zip = new FunctionZip(Fixtures.config(), context).copyBaseZip();
    assertThat(zip).isEqualTo(root.toAbsolutePath().normalize().resolve("baseWebPubSubBinding.zip"));
    Map<String, byte[]> entries = entries(zip);
    assertThat(entries.keySet()).containsExactly("extra/file.txt", "booster-base.jar",
        "baseWebPubSubBinding/function.json", "host.json");
    assertThat(text(entries.get("host.json"))).isEqualTo(DEFAULT_HOST_JSON);
    assertThat(text(entries.get("baseWebPubSubBinding/function.json"))).isEqualTo("""
        {
          "bindings": [
            {
              "type": "webPubSubTrigger",
              "direction": "in",
              "name": "request",
              "hub": "booster",
              "eventType": "user",
              "eventName": "message",
              "connection": "WebPubSubConnectionString"
            }
          ],
          "scriptFile": "../booster-base.jar",
          "entryPoint": "com.boostercloud.framework.provider.azure.infrastructure.base.BaseWebPubSubBinding.run"
        }""");

    Path jar = root.resolve("booster-base.jar");
    Files.write(jar, entries.get("booster-base.jar"));
    byte[] classFile = BaseWebPubSubBinding.classFile();
    assertThat(((classFile[6] & 0xff) << 8) | (classFile[7] & 0xff)).isEqualTo(52);
    try (URLClassLoader loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
      Class<?> type = loader.loadClass(BaseWebPubSubBinding.CLASS_NAME);
      Object instance = type.getConstructor().newInstance();
      Method run = type.getMethod("run", String.class);
      assertThat(run.invoke(instance, "{}")).isEqualTo("{\"data\":\"ok\"}");
    }
  }

  @Test
  void the_bootstrap_package_has_no_function_without_subscriptions() throws IOException {
    Path zip = new FunctionZip(Fixtures.noSubscriptionsConfig(), context).copyBaseZip();
    assertThat(entries(zip).keySet()).containsExactly("host.json");
  }

  private static List<FunctionDefinition> rocketFunctions() {
    return List.of(new FunctionDefinition("test", new FunctionConfig(
        List.of(Binding.httpTrigger("request", List.of("POST", "GET"), "anonymous", "test"), Binding.httpReturn()),
        "../my-store.jar", BoosterAzureFunctions.ROCKET_HTTP_ENTRY_POINT)));
  }

  @Test
  void builds_rocket_packages_with_the_default_or_a_custom_host_json() throws IOException {
    FunctionZip functionZip = new FunctionZip(Fixtures.config(), context);
    ZipResource zip = functionZip.copyZipV4ForRocket(rocketFunctions(), "rocket_app.zip", null);
    assertThat(zip.name()).isEqualTo("functions");
    Map<String, byte[]> entries = entries(zip.path());
    assertThat(entries.keySet()).containsExactly("my-store.jar", "lib/booster-framework-core-4.1.0.jar",
        "lib/booster-framework-provider-azure-4.1.0.jar", "assets/img/logo.txt", "test/function.json", "host.json");
    assertThat(text(entries.get("host.json"))).isEqualTo(DEFAULT_HOST_JSON);

    Path custom = root.resolve("rocket-host.json");
    Files.writeString(custom, "{\"version\":\"2.0\",\"extensions\":{}}");
    Map<String, byte[]> customEntries = entries(functionZip.copyZipV4ForRocket(rocketFunctions(), "r2.zip", custom)
        .path());
    assertThat(text(customEntries.get("host.json"))).isEqualTo("{\"version\":\"2.0\",\"extensions\":{}}");

    Path empty = root.resolve("empty-host.json");
    Files.writeString(empty, "");
    assertThat(entries(functionZip.copyZipV4ForRocket(rocketFunctions(), "r3.zip", empty).path()))
        .doesNotContainKey("host.json");
  }

  @Test
  @SuppressWarnings("deprecation")
  void builds_legacy_packages_keeping_every_source_file() throws IOException {
    FunctionZip functionZip = new FunctionZip(Fixtures.config(), context);
    Map<String, byte[]> entries = entries(functionZip.copyZipLegacy(rocketFunctions(), "legacy.zip", null).path());
    assertThat(entries.keySet()).containsExactly("my-store.jar", "lib/booster-framework-core-4.1.0.jar",
        "lib/booster-framework-provider-azure-4.1.0.jar", "assets/img/logo.txt", "host.json", "test/function.json");
    // the sources have a host.json, so the default one is not added
    assertThat(text(entries.get("host.json"))).isEqualTo("{\"version\": \"custom\"}");

    InfrastructureContext withoutHostJson = InfrastructureContext.builder().projectRoot(root)
        .applicationClass(Fixtures.APP_CLASS).configuration(Fixtures.CONFIGURATION).httpTransport(http)
        .deploySources(new DeploySources(root, List.of(), List.of())).build();
    Map<String, byte[]> minimal = entries(new FunctionZip(Fixtures.config(), withoutHostJson)
        .copyZipLegacy(rocketFunctions(), "legacy2.zip", null).path());
    assertThat(minimal.keySet()).containsExactly("my-store.jar", "test/function.json", "host.json");
    assertThat(text(minimal.get("host.json"))).isEqualTo(DEFAULT_HOST_JSON);
  }

  @Test
  void uploads_a_package_through_kudu_with_the_publishing_credentials() throws IOException {
    http.on("POST https://management.azure.com/subscriptions/subscription-id/resourceGroups/mystoreproductionrg"
            + "/providers/Microsoft.Web/sites/mystoreproductionrgfpr/config/publishingcredentials/list",
        HttpTransport.Response.of(200, "{\"name\":\"mystoreproductionrgfpr\",\"properties\":{"
            + "\"publishingUserName\":\"$mystoreproductionrgfpr\",\"publishingPassword\":\"secret\"}}"));
    http.on("POST https://mystoreproductionrgfpr.scm.azurewebsites.net/", HttpTransport.Response.of(202, ""));
    FunctionZip functionZip = new FunctionZip(Fixtures.config(), context);
    ZipResource zip = functionZip.copyZip("functionApp.zip", null);

    assertThat(functionZip.deployZip("mystoreproductionrgfpr", "mystoreproductionrg", zip)).isSameAs(zip);

    assertThat(http.calls()).containsExactly(
        "POST https://management.azure.com/subscriptions/subscription-id/resourceGroups/mystoreproductionrg"
            + "/providers/Microsoft.Web/sites/mystoreproductionrgfpr/config/publishingcredentials/list"
            + "?api-version=2023-12-01",
        "POST https://mystoreproductionrgfpr.scm.azurewebsites.net/api/zipDeploy?isAsync=true");
    HttpTransport.Request upload = http.requests("POST", "https://mystoreproductionrgfpr.scm").get(0);
    assertThat(upload.header("Authorization")).contains("Basic " + Base64.getEncoder()
        .encodeToString("$mystoreproductionrgfpr:secret".getBytes(StandardCharsets.UTF_8)));
    assertThat(upload.body()).isEqualTo(zip.path());
    assertThat(upload.header("Content-Type")).isEmpty();
    assertThat(upload.timeout()).isNull();
  }

  @Test
  void the_kudu_host_comes_from_the_credentials_name_and_the_status_is_not_checked() throws IOException {
    http.on("POST https://management.azure.com/", HttpTransport.Response.of(200,
        "{\"name\":\"other\",\"properties\":{}}"));
    http.on("POST https://other.scm.azurewebsites.net/", HttpTransport.Response.of(500, "boom"));
    FunctionZip functionZip = new FunctionZip(Fixtures.config(), context);
    Path file = root.resolve("p.zip");
    Files.write(file, new byte[] {1});
    functionZip.deployZip("app", "rg", new ZipResource(file, "p.zip", "functions"));
    HttpTransport.Request upload = http.requests("POST", "https://other.scm").get(0);
    assertThat(upload.header("Authorization")).contains("Basic " + Base64.getEncoder()
        .encodeToString(":".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void a_new_config_uses_the_default_app_name() {
    BoosterConfig config = new BoosterConfig("test");
    assertThat(FunctionAppLayout.appJarName(config)).isEqualTo("new-booster-app.jar");
    assertThat(FunctionAppLayout.scriptFile(config)).isEqualTo("../new-booster-app.jar");
  }
}
