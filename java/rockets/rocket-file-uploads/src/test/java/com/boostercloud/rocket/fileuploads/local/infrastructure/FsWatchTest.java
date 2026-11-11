package com.boostercloud.rocket.fileuploads.local.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@code fs-watch.ts}: the filters of the callback and the recursive watching. */
class FsWatchTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);
  private static final Duration QUIET = Duration.ofSeconds(3);

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
    // no debouncing: every change is dispatched, even the same one right again
    watch.onChange("client1/myfile.txt");
    await().atMost(TIMEOUT).until(() -> dispatched.size() == 2);
    assertThat(dispatched).containsExactly(expected, expected);
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
    int afterCreation = awaitNotificationsBeyond(0);
    // rewriting an existing file (a second upload of the same name): Node's 'change' event
    Files.writeString(file, "two, a different content");
    int afterModification = awaitNotificationsBeyond(afterCreation);
    Files.delete(file);
    awaitNotificationsBeyond(afterModification);

    assertThat(names()).containsOnly("client1/myfile.txt");
  }

  /**
   * Waits for a notification after the first {@code count} ones, then until none has arrived for {@link #QUIET}
   * (longer than the 2-second polling of macOS), so that the notifications of the next step are not those of this one
   * (a creation can be reported as a creation and then a modification).
   *
   * @return the number of notifications once quiet
   */
  private int awaitNotificationsBeyond(int count) {
    await().atMost(TIMEOUT).until(() -> dispatched.size() > count);
    int[] last = {dispatched.size()};
    long[] since = {System.nanoTime()};
    await().atMost(TIMEOUT).pollInterval(Duration.ofMillis(100)).until(() -> {
      int now = dispatched.size();
      if (now != last[0]) {
        last[0] = now;
        since[0] = System.nanoTime();
        return false;
      }
      return System.nanoTime() - since[0] >= QUIET.toNanos();
    });
    return last[0];
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
  void a_file_created_while_a_new_folder_is_registered_is_notified() throws Exception {
    Files.createDirectories(project.resolve("clientst/rocketfiles/client1"));
    start(List.of("client1/**"));
    Path folder = project.resolve("clientst/rocketfiles/client1/new");
    Path racer = folder.resolve("racer.txt");
    // a file written into the new folder right after the watcher listed it (a concurrent upload into a new folder)
    watch.afterListing(directory -> {
      if (directory.equals(folder) && Files.notExists(racer)) {
        try {
          Files.writeString(racer, "racer");
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    });

    Files.createDirectories(folder);

    // Node's recursive watch reports every creation; the folder must be watched before it is listed
    await().atMost(Duration.ofSeconds(15)).until(() -> names().contains("client1/new/racer.txt"));
  }

  @Test
  void a_burst_larger_than_the_jdk_event_queue_is_fully_notified() throws Exception {
    Path folder = Files.createDirectories(project.resolve("clientst/rocketfiles/client1"));
    start(List.of("client1"));
    // the JDK keeps 512 pending events per directory and reports the rest as OVERFLOW (the macOS service polls every
    // 2 seconds, so all these files fall into one poll); Node's recursive watch reports every one of them
    int count = 700;
    for (int i = 0; i < count; i++) {
      Files.writeString(folder.resolve("f" + i + ".txt"), Integer.toString(i));
    }

    List<Object> expected = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      expected.add("client1/f" + i + ".txt");
    }
    await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
        assertThat(new java.util.HashSet<>(names())).containsAll(expected));
  }

  @Test
  void a_failing_dispatch_is_logged_and_the_watch_goes_on() {
    List<String> errors = Collections.synchronizedList(new ArrayList<>());
    config.logger = new Logger() {
      @Override
      public void debug(Object message, Object... optionalParams) {}

      @Override
      public void info(Object message, Object... optionalParams) {}

      @Override
      public void warn(Object message, Object... optionalParams) {}

      @Override
      public void error(Object message, Object... optionalParams) {
        errors.add(message + " " + java.util.Arrays.toString(optionalParams));
      }
    };
    watch = FsWatch.fsWatch(config, project, "clientst", "rocketfiles", 4321, List.of("client1"), request -> {
      if ("client1/bad.txt".equals(request.get("name"))) {
        throw new IllegalStateException("Rocket function with ID \"rocket-files\" not found");
      }
      dispatched.add(request);
    });

    // Node: an unhandled promise rejection; Java: an error line, and later notifications are still dispatched
    watch.onChange("client1/bad.txt");
    watch.onChange("client1/good.txt");

    await().atMost(TIMEOUT).until(() -> !errors.isEmpty() && names().contains("client1/good.txt"));
    assertThat(errors.getFirst()).startsWith("[Booster]|RocketFiles#fsWatch: ")
        .contains("Error dispatching the file notification").contains("client1/bad.txt").contains("Rocket function with ID \"rocket-files\" not found");
  }

  @Test
  void changes_made_by_the_server_are_notified_right_away() {
    start(List.of("client1"));
    Path upload = project.resolve("clientst/rocketfiles/client1/upload.txt");

    assertThat(watch.changed(upload)).containsEntry("name", "client1/upload.txt")
        .containsEntry("uri", "http://localhost:4321/clientst/rocketfiles/client1/upload.txt");
    // filtered as the watch events: other folders, the container itself, outside the container
    assertThat(watch.changed(project.resolve("clientst/rocketfiles/client2/x.txt"))).isNull();
    assertThat(watch.changed(project.resolve("clientst/rocketfiles"))).isNull();
    assertThat(watch.changed(project.resolve("clientst/elsewhere/client1/x.txt"))).isNull();
    await().atMost(TIMEOUT).until(() -> names().equals(List.of("client1/upload.txt")));

    watch.close();
    assertThat(watch.changed(upload)).isNull();
  }

  @Test
  void close_handles_the_changes_already_reported() throws Exception {
    Files.createDirectories(project.resolve("clientst/rocketfiles/client1"));
    start(List.of("client1"));
    // keep the watch loop busy (registering a new folder) while a change of client1 is reported
    CountDownLatch busy = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    Path folder = project.resolve("clientst/rocketfiles/client1/slow");
    watch.afterListing(directory -> {
      if (directory.equals(folder)) {
        busy.countDown();
        try {
          release.await();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    });
    Files.createDirectories(folder);
    assertThat(busy.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
    Files.writeString(project.resolve("clientst/rocketfiles/client1/reported.txt"), "reported");
    Thread.sleep(3000); // reported by now, also by the 2-second polling of macOS, but not handled yet

    Thread closing = Thread.ofVirtual().start(watch::close);
    Thread.sleep(200);
    release.countDown();
    closing.join(TIMEOUT);

    assertThat(closing.isAlive()).isFalse();
    assertThat(names()).contains("client1/slow", "client1/reported.txt");
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
