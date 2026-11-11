package com.boostercloud.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.cli.services.project.PomFile;
import com.boostercloud.cli.testing.FakeProcessService;
import com.boostercloud.cli.testing.TestCli;
import com.boostercloud.cli.testing.TestProjects;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Creates a project with {@code new:project} in a temporary directory, generates every kind of resource in it,
 * runs the build and runtime commands against the fake process runner, and finally compiles the generated sources
 * against a minimal stand-in of the framework API (ARCHITECTURE.md §6b) to prove they are valid Java. No network
 * nor framework artifacts are needed.
 */
class EndToEndTest {
  @TempDir Path workspace;

  private static final String BASE = "src/main/java/com/acme/mystore/";

  @Test
  void createsAProjectAndGeneratesEveryResource() throws Exception {
    // 1. new:project
    TestCli root = new TestCli(workspace);
    TestCli.Result created = root.run("new:project", "my-store", "--default", "--groupId", "com.acme");
    assertThat(created.exitCode()).isZero();
    assertThat(created.stdout()).contains("ℹ Project generated!");
    Path project = workspace.resolve("my-store");
    assertThat(root.process().calls())
        .extracting(FakeProcessService.Call::line)
        .containsExactly("mvn -B -q dependency:resolve", "git init", "git add -A", "git commit -m Initial commit");
    assertThat(root.process().calls()).allSatisfy(call -> assertThat(call.cwd()).isEqualTo(project));
    assertThat(tree(project))
        .containsExactly(
            ".gitignore",
            "README.md",
            "pom.xml",
            "src/main/java/com/acme/mystore/Main.java",
            "src/main/java/com/acme/mystore/commands/",
            "src/main/java/com/acme/mystore/common/",
            "src/main/java/com/acme/mystore/config/Config.java",
            "src/main/java/com/acme/mystore/entities/",
            "src/main/java/com/acme/mystore/eventhandlers/",
            "src/main/java/com/acme/mystore/events/",
            "src/main/java/com/acme/mystore/queries/",
            "src/main/java/com/acme/mystore/readmodels/",
            "src/main/java/com/acme/mystore/scheduledcommands/",
            "src/main/resources/");
    PomFile pom = PomFile.read(project.resolve("pom.xml"));
    assertThat(pom.artifactId()).isEqualTo("my-store");
    assertThat(pom.property("booster.mainClass")).contains("com.acme.mystore.Main");
    assertThat(pom.dependencies())
        .filteredOn(d -> d.groupId().equals("com.boostercloud"))
        .allSatisfy(d -> assertThat(d.version()).isEqualTo(CliVersion.VERSION))
        .hasSize(5);
    assertThat(TestProjects.read(project.resolve("pom.xml")))
        .contains("<parameters>true</parameters>")
        .contains("<outputDirectory>${project.build.directory}/lib</outputDirectory>");
    assertThat(TestProjects.read(project.resolve(BASE + "Main.java"))).contains("Booster.run(Main.class, args);");
    assertThat(TestProjects.read(project.resolve(BASE + "config/Config.java")))
        .contains("public class Config implements BoosterConfigurator")
        .contains("Booster.configure(\"local\", config -> {")
        .contains("Booster.configure(\"production\", config -> {")
        .contains("config.appName = \"my-store\";")
        .contains("config.providerPackage = \"com.boostercloud.framework.provider.azure\";");
    assertThat(TestProjects.read(project.resolve(".gitignore"))).contains("target/").contains(".booster");

    // 2. new:* generators and add:* inside the project
    TestCli cli = new TestCli(project);
    run(cli, "new:event", "PostCreated", "--fields", "postId:UUID", "title:string", "tags:Array<string>", "at:Date");
    run(cli, "new:event", "PostUpdated", "--fields", "postId:UUID", "title:string");
    run(cli, "new:entity", "Post", "--fields", "title:string", "score:number", "--reduces", "PostCreated");
    run(cli, "new:entity", "Comment", "--fields", "postId:UUID", "text:string");
    run(cli, "new:read-model", "PostReadModel", "--fields", "title:string", "--projects", "Post:id");
    run(cli, "new:command", "CreatePost", "--fields", "title:string", "authorId:UUID");
    run(cli, "new:command", "Ping");
    run(cli, "new:query", "PostsByTitle", "--fields", "title:string");
    run(cli, "new:event-handler", "NotifyPost", "--event", "PostCreated");
    run(cli, "new:scheduled-command", "Cleanup");
    run(cli, "new:type", "Address", "--fields", "street:string", "number:number");
    run(cli, "new:type", "Profile", "--fields", "address:Address", "ids:UUID[]", "flags:Record<string, boolean>");
    run(cli, "add:reducer", "--entity", "Post", "--event", "PostUpdated");
    run(cli, "add:projection", "--read-model", "PostReadModel", "--entity", "Comment:postId");

    assertThat(TestProjects.read(project.resolve(BASE + "entities/Post.java")))
        .contains("import com.acme.mystore.events.PostCreated;\nimport com.acme.mystore.events.PostUpdated;\n")
        .contains("reducePostCreated(PostCreated event, Post currentPost)")
        .contains("reducePostUpdated(PostUpdated event, Post currentPost)");
    assertThat(TestProjects.read(project.resolve(BASE + "readmodels/PostReadModel.java")))
        .contains("import com.acme.mystore.entities.Comment;\nimport com.acme.mystore.entities.Post;\n")
        .contains("@Projects(entity = Comment.class, joinKey = \"postId\")");
    assertThat(TestProjects.read(project.resolve(BASE + "eventhandlers/NotifyPost.java")))
        .contains("import com.acme.mystore.events.PostCreated;");
    assertThat(TestProjects.read(project.resolve(BASE + "common/Profile.java")))
        .contains("    Address address,\n    List<UUID> ids,\n    Map<String, Boolean> flags\n");

    // 3. custom templates
    run(cli, "stub:publish");
    TestProjects.write(
        project.resolve("stubs/type.stub"),
        TestProjects.read(project.resolve("stubs/type.stub")).replace("public record", "/** Custom. */\npublic record"));
    run(cli, "new:type", "Money", "--fields", "amount:number", "currency:string");
    assertThat(TestProjects.read(project.resolve(BASE + "common/Money.java"))).contains("/** Custom. */\npublic record Money(");

    // 4. build and runtime commands (Maven and the application are faked)
    cli.process().onCall(call -> {
      if (call.line().contains(" package ")) {
        try {
          Files.createDirectories(project.resolve("target/classes"));
        } catch (IOException e) {
          throw new java.io.UncheckedIOException(e);
        }
      }
    });
    run(cli, "build");
    run(cli, "start", "-e", "local", "--port", "4000");
    run(cli, "deploy", "-e", "production");
    run(cli, "synth", "-e", "production");
    run(cli, "nuke", "-e", "production", "--force");
    List<FakeProcessService.Call> launches = cli.process().calls().stream().filter(FakeProcessService.Call::spawned).toList();
    assertThat(launches)
        .extracting(call -> String.join(" ", call.command().subList(4, call.command().size())))
        .containsExactly("start --port 4000", "deploy", "synth", "nuke");
    assertThat(launches.getFirst().command().subList(1, 4))
        .containsExactly(
            "-cp",
            project.resolve("target/classes") + ":" + project.resolve("target/lib/*"),
            "com.acme.mystore.Main");
    assertThat(launches).extracting(c -> c.environment().get("BOOSTER_ENV")).containsExactly("local", "production", "production", "production");
    assertThat(cli.process().calls().stream().filter(c -> !c.spawned()).map(FakeProcessService.Call::line).distinct())
        .containsExactly("mvn -B -q clean", "mvn -B -q package -DskipTests");
    TestCli.Result hook = cli.run("my-rocket-command", "-e", "local", "--size", "2");
    assertThat(hook.exitCode()).isZero();
    assertThat(cli.process().calls().getLast().command()).endsWith("com.acme.mystore.Main", "my-rocket-command", "--size", "2");

    // 5. the generated sources are valid Java
    assertCompiles(project);
  }

  private static void run(TestCli cli, String... args) {
    TestCli.Result result = cli.run(args);
    assertThat(result.exitCode()).as(String.join(" ", args) + "\n" + result.stdout() + result.stderr()).isZero();
    assertThat(result.stderr()).as(String.join(" ", args)).isEmpty();
  }

  private static List<String> tree(Path root) throws IOException {
    try (Stream<Path> paths = Files.walk(root)) {
      return paths
          .filter(p -> !p.equals(root))
          .filter(p -> Files.isRegularFile(p) || isEmptyDirectory(p))
          .map(p -> root.relativize(p).toString().replace('\\', '/') + (Files.isDirectory(p) ? "/" : ""))
          .sorted()
          .toList();
    }
  }

  private static boolean isEmptyDirectory(Path path) {
    if (!Files.isDirectory(path)) {
      return false;
    }
    try (Stream<Path> entries = Files.list(path)) {
      return entries.findAny().isEmpty();
    } catch (IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }

  /** A minimal stand-in of the framework's user-facing API, enough to type-check the generated code. */
  private static final Map<String, String> FRAMEWORK_API =
      Map.ofEntries(
          Map.entry("com/boostercloud/framework/types/BoosterConfig", "package com.boostercloud.framework.types; public class BoosterConfig { public String appName; public String providerPackage; }"),
          Map.entry("com/boostercloud/framework/core/Booster", "package com.boostercloud.framework.core; public final class Booster { public static void run(Class<?> app, String[] args) {} public static void configure(String env, java.util.function.Consumer<com.boostercloud.framework.types.BoosterConfig> configurator) {} }"),
          Map.entry("com/boostercloud/framework/core/BoosterConfigurator", "package com.boostercloud.framework.core; public interface BoosterConfigurator { void configure(); }"),
          Map.entry("com/boostercloud/framework/types/concepts/UUID", "package com.boostercloud.framework.types.concepts; public final class UUID {}"),
          Map.entry("com/boostercloud/framework/types/concepts/Register", "package com.boostercloud.framework.types.concepts; public final class Register { public Register events(Object... events) { return this; } }"),
          Map.entry("com/boostercloud/framework/types/concepts/QueryInfo", "package com.boostercloud.framework.types.concepts; public final class QueryInfo {}"),
          Map.entry("com/boostercloud/framework/types/concepts/ProjectionResult", "package com.boostercloud.framework.types.concepts; public interface ProjectionResult<T> { static <T> ProjectionResult<T> of(T readModel) { return null; } }"),
          Map.entry("com/boostercloud/framework/core/decorators/Command", "package com.boostercloud.framework.core.decorators; public @interface Command { Class<?>[] authorize() default {}; }"),
          Map.entry("com/boostercloud/framework/core/decorators/Query", "package com.boostercloud.framework.core.decorators; public @interface Query { Class<?>[] authorize() default {}; }"),
          Map.entry("com/boostercloud/framework/core/decorators/ReadModel", "package com.boostercloud.framework.core.decorators; public @interface ReadModel { Class<?>[] authorize() default {}; }"),
          Map.entry("com/boostercloud/framework/core/decorators/Event", "package com.boostercloud.framework.core.decorators; public @interface Event {}"),
          Map.entry("com/boostercloud/framework/core/decorators/Entity", "package com.boostercloud.framework.core.decorators; public @interface Entity { Class<?>[] authorizeReadEvents() default {}; }"),
          Map.entry("com/boostercloud/framework/core/decorators/Reduces", "package com.boostercloud.framework.core.decorators; public @interface Reduces { Class<?> value(); }"),
          Map.entry("com/boostercloud/framework/core/decorators/Projects", "package com.boostercloud.framework.core.decorators; public @interface Projects { Class<?> entity(); String joinKey(); }"),
          Map.entry("com/boostercloud/framework/core/decorators/EventHandler", "package com.boostercloud.framework.core.decorators; public @interface EventHandler { Class<?> value(); }"),
          Map.entry("com/boostercloud/framework/core/decorators/ScheduledCommand", "package com.boostercloud.framework.core.decorators; public @interface ScheduledCommand { String minute() default \"*\"; }"));

  private void assertCompiles(Path project) throws IOException {
    Path sources = workspace.resolve("compile/src");
    for (Map.Entry<String, String> entry : FRAMEWORK_API.entrySet()) {
      TestProjects.write(sources.resolve(entry.getKey() + ".java"), entry.getValue());
    }
    List<String> files = new ArrayList<>();
    try (Stream<Path> generated = Files.walk(project.resolve("src/main/java"))) {
      for (Path file : generated.filter(p -> p.toString().endsWith(".java")).toList()) {
        // fill in the placeholders the user is expected to complete (as in TypeScript, they do not compile as-is),
        // literally: '/* NEW X HERE */' becomes an X, as the 'new X(...)' a user writes there would
        String source =
            TestProjects.read(file)
                .replace("authorize = /* Specify authorized roles here. Use All.class to authorize anyone */", "authorize = {}")
                .replaceAll("/\\* NEW (\\w+) HERE \\*/", "($1) null")
                .replaceAll("return /\\* [^*]+ \\*/;", "return null;");
        if (file.getParent().endsWith("queries")) {
          source = source.replace("/* YOUR CODE HERE */", "return null;");
        }
        Path copy = sources.resolve(project.resolve("src/main/java").relativize(file));
        TestProjects.write(copy, source);
      }
    }
    try (Stream<Path> all = Files.walk(sources)) {
      all.filter(p -> p.toString().endsWith(".java")).forEach(p -> files.add(p.toString()));
    }
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    StringWriter diagnostics = new StringWriter();
    List<String> arguments = new ArrayList<>(List.of("-d", workspace.resolve("compile/classes").toString(), "-proc:none", "-Xlint:none"));
    arguments.addAll(files);
    int result = compiler.run(null, null, new java.io.PrintStream(new java.io.OutputStream() {
      @Override
      public void write(int b) {
        diagnostics.write(b);
      }
    }), arguments.toArray(String[]::new));
    assertThat(result).as(diagnostics.toString()).isZero();
  }
}
