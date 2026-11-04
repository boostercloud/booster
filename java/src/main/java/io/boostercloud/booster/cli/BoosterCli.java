package io.boostercloud.booster.cli;

import io.boostercloud.booster.core.Booster;
import io.boostercloud.booster.example.ShoppingCartApplication;
import io.boostercloud.booster.store.JdbcEventStore;
import io.boostercloud.booster.transport.BoosterHttpServer;
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
          version
          help

        Servers bind to loopback. Stop the server before replaying its H2 journal.
        Generated projects have their own Application main class; use mvn exec:java.
        """;
  }
}
