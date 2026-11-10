package com.boostercloud.rocket.fileuploads.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.awaitility.Awaitility.await;

import com.boostercloud.application.tester.ApplicationOutputs;
import com.boostercloud.application.tester.ApplicationTester;
import com.boostercloud.application.tester.Counters;
import com.boostercloud.application.tester.ProviderTestHelper;
import com.boostercloud.application.tester.Queries;
import com.boostercloud.application.tester.graphql.GraphQLClient;
import com.boostercloud.application.tester.graphql.GraphQLClientException;
import com.boostercloud.application.tester.graphql.GraphQLError;
import com.boostercloud.application.tester.graphql.GraphQLResponse;
import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.provider.local.LocalProvider;
import com.boostercloud.framework.provider.local.LocalProviderRuntime;
import com.boostercloud.framework.provider.local.Provider;
import com.boostercloud.framework.provider.local.infrastructure.Infrastructure;
import com.boostercloud.framework.provider.local.infrastructure.LocalInfrastructure;
import com.boostercloud.framework.provider.local.infrastructure.LocalServer;
import com.boostercloud.framework.provider.local.infrastructure.testhelper.LocalTestHelper;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.framework.types.InternalInfo;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.rocket.fileuploads.e2e.app.App;
import com.boostercloud.rocket.fileuploads.support.Http;
import com.boostercloud.rocket.fileuploads.types.Constants;
import com.boostercloud.rocket.fileuploads.types.UploadedFileEvent;
import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End to end, in-process, with the real pieces: core ({@code Booster.start} on the {@code e2e.app} application), the
 * local provider over a temporary project directory and the local infrastructure server with the rocket mounted (free
 * ports for HTTP and the websocket server). The client side is the application tester's GraphQL client and plain HTTP,
 * as a front end would do: ask for a presigned URL, upload the bytes, observe the event, entity and read model the
 * rocket produces, download them back, list, delete, restart the server on the same directory and find the files and
 * read models again.
 */
class LocalEndToEndTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(60);

  private static final String PUT = """
      mutation FileUploadPut($directory: String!, $fileName: String!, $storageName: String) {
        FileUploadPut(input: { directory: $directory, fileName: $fileName, storageName: $storageName })
      }""";
  private static final String GET = """
      mutation FileUploadGet($directory: String!, $fileName: String!, $storageName: String) {
        FileUploadGet(input: { directory: $directory, fileName: $fileName, storageName: $storageName })
      }""";
  private static final String LIST = """
      mutation FileUploadList($directory: String!, $storageName: String) {
        FileUploadList(input: { directory: $directory, storageName: $storageName })
      }""";
  private static final String DELETE = """
      mutation DeleteFile($directory: String!, $fileName: String!, $storageName: String) {
        DeleteFile(input: { directory: $directory, fileName: $fileName, storageName: $storageName })
      }""";
  private static final String READ_MODEL = """
      query UploadedFileEntityReadModel($id: ID!) {
        UploadedFileEntityReadModel(id: $id) { id metadata }
      }""";

  @TempDir
  Path project;

  private BoosterConfig config;
  private LocalServer server;
  private GraphQLClient client;
  private ApplicationTester applicationUnderTest;

  @BeforeEach
  void setUp() {
    System.setProperty(EnvironmentVariables.BOOSTER_ENV_PROPERTY, "local");
    Booster.start(App.class);
    config = Booster.config();
    config.logger = Logger.NOOP;
    config.setUserProjectRootPath(project.toString());
    startServer();
  }

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.close();
    }
    Booster.reset();
    System.clearProperty(EnvironmentVariables.BOOSTER_ENV_PROPERTY);
    EnvironmentVariables.unset(Constants.LOCAL_PORT_ENV);
    EnvironmentVariables.unset(InternalInfo.BOOSTER_LOCAL_PORT);
  }

  /** What {@code boost start} does, with free ports and the temporary project directory. */
  @SuppressWarnings("deprecation")
  private void startServer() {
    LocalProviderRuntime runtime = LocalProviderRuntime.create(LocalProviderRuntime.Options.defaults()
        .withBaseDirectory(project).withWebSocketServer(true, "0"));
    config.setProvider(new LocalProvider(runtime, config.rockets, Provider::loadInfrastructurePackage));
    server = Infrastructure.infrastructure(config.rockets,
        LocalInfrastructure.Options.defaults().withRuntime(runtime).withShutdownHook(false)).startServer(config, 0);
    int webSocketPort = URI.create(server.websocketURL()).getPort();
    LocalTestHelper helper = LocalTestHelper.build(config.appName,
        LocalTestHelper.Options.defaults().withBaseDirectory(project).withPorts(server.port(), webSocketPort));
    applicationUnderTest = new ApplicationTester(ProviderTestHelper.of(
        new ApplicationOutputs(helper.outputs().graphqlURL(), helper.outputs().websocketURL(),
            helper.outputs().healthURL()),
        new Counters() {
          @Override
          public long subscriptions() {
            return helper.counters().subscriptions();
          }

          @Override
          public long connections() {
            return helper.counters().connections();
          }

          @Override
          public long readModels(String readModelName) {
            return helper.counters().readModels(readModelName);
          }

          @Override
          public long events() {
            return helper.counters().events();
          }
        },
        new Queries() {
          @Override
          public List<Map<String, Object>> events(String primaryKey) {
            return helper.queries().events(primaryKey);
          }

          @Override
          public List<Map<String, Object>> readModels(String primaryKey, String readModelName) {
            return helper.queries().readModels(primaryKey, readModelName);
          }
        }));
    client = applicationUnderTest.graphql().client();
  }

  private void restartServer() {
    server.close();
    server = null;
    startServer();
  }

  private String mutate(String document, Map<String, ?> variables, String field) {
    return (String) client.mutate(document, variables).get(field);
  }

  private static Map<String, Object> variables(Object... keysAndValues) {
    Map<String, Object> variables = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      variables.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return variables;
  }

  /** The metadata the local watcher sends for a file, and its read model id (the md5 of its JSON). */
  private Map<String, Object> metadata(String name) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("BOOSTER_ROCKET_FUNCTION_ID", "rocket-files");
    metadata.put("uri", "http://localhost:" + server.port() + "/clientst/rocketfiles/" + name);
    metadata.put("name", name);
    return metadata;
  }

  private Map<String, Object> awaitReadModel(Map<String, Object> metadata) {
    UUID id = new UploadedFileEvent(metadata).entityID();
    GraphQLResponse[] response = new GraphQLResponse[1];
    await().atMost(TIMEOUT).pollInterval(Duration.ofMillis(250)).until(() -> {
      response[0] = client.query(READ_MODEL, variables("id", id.value()));
      return response[0].get("UploadedFileEntityReadModel") != null;
    });
    @SuppressWarnings("unchecked")
    Map<String, Object> readModel = (Map<String, Object>) response[0].get("UploadedFileEntityReadModel");
    return readModel;
  }

  private static byte[] randomBytes(int size, long seed) {
    byte[] bytes = new byte[size];
    new Random(seed).nextBytes(bytes);
    return bytes;
  }

  @Test
  void upload_observe_download_list_delete_and_restart() {
    byte[] content = randomBytes(256 * 1024, 7);
    byte[] kept = randomBytes(10_000, 8);

    // 1. presigned PUT URLs and the uploads
    String putUrl = mutate(PUT, variables("directory", "client1", "fileName", "myfile.txt", "storageName", "clientst"),
        "FileUploadPut");
    assertThat(putUrl).isEqualTo("http://localhost:" + server.port() + "/clientst/rocketfiles/client1/myfile.txt");
    HttpResponse<byte[]> upload = Http.put(putUrl, content);
    assertThat(upload.statusCode()).isEqualTo(200);
    assertThat(Http.text(upload)).isEqualTo("{\"status\":\"success\",\"result\":{\"message\":\"File uploaded\","
        + "\"data\":{\"name\":\"myfile.txt\",\"mimeType\":\"unknown\",\"size\":0}}}");
    String keptUrl = mutate(PUT, variables("directory", "client2", "fileName", "kept.bin", "storageName", "CLIENTST"),
        "FileUploadPut");
    assertThat(Http.put(keptUrl, kept).statusCode()).isEqualTo(200);
    assertThat(project.resolve("clientst/rocketfiles/client1/myfile.txt")).hasBinaryContent(content);

    // 2. the watcher notification: UploadedFileEvent -> UploadedFileEntity -> the application's read model
    Map<String, Object> metadata = metadata("client1/myfile.txt");
    Map<String, Object> readModel = awaitReadModel(metadata);
    assertThat(readModel).containsEntry("id", new UploadedFileEvent(metadata).entityID().value())
        .containsEntry("metadata", metadata);
    awaitReadModel(metadata("client2/kept.bin"));
    // one event per notification and storage (the local library accepts every notification for every storage)
    assertThat(applicationUnderTest.count().events()).isGreaterThanOrEqualTo(4);

    // 3. the presigned GET URL serves the same bytes
    String getUrl = mutate(GET, variables("directory", "client1", "fileName", "myfile.txt", "storageName", "clientst"),
        "FileUploadGet");
    assertThat(getUrl).isEqualTo(putUrl);
    HttpResponse<byte[]> download = Http.get(getUrl);
    assertThat(download.statusCode()).isEqualTo(200);
    assertThat(download.body()).isEqualTo(content);
    assertThat(download.headers().firstValue("content-disposition")).hasValue("attachment; filename=\"myfile.txt\"");

    // 4. list
    Object listed = client.mutate(LIST, variables("directory", "client1", "storageName", "clientst"))
        .get("FileUploadList");
    assertThat((List<?>) listed).singleElement().satisfies(item -> {
      Map<?, ?> map = (Map<?, ?>) item;
      assertThat(map.get("name")).isEqualTo("client1/myfile.txt");
      Map<?, ?> properties = (Map<?, ?>) map.get("properties");
      assertThat(List.copyOf(properties.keySet())).isEqualTo(List.of("lastModified"));
      assertThat(Instant.parse((String) properties.get("lastModified"))).isBefore(Instant.now().plusSeconds(5));
    });

    // 5. delete (Java addition): the file is gone, its URL fails, the watcher notifies the deletion too
    long eventsBeforeDeletion = applicationUnderTest.count().events();
    assertThat(client.mutate(DELETE, variables("directory", "client1", "fileName", "myfile.txt", "storageName",
        "clientst")).get("DeleteFile")).isEqualTo(true);
    assertThat(client.mutate(DELETE, variables("directory", "client1", "fileName", "myfile.txt", "storageName",
        "clientst")).get("DeleteFile")).isEqualTo(false);
    assertThat(Http.get(getUrl).statusCode()).isEqualTo(500);
    assertThat((List<?>) client.mutate(LIST, variables("directory", "client1", "storageName", "clientst"))
        .get("FileUploadList")).isEmpty();
    await().atMost(TIMEOUT).until(() -> applicationUnderTest.count().events() > eventsBeforeDeletion);

    // 6. restart on the same project directory: files, events and read models are still there
    int oldPort = server.port();
    long readModelsBeforeRestart = applicationUnderTest.count().readModels("UploadedFileEntityReadModel");
    restartServer();
    String keptAfterRestart = mutate(GET, variables("directory", "client2", "fileName", "kept.bin", "storageName",
        "clientst"), "FileUploadGet");
    assertThat(keptAfterRestart).isEqualTo("http://localhost:" + server.port() + "/clientst/rocketfiles/client2/kept.bin");
    assertThat(Http.get(keptAfterRestart).body()).isEqualTo(kept);
    assertThat((List<?>) client.mutate(LIST, variables("directory", "client2", "storageName", "clientst"))
        .get("FileUploadList")).hasSize(1);
    assertThat(applicationUnderTest.count().readModels("UploadedFileEntityReadModel"))
        .isEqualTo(readModelsBeforeRestart);
    Map<String, Object> keptMetadata = new LinkedHashMap<>();
    keptMetadata.put("BOOSTER_ROCKET_FUNCTION_ID", "rocket-files");
    keptMetadata.put("uri", "http://localhost:" + oldPort + "/clientst/rocketfiles/client2/kept.bin");
    keptMetadata.put("name", "client2/kept.bin");
    assertThat(awaitReadModel(keptMetadata)).containsEntry("metadata", keptMetadata);
  }

  @Test
  void files_dropped_into_the_storage_folder_are_notified_after_a_restart_too() throws Exception {
    restartServer();
    Path folder = Files.createDirectories(project.resolve("cmsst/rocketfiles/cms2"));
    Files.writeString(folder.resolve("dropped.txt"), "dropped by hand");

    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("BOOSTER_ROCKET_FUNCTION_ID", "rocket-files");
    metadata.put("uri", "http://localhost:" + server.port() + "/cmsst/rocketfiles/cms2/dropped.txt");
    metadata.put("name", "cms2/dropped.txt");
    assertThat(awaitReadModel(metadata)).containsEntry("metadata", metadata);

    String url = mutate(GET, variables("directory", "cms2", "fileName", "dropped.txt", "storageName", "cmsst"),
        "FileUploadGet");
    assertThat(Http.text(Http.get(url))).isEqualTo("dropped by hand");
  }

  @Test
  void errors_reach_the_client_with_the_typescript_messages() {
    assertThat(errorOf(PUT, variables("directory", "client9", "fileName", "a.txt", "storageName", "clientst")))
        .contains("Invalid directory client9");
    assertThat(errorOf(PUT, variables("directory", "client1", "fileName", "a.txt")))
        .contains("Missing storageAccountName");
    assertThat(errorOf(PUT, variables("directory", "client1", "fileName", "a.txt", "storageName", "nope")))
        .contains("Storage account name nope not found");
    assertThat(errorOf(LIST, variables("directory", "client2", "storageName", "clientst")))
        .contains("ENOENT: no such file or directory, scandir");

    HttpResponse<byte[]> missing = Http.get("http://localhost:" + server.port()
        + "/clientst/rocketfiles/client1/missing.txt");
    assertThat(missing.statusCode()).isEqualTo(500);
    assertThat(Http.text(missing)).contains("ENOENT: no such file or directory, stat");
    assertThat(Http.put("http://localhost:" + server.port() + "/clientst/rocketfiles/elsewhere/a.txt", new byte[] {1})
        .statusCode()).isEqualTo(404);
  }

  private String errorOf(String document, Map<String, Object> variables) {
    GraphQLClientException error = catchThrowableOfType(GraphQLClientException.class,
        () -> client.mutate(document, variables));
    assertThat(error).as("a GraphQL error for %s", variables).isNotNull();
    return error.graphQLErrors().stream().map(GraphQLError::message).toList().toString();
  }
}
