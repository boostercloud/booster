package com.boostercloud.framework.provider.local.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Kills a writer process ({@link StoreWriterMain}) with {@code SIGKILL} at random moments, over and over on the same
 * data file, and checks after every kill that the file loads without corruption and holds exactly the acknowledged
 * operations (plus, possibly, the single operation that was running when the process died). Some kills land while the
 * writer is still loading, which rewrites (compacts) the file, and some during explicit or growth-triggered
 * compactions.
 */
class KillDuringWriteStressTest {

  private static final int CYCLES = 8;

  @TempDir
  Path directory;

  /** What the parent knows about one document: its key and its counter. */
  private record Expected(String key, int counter) {}

  private record Interrupted(String operation, String id, String key, int counter) {}

  @Test
  void everyAcknowledgedWriteSurvivesAKill() throws Exception {
    Path file = directory.resolve(".booster").resolve("killed.json");
    Map<String, Expected> model = new TreeMap<>();
    Random random = new Random(2026);
    int acknowledgedTotal = 0;
    int interruptedOperations = 0;
    for (int cycle = 0; cycle < CYCLES; cycle++) {
      // even cycles are killed after some acknowledged operations, odd ones after a random delay (possibly while the
      // writer is still loading and compacting the file)
      boolean killByTime = cycle % 2 == 1;
      int acknowledgementsBeforeKill = 50 + random.nextInt(600);
      long killDelayMillis = random.nextInt(900);
      List<String> output = new ArrayList<>();
      Process child = start(file, cycle);
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(child.getInputStream(),
          StandardCharsets.UTF_8))) {
        if (killByTime) {
          Thread.sleep(killDelayMillis);
          kill(child);
        } else {
          int acknowledged = 0;
          String line;
          while ((line = reader.readLine()) != null) {
            output.add(line);
            if (line.equals("ACK") && ++acknowledged == acknowledgementsBeforeKill) {
              break;
            }
          }
          kill(child);
        }
        String line;
        while ((line = reader.readLine()) != null) {
          output.add(line);
        }
      } finally {
        child.destroyForcibly();
        assertThat(child.waitFor(30, TimeUnit.SECONDS)).isTrue();
      }
      assertThat(output).noneMatch(line -> line.startsWith("WARN") && !line.contains("incomplete last line"));
      assertThat(output).noneMatch(line -> line.equals("DONE"));
      assertThat(output).noneMatch(line -> line.contains("Exception") || line.contains("Error"));

      Interrupted interrupted = null;
      Interrupted begun = null;
      for (String line : output) {
        if (line.startsWith("BEGIN ")) {
          String[] parts = line.split(" ");
          begun = new Interrupted(parts[1], parts.length > 2 ? parts[2] : null, parts.length > 3 ? parts[3] : null,
              parts.length > 4 ? Integer.parseInt(parts[4]) : 0);
        } else if (line.equals("ACK")) {
          apply(model, begun);
          acknowledgedTotal++;
          begun = null;
        }
      }
      interrupted = begun;
      if (interrupted != null) {
        interruptedOperations++;
      }

      List<String> warnings = Collections.synchronizedList(new ArrayList<>());
      Datastore datastore = new Datastore(DatastoreOptions.of(file).withWarnings(warnings::add));
      Map<String, Map<String, Object>> stored;
      try {
        datastore.loadDatabase();
        stored = StoreStressTest.byId(datastore.getAllData());
      } finally {
        datastore.close();
      }
      assertThat(warnings).as("cycle %d", cycle).allMatch(w -> w.startsWith("Discarding an incomplete last line"));
      assertThat(stored.values()).allMatch(StoreWriterMain::consistent);
      Map<String, Expected> actual = new TreeMap<>();
      stored.forEach((id, document) ->
          actual.put(id, new Expected((String) document.get("key"), ((Number) document.get("counter")).intValue())));
      if (interrupted == null || interrupted.operation().equals("compact")) {
        assertThat(actual).as("cycle %d", cycle).isEqualTo(model);
      } else {
        Map<String, Expected> applied = new TreeMap<>(model);
        apply(applied, interrupted);
        TreeSet<String> ids = new TreeSet<>(model.keySet());
        ids.addAll(applied.keySet());
        for (String id : ids) {
          if (!id.equals(interrupted.id())) {
            assertThat(actual.get(id)).as("cycle %d, document %s", cycle, id).isEqualTo(model.get(id));
          }
        }
        assertThat(actual.get(interrupted.id())).as("cycle %d, interrupted %s", cycle, interrupted)
            .isIn(model.get(interrupted.id()), applied.get(interrupted.id()));
        assertThat(actual.keySet()).isSubsetOf(ids);
      }
      model = actual;
    }
    System.out.printf("[store stress] %d kills, %d acknowledged operations, %d interrupted operations, "
        + "%d documents at the end%n", CYCLES, acknowledgedTotal, interruptedOperations, model.size());
    assertThat(acknowledgedTotal).isPositive();
    assertThat(Files.exists(file)).isTrue();
  }

  /** {@code SIGKILL} without closing our end of the pipe ({@link Process#destroyForcibly()} closes it). */
  private static void kill(Process child) {
    child.toHandle().destroyForcibly();
  }

  private static void apply(Map<String, Expected> model, Interrupted operation) {
    switch (operation.operation()) {
      case "insert", "update" -> model.put(operation.id(), new Expected(operation.key(), operation.counter()));
      case "remove" -> model.remove(operation.id());
      default -> {
        // compaction changes no document
      }
    }
  }

  private Process start(Path file, int cycle) throws IOException {
    String java = ProcessHandle.current().info().command().orElse(
        Path.of(System.getProperty("java.home"), "bin", "java").toString());
    return new ProcessBuilder(java, "-Xshare:auto", "-cp", System.getProperty("java.class.path"),
        StoreWriterMain.class.getName(), file.toString(), Long.toString(1000L + cycle), "100000")
        .redirectErrorStream(true)
        .start();
  }
}
