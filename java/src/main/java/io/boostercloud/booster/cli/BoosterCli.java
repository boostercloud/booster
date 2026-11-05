package io.boostercloud.booster.cli;

import io.boostercloud.booster.core.Booster;
import io.boostercloud.booster.example.ShoppingCartApplication;
import io.boostercloud.booster.store.JdbcEventStore;
import io.boostercloud.booster.transport.BoosterHttpServer;
import io.boostercloud.booster.local.*;
import io.boostercloud.booster.azure.AzureClients;
import io.boostercloud.booster.azure.infrastructure.*;
import java.time.Duration;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/** Small, dependency-free command line for creating and running local Java applications. */
public final class BoosterCli {
  public static final String VERSION = "0.1.0-SNAPSHOT";

  private BoosterCli() {}

  public static void main(String[] args) {
    int status = execute(args, System.out, System.err);
    if (status != 0) System.exit(status);
  }

  public static int execute(String[] args, PrintStream output, PrintStream error) {
    try {
      if (args.length == 0 || args[0].equals("help") || args[0].equals("--help") || args[0].equals("-h")) {
        output.print(help());
        return 0;
      }
      if (args[0].equals("version") || args[0].equals("--version")) {
        if (args.length != 1) throw new IllegalArgumentException("version takes no arguments");
        output.println("Booster Java " + VERSION);
        return 0;
      }
      return switch (args[0]) {
        case "new" -> createProject(args, output);
        case "demo", "start" -> serve(parseOptions(args, 1, Set.of("port", "data")), output);
        case "replay" -> replay(parseOptions(args, 1, Set.of("data")), output);
        case "local" -> local(args, output);
        case "azure" -> azure(args, output);
        case "generate" -> generate(args, output);
        case "build", "clean" -> maven(args, output);
        default -> throw new IllegalArgumentException("Unknown command: " + args[0] + ". Run 'help' for usage.");
      };
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      error.println("Interrupted");
      return 130;
    } catch (Exception failure) {
      error.println("Error: " + failure.getMessage());
      return 1;
    }
  }

  private static int generate(String[] args, PrintStream output) throws Exception {
    if (args.length < 3) throw new IllegalArgumentException("Usage: generate <kind> <Name> --package com.example [--project .] [--fields id:String,amount:long]");
    var options = parseOptions(args, 3, Set.of("package", "project", "fields"));
    var fields = options.containsKey("fields") ? java.util.Arrays.asList(options.get("fields").split(",", -1)) : java.util.List.<String>of();
    output.println(DomainGenerator.generate(Path.of(options.getOrDefault("project", ".")), required(options, "package"), args[1], args[2], fields));
    return 0;
  }
  private static int maven(String[] args, PrintStream output) throws Exception {
    var options = parseOptions(args, 1, Set.of("project"));
    Path project = Path.of(options.getOrDefault("project", ".")).toAbsolutePath().normalize();
    if (!Files.isRegularFile(project.resolve("pom.xml"))) throw new IllegalArgumentException("No Maven project at " + project);
    String executable = Files.isExecutable(project.resolve("mvnw")) ? project.resolve("mvnw").toString() : "mvn";
    var process = new ProcessBuilder(executable, "-B", "-C", args[0].equals("build") ? "verify" : "clean")
        .directory(project.toFile()).redirectErrorStream(true).start();
    try (var stream = process.getInputStream()) { stream.transferTo(output); }
    return process.waitFor();
  }

  private static int local(String[] args, PrintStream output) throws Exception {
    if (args.length < 2) throw new IllegalArgumentException("Usage: local synth|start|stop|status|rebuild|nuke --config local.json");
    var options = parseOptions(args, 2, switch (args[1]) {
      case "synth" -> Set.of("config", "application", "data", "host", "port");
      case "start" -> Set.of("config", "detach");
      case "nuke" -> Set.of("config", "confirm-data");
      case "stop", "status", "rebuild" -> Set.of("config");
      default -> throw new IllegalArgumentException("Unknown local operation: " + args[1]);
    });
    Path file = Path.of(options.getOrDefault("config", "local.json"));
    if (args[1].equals("synth")) {
      String application = required(options, "application");
      output.println(LocalInfrastructure.synth(new LocalConfiguration(application, options.getOrDefault("data", ".booster-java"),
          options.getOrDefault("host", "127.0.0.1"), parsePort(options.getOrDefault("port", "3000"))), file));
      return 0;
    }
    var config = LocalConfiguration.read(file);
    switch (args[1]) {
      case "start" -> {
        if (options.containsKey("detach") && !Set.of("true", "false").contains(options.get("detach")))
          throw new IllegalArgumentException("--detach must be true or false");
        if (Boolean.parseBoolean(options.getOrDefault("detach", "false"))) output.println("Started PID " + LocalInfrastructure.startDetached(file));
        else LocalHost.run(config, output);
      }
      case "stop" -> { LocalInfrastructure.stop(config); output.println("Stopped local application; data retained."); }
      case "status" -> output.println(io.boostercloud.booster.core.Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(LocalInfrastructure.status(config)));
      case "rebuild" -> output.println("Rebuilt projections through journal position " + LocalInfrastructure.rebuild(config));
      case "nuke" -> { LocalInfrastructure.nuke(config, Path.of(required(options, "confirm-data"))); output.println("Removed local application data."); }
      default -> throw new IllegalStateException("Unexpected local operation");
    }
    return 0;
  }

  private static int azure(String[] args, PrintStream output) throws Exception {
    if (args.length < 2) throw new IllegalArgumentException("Usage: azure synth|deploy|status|nuke --config azure.json");
    var options = parseOptions(args, 2, switch (args[1]) {
      case "synth" -> Set.of("config", "output");
      case "deploy", "status" -> Set.of("config");
      case "nuke" -> Set.of("config", "confirm-resource-group");
      default -> throw new IllegalArgumentException("Unknown Azure operation: " + args[1]);
    });
    var config = AzureDeploymentConfig.read(Path.of(options.getOrDefault("config", "azure.json")));
    if (args[1].equals("synth")) {
      output.println(AzureInfrastructure.write(config, Path.of(options.getOrDefault("output", "azure/template.json")))); return 0;
    }
    var deployment = new AzureDeployment(new AzureArmClient(AzureClients.credential()));
    switch (args[1]) {
      case "deploy" -> output.println(io.boostercloud.booster.core.Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(deployment.deploy(config, Duration.ofMinutes(30))));
      case "status" -> output.println(io.boostercloud.booster.core.Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(deployment.status(config)));
      case "nuke" -> { deployment.nuke(config, required(options, "confirm-resource-group"), Duration.ofMinutes(30)); output.println("Azure resource-group deletion verified."); }
      default -> throw new IllegalStateException("Unexpected Azure operation");
    }
    return 0;
  }

  private static String required(Map<String, String> options, String name) {
    String value = options.get(name); if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing --" + name); return value;
  }

  private static int createProject(String[] args, PrintStream output) throws Exception {
    if (args.length < 2 || args[1].startsWith("--")) {
      throw new IllegalArgumentException("Usage: new <directory> [--package com.example.cart]");
    }
    Map<String, String> options = parseOptions(args, 2, Set.of("package"));
    Path target = ProjectGenerator.create(Path.of(args[1]), options.getOrDefault("package", "com.example.cart"));
    output.println("Created " + target);
    output.println("With Java 27 and Maven installed, run:");
    output.println("  cd " + target);
    output.println("  mvn verify");
    output.println("  mvn exec:java");
    output.println("Install io.boostercloud:booster-java:" + VERSION + " in your local Maven repository first.");
    return 0;
  }

  private static int serve(Map<String, String> options, PrintStream output) throws Exception {
    int port = parsePort(options.getOrDefault("port", "3000"));
    Path directory = Path.of(options.getOrDefault("data", ".booster-java")).toAbsolutePath().normalize();
    Files.createDirectories(directory);
    CountDownLatch stopped = new CountDownLatch(1);
    try (Booster booster = ShoppingCartApplication.create(new JdbcEventStore(directory.resolve("events")));
         BoosterHttpServer server = new BoosterHttpServer(booster, port)) {
      server.start();
      Thread shutdown = new Thread(() -> {
        server.close();
        booster.close();
        stopped.countDown();
      }, "booster-shutdown");
      Runtime.getRuntime().addShutdownHook(shutdown);
      try {
        output.println("Booster shopping-cart example started on 127.0.0.1:" + server.port());
        output.println("GraphQL: /graphql; health: /health; event journal: " + directory);
        output.println("This example permits anonymous commands and queries. Configure access policies for your application.");
        output.flush();
        stopped.await();
      } finally {
        try { Runtime.getRuntime().removeShutdownHook(shutdown); }
        catch (IllegalStateException shutdownInProgress) { /* JVM shutdown owns the hook now. */ }
      }
    }
    return 0;
  }

  private static int replay(Map<String, String> options, PrintStream output) throws Exception {
    Path directory = Path.of(options.getOrDefault("data", ".booster-java")).toAbsolutePath().normalize();
    if (!Files.isRegularFile(directory.resolve("events.mv.db"))) {
      throw new IllegalArgumentException("No event journal exists in: " + directory);
    }
    try (JdbcEventStore store = new JdbcEventStore(directory.resolve("events"));
         Booster booster = ShoppingCartApplication.create(store)) {
      booster.rebuild();
      output.println("Replayed " + store.lastPosition() + " events and rebuilt shopping-cart projections.");
    }
    return 0;
  }

  static Map<String, String> parseOptions(String[] args, int offset, Set<String> allowed) {
    Map<String, String> values = new HashMap<>();
    for (int i = offset; i < args.length; i += 2) {
      String key = args[i];
      if (!key.startsWith("--") || !allowed.contains(key.substring(2))) {
        throw new IllegalArgumentException("Unknown option: " + key);
      }
      if (i + 1 >= args.length || args[i + 1].startsWith("--") || args[i + 1].isBlank()) {
        throw new IllegalArgumentException("Missing value for " + key);
      }
      if (values.putIfAbsent(key.substring(2), args[i + 1]) != null) {
        throw new IllegalArgumentException("Duplicate option: " + key);
      }
    }
    return Map.copyOf(values);
  }

  static int parsePort(String value) {
    try {
      int port = Integer.parseInt(value);
      if (port < 0 || port > 65535) throw new NumberFormatException();
      return port;
    } catch (NumberFormatException invalid) {
      throw new IllegalArgumentException("Port must be an integer between 0 and 65535");
    }
  }

  private static String help() {
    return """
        Booster Java — event-sourced applications on Java 27

        Usage: java -jar booster-java.jar <command> [options]

          new <directory> [--package com.example.cart]
              Generate a standalone Maven shopping-cart application and tests.
          demo [--port 3000] [--data .booster-java]
          start [--port 3000] [--data .booster-java]
              Run the bundled shopping-cart application with a durable H2 journal.
          replay [--data .booster-java]
              Replay the bundled application's journal and validate its projections.
          local synth --application com.example.Application [--config local.json] [--data .booster-java] [--port 3000]
          local start [--config local.json] [--detach true]
          local stop|status|rebuild [--config local.json]
          local nuke --config local.json --confirm-data <absolute-data-directory>
              Run and manage any LocalApplication; normal stops retain durable data.
          azure synth [--config azure.json] [--output azure/template.json]
          azure deploy|status [--config azure.json]
          azure nuke --config azure.json --confirm-resource-group <group-name>
              Synthesize ARM locally, deploy/update with Azure Identity, or explicitly remove owned resources.
          generate <kind> <Name> --package com.example [--project .] [--fields field:JavaType,...]
              Generate a command, event, entity, read-model, query, event-handler,
              scheduled-command, data-migration, schema-migration, reducer, projection or type.
          build|clean [--project .]
              Run Maven verification or remove build outputs for an application.
          version
          help

        Servers bind to loopback. Stop the server before replaying its H2 journal.
        Generated projects have their own Application main class; use mvn exec:java.
        """;
  }
}
