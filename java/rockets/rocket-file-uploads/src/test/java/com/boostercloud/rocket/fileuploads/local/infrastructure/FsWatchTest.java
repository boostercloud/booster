package com.boostercloud.rocket.fileuploads.local.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@code fs-watch.ts}: the filters of the callback and the recursive watching. */
class FsWatchTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  @TempDir
  Path project;

  private final List<Map<String, Object>> dispatched = Collections.synchronizedList(new ArrayList<>());
  private BoosterConfig config;
  private FsWatch watch;

  @BeforeEach
  void setUp() {
    config = new BoosterConfig("local");
    config.logger = Logger.NOOP;
  }

  @AfterEach
  void tearDown() {
    if (watch != null) {
      watch.close();
    }
  }

  private FsWatch start(List<String> directories) {
    watch = FsWatch.fsWatch(config, project, "clientst", "rocketfiles", 4321, directories, dispatched::add);
    return watch;
  }

  private List<Object> names() {
    synchronized (dispatched) {
      return dispatched.stream().map(request -> request.get("name")).toList();
    }
  }

  @Test
  void creates_the_container_directory() {
    start(List.of("client1"));
    assertThat(project.resolve("clientst/rocketfiles")).isDirectory();
    assertThat(watch.root()).isEqualTo(project.resolve("clientst/rocketfiles"));
  }

  @Test
  void dispatches_the_typescript_request() {
    start(List.of("client1"));

    Map<String, Object> request = watch.onChange("client1/myfile.txt");

    Map<String, Object> expected = new LinkedHashMap<>();
    expected.put("BOOSTER_ROCKET_FUNCTION_ID", "rocket-files");
    expected.put("uri", "http://localhost:4321/clientst/rocketfiles/client1/myfile.txt");
    expected.put("name", "client1/myfile.txt");
    assertThat(request).containsExactlyEntriesOf(expected);
    await().atMost(TIMEOUT).until(() -> dispatched.contains(expected));
  }

  @Test
  void ignores_hidden_segments_and_other_directories() {
    start(List.of("client1", "**"));
    FsWatch onlyClient = FsWatch.fsWatch(config, project, "other", "c", 1, List.of("client1"), dispatched::add);
    try {
      assertThat(watch.onChange("client1/.a")).isNull();
      assertThat(watch.onChange(".hidden/x")).isNull();
      assertThat(watch.onChange("client1/..")).isNull();
      assertThat(watch.onChange(null)).isNull();
      assertThat(watch.onChange("client1/a.b")).isNotNull();
      assertThat(watch.onChange("client1/.")).isNotNull();
      assertThat(watch.onChange("client1")).isNotNull();
      assertThat(onlyClient.onChange("client1")).isNull();
      assertThat(onlyClient.onChange("client2/a.txt")).isNull();
      assertThat(onlyClient.onChange("client1/sub/a.txt")).isNull();
    } finally {
      onlyClient.close();
    }
  }

  @Test
  void notifies_creations_modifications_and_deletions() throws Exception {
    Files.createDirectories(project.resolve("clientst/rocketfiles/client1"));
    start(List.of("client1"));
    Path file = project.resolve("clientst/rocketfiles/client1/myfile.txt");

    Files.writeString(file, "one");
    await().atMost(TIMEOUT).until(() -> names().contains("client1/myfile.txt"));
    int afterCreation = dispatched.size();

    Files.delete(file);
    await().atMost(TIMEOUT).until(() -> dispatched.size() > afterCreation);
    assertThat(names()).containsOnly("client1/myfile.txt");
  }

  @Test
  void watches_directories_created_later_and_their_first_files() throws Exception {
    start(List.of("client1/**"));

    Path nested = Files.createDirectories(project.resolve("clientst/rocketfiles/client1/a/b"));
    Files.writeString(nested.resolve("deep.txt"), "deep");

    await().atMost(TIMEOUT).until(() -> names().contains("client1/a/b/deep.txt"));
    Files.writeString(nested.resolve("later.txt"), "later");
    await().atMost(TIMEOUT).until(() -> names().contains("client1/a/b/later.txt"));
  }

  @Test
  void stops_on_close() throws Exception {
    Files.createDirectories(project.resolve("clientst/rocketfiles/client1"));
    start(List.of("client1"));
    watch.close();

    Files.writeString(project.resolve("clientst/rocketfiles/client1/late.txt"), "late");
    Thread.sleep(3000);

    assertThat(dispatched).isEmpty();
    assertThat(watch.onChange("client1/x")).isNotNull();
    assertThat(dispatched).isEmpty();
  }
}
