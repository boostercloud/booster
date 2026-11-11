package com.boostercloud.rocket.fileuploads.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.boostercloud.application.tester.ApplicationTester;
import com.boostercloud.application.tester.graphql.GraphQLClient;
import com.boostercloud.application.tester.graphql.GraphQLResponse;
import com.boostercloud.framework.provider.local.Paths;
import com.boostercloud.framework.provider.local.infrastructure.testhelper.LocalStoreReader;
import com.boostercloud.framework.provider.local.infrastructure.testhelper.LocalTestHelper;
import com.boostercloud.rocket.fileuploads.e2e.app.App;
import com.boostercloud.rocket.fileuploads.e2e.app.Main;
import com.boostercloud.rocket.fileuploads.support.Http;
import com.boostercloud.rocket.fileuploads.support.LocalApplications;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The {@code e2e.app} application run as {@code boost start} runs it: a separate JVM started with
 * {@code java -cp target/classes:<dependencies> Main start --port <N>} from the project directory (the application's
 * classes are copied to {@code <project>/target/classes}, so the project directory, {@code .booster} and the rocket's
 * storage folders are those of a real project), on free ports. The client sends concurrent GraphQL mutations, uploads,
 * downloads and listings, the server is stopped with SIGTERM (what the CLI forwards on Ctrl-C) and started again over
 * the same project directory on other ports, and everything is checked again.
 */
@DisabledOnOs(OS.WINDOWS)
class LocalServerProcessEndToEndTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(90);
  private static final Pattern LISTENING = Pattern.compile("Local server listening on http://localhost:(\\d+)");
  private static final int CONCURRENCY = 16;

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
  private static final String READ_MODEL = """
      query UploadedFileEntityReadModel($id: ID!) {
        UploadedFileEntityReadModel(id: $id) { id metadata }
      }""";

  @TempDir
  Path project;

  private final List<ServerProcess> processes = new CopyOnWriteArrayList<>();
  private final ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);

  /** One file of the test. */
  private record UploadedFile(String storage, String directory, String fileName, byte[] content) {
    String name() {
      return directory + "/" + fileName;
    }
  }

  /** A running server process and its output. */
  private record ServerProcess(Process process, List<String> output, int port, int webSocketPort) {}

  @AfterEach
  void tearDown() throws InterruptedException {
    pool.shutdownNow();
    for (ServerProcess server : processes) {
      server.process().destroyForcibly();
      server.process().waitFor(30, TimeUnit.SECONDS);
    }
  }

  // --- the server process -------------------------------------------------------------------------------------

  private static int freePort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** {@code mvn package} of a project: its classes in {@code <project>/target/classes}. */
  private void copyApplicationClasses() throws IOException, URISyntaxException {
    Path testClasses = Path.of(App.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    Path packageDirectory = Path.of(App.class.getPackageName().replace('.', '/'));
    Path source = testClasses.resolve(packageDirectory);
    Path target = project.resolve("target/classes").resolve(packageDirectory);
    Files.createDirectories(target);
    try (Stream<Path> classes = Files.list(source)) {
      for (Path file : classes.toList()) {
        Files.copy(file, target.resolve(file.getFileName()));
      }
    }
  }

  /** The CLI's launch: {@code java -cp target/classes:<runtime dependencies> <main> start --port <N>}. */
  private ServerProcess startServer() throws Exception {
    if (!Files.isDirectory(project.resolve("target/classes"))) {
      copyApplicationClasses();
    }
    Path testClasses = Path.of(App.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    List<String> classpath = new ArrayList<>();
    classpath.add(project.resolve("target/classes").toString());
    for (String entry : System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"))
        .split(File.pathSeparator)) {
      if (!entry.isEmpty() && !Path.of(entry).toAbsolutePath().normalize().equals(testClasses.normalize())) {
        classpath.add(entry);
      }
    }
    int port = freePort();
    int webSocketPort = freePort();
    String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    ProcessBuilder builder = new ProcessBuilder(java, "-cp", String.join(File.pathSeparator, classpath),
        Main.class.getName(), "start", "--port", Integer.toString(port))
        .directory(project.toFile()).redirectErrorStream(true);
    Map<String, String> environment = builder.environment();
    environment.remove("ROCKET_FILES_LOCAL_PORT");
    environment.remove("BOOSTER_INTERNAL_LOCAL_PORT");
    environment.put("BOOSTER_ENV", "local");
    environment.put("LOCAL_ENVIRONMENT_WEBSOCKET_SERVER_PORT", Integer.toString(webSocketPort));
    Process process = builder.start();
    List<String> output = new CopyOnWriteArrayList<>();
    ServerProcess server = new ServerProcess(process, output, port, webSocketPort);
    processes.add(server);
    Thread.ofVirtual().start(() -> {
      try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          output.add(line);
        }
      } catch (IOException e) {
        // the process ended
      }
    });
    await().atMost(TIMEOUT).pollInterval(Duration.ofMillis(200)).until(() -> {
      if (!process.isAlive()) {
        throw new AssertionError("The server process ended: " + String.join("\n", output));
      }
      return output.stream().map(LISTENING::matcher).anyMatch(Matcher::find);
    });
    return server;
  }

  /** SIGTERM, as the CLI forwards Ctrl-C: the server stops gracefully. */
  private static void stopServer(ServerProcess server) throws InterruptedException {
    assertThat(server.process().toHandle().destroy()).isTrue();
    assertThat(server.process().waitFor(60, TimeUnit.SECONDS)).as("stopped: %s", server.output()).isTrue();
    assertThat(server.process().exitValue()).isEqualTo(143);
    assertThat(String.join("\n", server.output())).contains("Local server stopped");
  }

  private ApplicationTester tester(ServerProcess server) {
    return LocalApplications.tester(LocalTestHelper.build("files-e2e", LocalTestHelper.Options.defaults()
        .withBaseDirectory(project).withPorts(server.port(), server.webSocketPort())));
  }

  // --- client helpers -----------------------------------------------------------------------------------------

  private static Map<String, Object> variables(Object... keysAndValues) {
    Map<String, Object> variables = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      variables.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return variables;
  }

  private <T> List<T> concurrently(List<Callable<T>> tasks) throws Exception {
    List<T> results = new ArrayList<>();
    for (Future<T> future : pool.invokeAll(tasks)) {
      try {
        results.add(future.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS));
      } catch (ExecutionException e) {
        // the task's own failure (an assertion awaited by Awaitility, a client error...)
        if (e.getCause() instanceof Error error) {
          throw error;
        }
        throw e.getCause() instanceof Exception cause ? cause : e;
      }
    }
    return results;
  }

  private static List<UploadedFile> files(String prefix, int count, long seed) {
    Random random = new Random(seed);
    String[][] directories = {{"clientst", "client1"}, {"clientst", "client2"}, {"cmsst", "cms1"}, {"cmsst", "cms2"}};
    List<UploadedFile> files = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      String[] directory = directories[i % directories.length];
      byte[] content = new byte[i == 0 ? 0 : random.nextInt(200_000)];
      random.nextBytes(content);
      files.add(new UploadedFile(directory[0], directory[1], prefix + i + ".bin", content));
    }
    return files;
  }

  private List<String> uploadConcurrently(GraphQLClient client, List<UploadedFile> files) throws Exception {
    List<Callable<String>> uploads = new ArrayList<>();
    for (UploadedFile file : files) {
      uploads.add(() -> {
        String url = (String) client.mutate(PUT, variables("directory", file.directory(), "fileName",
            file.fileName(), "storageName", file.storage())).get("FileUploadPut");
        HttpResponse<byte[]> response = Http.put(url, file.content());
        assertThat(response.statusCode()).as("PUT %s", url).isEqualTo(200);
        assertThat(Http.text(response)).isEqualTo("{\"status\":\"success\",\"result\":{\"message\":\"File uploaded\","
            + "\"data\":{\"name\":\"" + file.fileName() + "\",\"mimeType\":\"unknown\",\"size\":0}}}");
        return url;
      });
    }
    return concurrently(uploads);
  }

  private void downloadConcurrently(GraphQLClient client, List<UploadedFile> files, int port) throws Exception {
    List<Callable<Void>> downloads = new ArrayList<>();
    for (UploadedFile file : files) {
      downloads.add(() -> {
        String url = (String) client.mutate(GET, variables("directory", file.directory(), "fileName",
            file.fileName(), "storageName", file.storage())).get("FileUploadGet");
        assertThat(url).isEqualTo("http://localhost:" + port + "/" + file.storage() + "/rocketfiles/" + file.name());
        HttpResponse<byte[]> response = Http.get(url);
        assertThat(response.statusCode()).as("GET %s", url).isEqualTo(200);
        assertThat(response.body()).as("GET %s", url).isEqualTo(file.content());
        return null;
      });
    }
    concurrently(downloads);
  }

  @SuppressWarnings("unchecked")
  private void listConcurrently(GraphQLClient client, List<UploadedFile> files) throws Exception {
    Map<List<String>, Set<String>> expected = new LinkedHashMap<>();
    for (UploadedFile file : files) {
      expected.computeIfAbsent(List.of(file.storage(), file.directory()), key -> new HashSet<>()).add(file.name());
    }
    List<Callable<Void>> listings = new ArrayList<>();
    expected.forEach((key, names) -> listings.add(() -> {
      List<Object> items = (List<Object>) client.mutate(LIST, variables("directory", key.get(1), "storageName",
          key.get(0))).get("FileUploadList");
      Set<String> listed = new HashSet<>();
      for (Object item : items) {
        listed.add((String) ((Map<String, Object>) item).get("name"));
      }
      assertThat(listed).as("list %s", key).containsAll(names);
      return null;
    }));
    concurrently(listings);
  }

  /** Every file has its read model (id = md5 of the metadata the watcher sent with that port). */
  @SuppressWarnings("unchecked")
  private void awaitReadModels(GraphQLClient client, List<UploadedFile> files, int port) {
    await().atMost(TIMEOUT).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
      List<Callable<Void>> queries = new ArrayList<>();
      for (UploadedFile file : files) {
        queries.add(() -> {
          Map<String, Object> metadata = LocalApplications.localMetadata(port, file.storage(), file.name());
          GraphQLResponse response = client.query(READ_MODEL, variables("id", LocalApplications.entityId(metadata)));
          Map<String, Object> readModel = (Map<String, Object>) response.get("UploadedFileEntityReadModel");
          assertThat(readModel).as("read model of %s", file.name()).isNotNull();
          assertThat(readModel).containsEntry("metadata", metadata);
          return null;
        });
      }
      concurrently(queries);
    });
  }

  /** Waits until no new event has been stored for a while (the watcher notifies several times per upload). */
  private static void awaitQuietEvents(ApplicationTester tester) {
    long[] last = {-1};
    long[] since = {System.nanoTime()};
    await().atMost(TIMEOUT).pollInterval(Duration.ofMillis(500)).until(() -> {
      long count = tester.count().events();
      if (count != last[0]) {
        last[0] = count;
        since[0] = System.nanoTime();
        return false;
      }
      return System.nanoTime() - since[0] > Duration.ofSeconds(5).toNanos();
    });
  }

  private List<Map<String, Object>> storedDocuments() {
    return new LocalStoreReader(project).documents(Paths.EVENTS_DATABASE);
  }

  // --- the test -----------------------------------------------------------------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  void concurrent_clients_and_a_restart_over_the_same_project_directory() throws Exception {
    ServerProcess first = startServer();
    ApplicationTester tester = tester(first);
    GraphQLClient client = tester.graphql().client();

    // 1. concurrent presigned URLs and uploads into both storages
    List<UploadedFile> files = files("file", 40, 11);
    List<String> urls = uploadConcurrently(client, files);
    assertThat(new HashSet<>(urls)).hasSize(files.size());
    for (UploadedFile file : files) {
      assertThat(project.resolve(file.storage()).resolve("rocketfiles").resolve(file.name()))
          .hasBinaryContent(file.content());
    }

    // 2. concurrent overwrites of one file: every PUT is answered, the file has the length of any of them
    byte[][] versions = new byte[8][];
    List<Callable<Integer>> overwrites = new ArrayList<>();
    for (int i = 0; i < versions.length; i++) {
      versions[i] = new byte[50_000];
      Arrays.fill(versions[i], (byte) ('a' + i));
      byte[] version = versions[i];
      overwrites.add(() -> Http.put(urls.getFirst(), version).statusCode());
    }
    assertThat(concurrently(overwrites)).containsOnly(200);
    Path overwritten = project.resolve(files.getFirst().storage()).resolve("rocketfiles").resolve(
        files.getFirst().name());
    assertThat(overwritten).hasSize(50_000);
    UploadedFile last = new UploadedFile(files.getFirst().storage(), files.getFirst().directory(),
        files.getFirst().fileName(), Files.readAllBytes(overwritten));
    List<UploadedFile> current = new ArrayList<>(files);
    current.set(0, last);

    // 3. concurrent downloads and listings while the notifications are processed
    downloadConcurrently(client, current, first.port());
    listConcurrently(client, current);

    // 4. every upload reached the application: UploadedFileEvent (one per storage and notification) ->
    //    UploadedFileEntity snapshot -> the README read model
    awaitReadModels(client, files, first.port());
    awaitQuietEvents(tester);
    List<Map<String, Object>> documents = storedDocuments();
    for (UploadedFile file : files) {
      Map<String, Object> metadata = LocalApplications.localMetadata(first.port(), file.storage(), file.name());
      assertThat(documents.stream().filter(document -> "event".equals(document.get("kind"))
              && "UploadedFileEvent".equals(document.get("typeName"))
              && metadata.equals(((Map<String, Object>) document.get("value")).get("metadata"))).count())
          .as("events of %s", file.name()).isGreaterThanOrEqualTo(2);
      assertThat(documents).as("snapshot of %s", file.name()).anySatisfy(document -> {
        assertThat(document).containsEntry("kind", "snapshot").containsEntry("typeName", "UploadedFileEntity");
        assertThat((Map<String, Object>) document.get("value")).containsEntry("id",
            LocalApplications.entityId(metadata)).containsEntry("metadata", metadata);
      });
    }
    long events = tester.count().events();
    long readModels = tester.count().readModels("UploadedFileEntityReadModel");
    assertThat(readModels).isEqualTo(files.size());

    // 5. SIGTERM and a new process over the same project directory, on other ports
    stopServer(first);
    ServerProcess second = startServer();
    assertThat(second.port()).isNotEqualTo(first.port());
    ApplicationTester testerAfterRestart = tester(second);
    GraphQLClient clientAfterRestart = testerAfterRestart.graphql().client();

    downloadConcurrently(clientAfterRestart, current, second.port());
    listConcurrently(clientAfterRestart, current);
    awaitReadModels(clientAfterRestart, files, first.port());
    Thread.sleep(3000); // longer than a poll of the macOS watcher: starting does not notify the existing files
    assertThat(testerAfterRestart.count().events()).isEqualTo(events);
    assertThat(testerAfterRestart.count().readModels("UploadedFileEntityReadModel")).isEqualTo(readModels);

    // 6. new concurrent uploads after the restart are notified with the new port
    List<UploadedFile> moreFiles = files("after-restart", 12, 12);
    uploadConcurrently(clientAfterRestart, moreFiles);
    downloadConcurrently(clientAfterRestart, moreFiles, second.port());
    awaitReadModels(clientAfterRestart, moreFiles, second.port());
    assertThat(testerAfterRestart.count().readModels("UploadedFileEntityReadModel"))
        .isEqualTo(readModels + moreFiles.size());
    stopServer(second);
  }
}
