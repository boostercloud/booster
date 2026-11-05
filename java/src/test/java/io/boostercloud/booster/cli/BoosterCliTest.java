package io.boostercloud.booster.cli;

import static org.junit.jupiter.api.Assertions.*;

import io.boostercloud.booster.core.Json;
import io.boostercloud.booster.core.RequestContext;
import io.boostercloud.booster.example.ShoppingCartApplication;
import io.boostercloud.booster.store.JdbcEventStore;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BoosterCliTest {
  @TempDir Path temporary;

  private record Execution(int code, String output, String error) {}

  private Execution execute(String... args) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    int code = BoosterCli.execute(args, new PrintStream(out), new PrintStream(err));
    return new Execution(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
  }

  @Test void helpAndVersionAreAvailableWithoutStartingServices() {
    assertEquals(0, execute().code());
    assertTrue(execute("help").output().contains("replay"));
    assertEquals("Booster Java " + BoosterCli.VERSION + System.lineSeparator(), execute("version").output());
  }

  @Test void invalidCommandsAndOptionsReturnUsefulErrors() {
    assertEquals(1, execute("unknown").code());
    assertTrue(execute("unknown").error().contains("Unknown command"));
    assertTrue(execute("demo", "--port").error().contains("Missing value"));
    assertTrue(execute("demo", "--port", "-1").error().contains("Port must"));
    assertTrue(execute("demo", "--port", "65536").error().contains("Port must"));
    assertTrue(execute("demo", "--port", "xyz").error().contains("Port must"));
    assertTrue(execute("demo", "--bogus", "value").error().contains("Unknown option"));
    assertTrue(execute("demo", "--port", "3000", "--port", "3001").error().contains("Duplicate option"));
  }

  @Test void optionsPermitAnEphemeralPortAndAPathContainingSpaces() {
    assertEquals(0, BoosterCli.parsePort("0"));
    assertEquals("my data", BoosterCli.parseOptions(new String[]{"--data", "my data"}, 0, Set.of("data")).get("data"));
  }

  @Test void projectGenerationCreatesACompilableIndependentApplication() throws Exception {
    Path root = temporary.resolve("new-project");
    Execution result = execute("new", root.toString(), "--package", "example.generated");
    assertEquals(0, result.code(), result.error());
    assertTrue(Files.readString(root.resolve("pom.xml")).contains("<maven.compiler.release>27</maven.compiler.release>"));
    assertTrue(Files.isRegularFile(root.resolve("README.md")));
    Path classes = Files.createDirectory(temporary.resolve("compiled"));
    ByteArrayOutputStream errors = new ByteArrayOutputStream();
    int compiled = ToolProvider.getSystemJavaCompiler().run(null, null, errors,
        "--release", "27", "-classpath", System.getProperty("java.class.path"), "-d", classes.toString(),
        root.resolve("src/main/java/example/generated/Application.java").toString(),
        root.resolve("src/main/java/example/generated/CartDomain.java").toString(),
        root.resolve("src/test/java/example/generated/CartDomainTest.java").toString());
    assertEquals(0, compiled, errors.toString(StandardCharsets.UTF_8));
  }

  @Test void projectGenerationPreservesExistingDirectoriesAndFiles() throws Exception {
    Path existing = Files.createDirectory(temporary.resolve("existing"));
    Path sentinel = Files.writeString(existing.resolve("mine.txt"), "user work");
    assertEquals(1, execute("new", existing.toString()).code());
    assertEquals("user work", Files.readString(sentinel));
    assertFalse(Files.exists(existing.resolve("pom.xml")));
    Path file = Files.writeString(temporary.resolve("file"), "keep");
    assertEquals(1, execute("new", file.toString()).code());
    assertEquals("keep", Files.readString(file));
  }

  @Test void projectGenerationRefusesExistingSymlinks() throws Exception {
    Path destination = Files.createDirectory(temporary.resolve("destination"));
    Path link = Files.createSymbolicLink(temporary.resolve("link"), destination);
    assertEquals(1, execute("new", link.toString()).code());
    assertFalse(Files.exists(destination.resolve("pom.xml")));
  }

  @Test void invalidPackagesDoNotCreateFiles() {
    Path root = temporary.resolve("invalid");
    assertEquals(1, execute("new", root.toString(), "--package", "../escape").code());
    assertEquals(1, execute("new", root.toString(), "--package", "example.class").code());
    assertFalse(Files.exists(root));
    assertEquals(1, execute("new").code());
  }

  @Test void replayRequiresAnExistingJournalAndDoesNotCreateOne() {
    Execution missing = execute("replay", "--data", temporary.toString());
    assertEquals(1, missing.code());
    assertFalse(Files.exists(temporary.resolve("events.mv.db")));
  }

  @Test void replayReopensTheJournalAndCompletes() {
    try (var app = ShoppingCartApplication.create(new JdbcEventStore(temporary.resolve("events")))) {
      app.executeCommand("OpenCart", Json.MAPPER.valueToTree(new ShoppingCartApplication.OpenCart("cart-1")), RequestContext.anonymous());
    }
    Execution result = execute("replay", "--data", temporary.toString());
    assertEquals(0, result.code(), result.error());
    assertTrue(result.output().contains("Replayed 1 events"));
  }
}
