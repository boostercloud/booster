package com.boostercloud.rocket.fileuploads.local.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.boostercloud.framework.provider.local.LocalProviderRuntime;
import com.boostercloud.framework.provider.local.infrastructure.Infrastructure;
import com.boostercloud.framework.provider.local.infrastructure.LocalInfrastructure;
import com.boostercloud.framework.provider.local.infrastructure.LocalServer;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.framework.types.InternalInfo;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.UserAppRegistry;
import com.boostercloud.rocket.fileuploads.core.BoosterRocketFiles;
import com.boostercloud.rocket.fileuploads.core.FileHandler;
import com.boostercloud.rocket.fileuploads.support.Http;
import com.boostercloud.rocket.fileuploads.support.RecordingUserApp;
import com.boostercloud.rocket.fileuploads.types.Constants;
import com.boostercloud.rocket.fileuploads.types.RocketFilesUserConfiguration;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The local infrastructure rocket on a real local server ({@code Infrastructure.infrastructure(rockets).startServer}),
 * with a recording application instead of core: the presigned URL routes ({@code file-controller.ts}), the mount
 * ({@code infra.ts}) and the watcher notifications.
 */
class LocalFilesServerTest {

  private static final RocketFilesUserConfiguration CLIENT =
      new RocketFilesUserConfiguration("ClientST", "rocketfiles", List.of("client1", "client2"));
  private static final RocketFilesUserConfiguration CMS =
      new RocketFilesUserConfiguration("cmsst", "rocketfiles", List.of("cms/*"));

  @TempDir
  Path project;

  private BoosterConfig config;
  private RecordingUserApp app;
  private LocalServer server;

  @BeforeEach
  void setUp() {
    config = new BoosterConfig("local");
    config.appName = "files";
    config.logger = Logger.NOOP;
    config.setUserProjectRootPath(project.toString());
    config.rockets = List.of(new BoosterRocketFiles(config, List.of(CLIENT, CMS)).rocketForLocal());
    app = new RecordingUserApp(config);
    UserAppRegistry.register(app);
    LocalProviderRuntime runtime = LocalProviderRuntime.create(LocalProviderRuntime.Options.defaults()
        .withBaseDirectory(project).withWebSocketServer(false, "0").withUserApp(() -> app));
    server = Infrastructure.infrastructure(config.rockets,
        LocalInfrastructure.Options.defaults().withRuntime(runtime).withShutdownHook(false)).startServer(config, 0);
  }

  @AfterEach
  void tearDown() {
    server.close();
    UserAppRegistry.clear();
    EnvironmentVariables.unset(Constants.LOCAL_PORT_ENV);
    EnvironmentVariables.unset(InternalInfo.BOOSTER_LOCAL_PORT);
  }

  private String url(String path) {
    return server.url() + path;
  }

  private static byte[] randomBytes(int size) {
    byte[] bytes = new byte[size];
    new Random(42).nextBytes(bytes);
    return bytes;
  }

  @Test
  void mounting_sets_the_port_of_the_presigned_urls_and_creates_the_containers() {
    assertThat(Constants.localPort()).isEqualTo(Integer.toString(server.port()));
    assertThat(project.resolve("clientst/rocketfiles")).isDirectory();
    assertThat(project.resolve("cmsst/rocketfiles")).isDirectory();
    assertThat(new FileHandler(config, "clientst").<String>presignedPut("client1", "myfile.txt"))
        .isEqualTo("http://localhost:" + server.port() + "/clientst/rocketfiles/client1/myfile.txt");
  }

  @Test
  void a_put_writes_the_raw_body_and_answers_the_typescript_result() {
    byte[] content = randomBytes(300_000);
    String presigned = new FileHandler(config, "clientst").presignedPut("client1", "myfile.txt");

    HttpResponse<byte[]> response = Http.put(presigned, content);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("content-type")).hasValue("application/json; charset=utf-8");
    assertThat(Http.text(response)).isEqualTo("{\"status\":\"success\",\"result\":{\"message\":\"File uploaded\","
        + "\"data\":{\"name\":\"myfile.txt\",\"mimeType\":\"unknown\",\"size\":0}}}");
    assertThat(project.resolve("clientst/rocketfiles/client1/myfile.txt")).hasBinaryContent(content);
  }

  @Test
  void a_second_put_replaces_the_file() {
    Http.put(url("/clientst/rocketfiles/client2/a.txt"), "a much longer first version".getBytes(StandardCharsets.UTF_8));
    Http.put(url("/clientst/rocketfiles/client2/a.txt"), "v2".getBytes(StandardCharsets.UTF_8));

    assertThat(project.resolve("clientst/rocketfiles/client2/a.txt")).hasContent("v2");
  }

  @Test
  void the_file_name_is_decoded_and_the_directory_comes_from_the_raw_url() {
    HttpResponse<byte[]> response = Http.put(url("/clientst/rocketfiles/client1/my%20file.txt?x=1"),
        "x".getBytes(StandardCharsets.UTF_8));

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(BoosterJson.parse(Http.text(response))).extracting(body -> ((Map<?, ?>) body).get("result"))
        .extracting(result -> ((Map<?, ?>) result).get("data"))
        .isEqualTo(Map.of("name", "my file.txt", "mimeType", "unknown", "size", 0));
    assertThat(project.resolve("clientst/rocketfiles/client1/my file.txt")).hasContent("x");
  }

  @Test
  void express_routing_details_are_kept() {
    // a query string is part of req.url, so a '/' in it moves the folder: path.parse('/client1/f.txt?x=a/b').dir
    assertThat(Http.put(url("/clientst/rocketfiles/client1/f.txt?x=a/b"), "q".getBytes()).statusCode())
        .isEqualTo(200);
    assertThat(project.resolve("clientst/rocketfiles/client1/f.txt?x=a/f.txt")).hasContent("q");
    // the folder is the raw URL, the file name the decoded parameter
    assertThat(Http.put(url("/cmsst/rocketfiles/cms/my%20dir/f%20g.txt"), "r".getBytes()).statusCode())
        .isEqualTo(200);
    assertThat(project.resolve("cmsst/rocketfiles/cms/my%20dir/f g.txt")).hasContent("r");
    // one trailing slash is tolerated (strict: false) and path.parse ignores it
    assertThat(Http.put(url("/clientst/rocketfiles/client2/t.txt/"), "t".getBytes()).statusCode()).isEqualTo(200);
    assertThat(project.resolve("clientst/rocketfiles/client2/t.txt")).hasContent("t");
    // routes and the mount path match ignoring case; the folder keeps the case of the URL
    assertThat(Http.put(url("/CLIENTST/RocketFiles/Client1/upper.txt"), "u".getBytes()).statusCode())
        .isEqualTo(200);
    assertThat(project.resolve("clientst/rocketfiles/Client1/upper.txt")).hasContent("u");
    assertThat(Http.text(Http.get(url("/clientst/rocketfiles/client2/t.txt")))).isEqualTo("t");
  }

  @Test
  void a_star_directory_is_an_express_wildcard_over_several_segments() {
    HttpResponse<byte[]> response = Http.put(url("/cmsst/rocketfiles/cms/a/b/c.txt"), "c".getBytes());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(project.resolve("cmsst/rocketfiles/cms/a/b/c.txt")).hasContent("c");
  }

  @Test
  void a_get_downloads_the_file_as_an_attachment() {
    byte[] content = randomBytes(50_000);
    Http.put(url("/clientst/rocketfiles/client1/report.txt"), content);
    String presigned = new FileHandler(config, "clientst").presignedGet("client1", "report.txt");

    HttpResponse<byte[]> response = Http.get(presigned);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo(content);
    assertThat(response.headers().firstValue("content-disposition")).hasValue("attachment; filename=\"report.txt\"");
    assertThat(response.headers().firstValue("content-type")).hasValue("text/plain; charset=UTF-8");
  }

  @Test
  void downloading_a_missing_file_answers_500_with_the_node_error() {
    HttpResponse<byte[]> response = Http.get(url("/clientst/rocketfiles/client1/missing.txt"));

    assertThat(response.statusCode()).isEqualTo(500);
    assertThat(BoosterJson.parse(Http.text(response))).isEqualTo(Map.of("title", "Unchecked IO Exception",
        "reason", "ENOENT: no such file or directory, stat '"
            + project.resolve("clientst/rocketfiles/client1/missing.txt").toAbsolutePath() + "'"));
  }

  @Test
  void writing_onto_a_directory_or_under_a_file_fails_with_the_node_errors() throws Exception {
    Path directory = Files.createDirectories(project.resolve("clientst/rocketfiles/client1/sub"));
    Files.writeString(project.resolve("clientst/rocketfiles/client2"), "a file where a directory is expected");

    HttpResponse<byte[]> onDirectory = Http.put(url("/clientst/rocketfiles/client1/sub"), new byte[] {1});
    HttpResponse<byte[]> underFile = Http.put(url("/clientst/rocketfiles/client2/a.txt"), new byte[] {1});

    assertThat(onDirectory.statusCode()).isEqualTo(500);
    assertThat(BoosterJson.parse(Http.text(onDirectory))).isEqualTo(Map.of("title", "Unchecked IO Exception",
        "reason", "EISDIR: illegal operation on a directory, open '" + directory + "'"));
    assertThat(underFile.statusCode()).isEqualTo(500);
    assertThat(BoosterJson.parse(Http.text(underFile))).isEqualTo(Map.of("title", "Unchecked IO Exception",
        "reason", "ENOTDIR: not a directory, open '" + project.resolve("clientst/rocketfiles/client2/a.txt") + "'"));
  }

  @Test
  void dot_files_are_not_served() throws Exception {
    Files.createDirectories(project.resolve("clientst/rocketfiles/client1"));
    Files.writeString(project.resolve("clientst/rocketfiles/client1/.env"), "secret");

    HttpResponse<byte[]> response = Http.get(url("/clientst/rocketfiles/client1/.env"));

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(BoosterJson.parse(Http.text(response)))
        .isEqualTo(Map.of("title", "Not Found Error", "reason", "Not Found"));
  }

  @Test
  void other_directories_and_methods_are_not_routed() {
    assertThat(Http.put(url("/clientst/rocketfiles/client3/a.txt"), new byte[] {1}).statusCode()).isEqualTo(404);
    assertThat(Http.put(url("/clientst/rocketfiles/client1/sub/a.txt"), new byte[] {1}).statusCode()).isEqualTo(404);
    assertThat(Http.send("POST", url("/clientst/rocketfiles/client1/a.txt"), Map.of(), new byte[] {1}).statusCode())
        .isEqualTo(404);
    assertThat(project.resolve("clientst/rocketfiles/client3")).doesNotExist();
  }

  @Test
  void targets_outside_the_container_are_refused() {
    HttpResponse<byte[]> response = Http.put(url("/clientst/rocketfiles/client1/..%2F..%2F..%2Fescaped.txt"),
        "x".getBytes());

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(BoosterJson.parse(Http.text(response))).isEqualTo(Map.of("title", "Invalid Parameter Error",
        "reason", "Invalid file name ../../../escaped.txt"));
    assertThat(project.resolve("escaped.txt")).doesNotExist();
    assertThat(Http.get(url("/clientst/rocketfiles/client1/..%2F..%2F..%2Fescaped.txt")).statusCode()).isEqualTo(400);
  }

  @Test
  void a_json_body_is_consumed_by_the_server_parser_first_as_in_typescript() {
    HttpResponse<byte[]> response = Http.send("PUT", url("/clientst/rocketfiles/client1/data.json"),
        Map.of("Content-Type", "application/json"), "{\"a\":1}".getBytes());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(project.resolve("clientst/rocketfiles/client1/data.json")).hasContent("");
  }

  @Test
  void uploads_are_notified_to_the_rocket_function() {
    Http.put(url("/clientst/rocketfiles/client1/myfile.txt"), "hello".getBytes());

    Map<String, Object> expected = new LinkedHashMap<>();
    expected.put("BOOSTER_ROCKET_FUNCTION_ID", "rocket-files");
    expected.put("uri", "http://localhost:" + server.port() + "/clientst/rocketfiles/client1/myfile.txt");
    expected.put("name", "client1/myfile.txt");
    await().atMost(Duration.ofSeconds(30)).until(() -> app.rocketRequests.contains(expected));
  }

  @Test
  void the_watchers_stop_with_the_server() throws Exception {
    server.close();
    Files.createDirectories(project.resolve("clientst/rocketfiles/client1"));
    Files.writeString(project.resolve("clientst/rocketfiles/client1/late.txt"), "late");

    Thread.sleep(3000);

    assertThat(app.rocketRequests).isEmpty();
  }
}
