package io.boostercloud.booster.local;

import com.fasterxml.jackson.databind.JsonNode;
import io.boostercloud.booster.core.Json;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/** Local lifecycle independent of the bundled demo and of cloud accounts. */
public final class LocalInfrastructure {
  private LocalInfrastructure() {}
  public static Path synth(LocalConfiguration config, Path target) throws IOException {
    target = target.toAbsolutePath().normalize(); Files.createDirectories(target.getParent());
    Files.writeString(target, Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(config) + "\n", StandardOpenOption.CREATE_NEW);
    return target;
  }
  public static JsonNode status(LocalConfiguration config) throws Exception {
    var metadata = metadata(config);
    try (var client = http()) {
      var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port(metadata) + "/__booster/status"))
          .header("Authorization", "Bearer " + metadata.path("token").asText())
          .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
      try (var body = response.body()) {
        if (response.statusCode() != 200) throw new IOException("Local application health returned HTTP " + response.statusCode());
        byte[] bytes = body.readNBytes(1_048_577);
        if (bytes.length > 1_048_576) throw new IOException("Local health response exceeds 1 MiB");
        var health = Json.MAPPER.readTree(bytes);
        return Json.MAPPER.valueToTree(Map.of("pid", metadata.path("pid").asLong(), "port", port(metadata),
            "applicationClass", metadata.path("applicationClass").asText(), "health", health));
      }
    }
  }
  public static void stop(LocalConfiguration config) throws Exception {
    var metadata = metadata(config);
    try (var client = http()) {
      var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port(metadata) + "/__booster/shutdown"))
          .timeout(Duration.ofSeconds(5)).header("Authorization", "Bearer " + metadata.path("token").asText())
          .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
      if (response.statusCode() != 202) throw new IOException("Local shutdown rejected with HTTP " + response.statusCode());
    }
    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    while (System.nanoTime() < deadline) {
      if (!Files.exists(config.directory().resolve(LocalHost.RUNTIME_FILE)) && unlocked(config.directory())) return;
      Thread.sleep(50);
    }
    throw new IOException("Local shutdown has not completed; inspect runtime.log before retrying");
  }
  public static long startDetached(Path configFile) throws Exception {
    configFile = configFile.toAbsolutePath().normalize();
    var config = LocalConfiguration.read(configFile);
    // Initialize/validate ownership before creating the log; fail without touching foreign directories.
    try (var ignored = new LocalProvider(config.directory())) {
      // Exclusive lock proves no host owns this directory, so metadata from a crashed process is stale.
      Files.deleteIfExists(config.directory().resolve(LocalHost.RUNTIME_FILE));
    }
    List<String> command = new ArrayList<>();
    command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
    for (String property : List.of("javax.net.ssl.trustStore", "javax.net.ssl.trustStoreType")) {
      String value = System.getProperty(property); if (value != null) command.add("-D" + property + "=" + value);
    }
    command.addAll(List.of("-cp", runtimeClasspath(), "io.boostercloud.booster.cli.BoosterCli",
        "local", "start", "--config", configFile.toString()));
    Process process = new ProcessBuilder(command).redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.appendTo(config.directory().resolve("runtime.log").toFile())).start();
    try {
      long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
      while (System.nanoTime() < deadline) {
        if (!process.isAlive()) throw new IOException("Local startup exited with " + process.exitValue() + "; inspect " + config.directory().resolve("runtime.log"));
        if (Files.exists(config.directory().resolve(LocalHost.RUNTIME_FILE))) {
          var started = metadata(config);
          if (started.path("pid").asLong() != process.pid())
            throw new IOException("Another process won the local startup race; use local status");
          status(config); return process.pid();
        }
        Thread.sleep(50);
      }
      throw new IOException("Local application did not become healthy within 30 seconds; inspect runtime.log");
    } catch (Exception failure) {
      process.destroy();
      try { if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly().waitFor(5, java.util.concurrent.TimeUnit.SECONDS); }
      catch (InterruptedException interrupted) { process.destroyForcibly(); Thread.currentThread().interrupt(); }
      throw failure;
    }
  }
  public static long rebuild(LocalConfiguration config) throws Exception {
    var application = config.instantiate();
    try (var provider = new LocalProvider(config.directory()); var runtime = application.create(provider);
         AutoCloseable workerShutdown = provider::stopWorkers) {
      runtime.rebuild(); return runtime.position();
    }
  }
  public static void nuke(LocalConfiguration config, Path confirmedDirectory) throws IOException {
    Path directory = config.directory();
    if (!directory.equals(confirmedDirectory.toAbsolutePath().normalize()))
      throw new IllegalArgumentException("Cleanup requires --confirm-data " + directory);
    LocalProvider.verifyOwned(directory);
    Path lockPath = LocalProvider.lifecycleLockPath(directory);
    try (var channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
      try (var lock = channel.tryLock()) {
        if (lock == null) throw new IOException("Local provider is still in use");
        try (var paths = Files.walk(directory)) {
          for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
            Files.delete(path);
          }
        }
      } catch (OverlappingFileLockException active) { throw new IOException("Local provider is still in use", active); }
    }
    // The stable lock inode remains outside the removed directory so a concurrent start
    // cannot acquire a new inode while cleanup still owns the previous lock.
  }
  private static JsonNode metadata(LocalConfiguration config) throws IOException {
    LocalProvider.verifyOwned(config.directory());
    Path path = config.directory().resolve(LocalHost.RUNTIME_FILE);
    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > 16_384) throw new IOException("Local application is not running (no runtime metadata)");
    var metadata = Json.MAPPER.readTree(Files.readString(path));
    if (!config.applicationClass().equals(metadata.path("applicationClass").asText()) || !metadata.path("token").asText().matches("[A-Za-z0-9_-]{43}"))
      throw new IOException("Local runtime metadata does not match this application");
    port(metadata); return metadata;
  }
  private static int port(JsonNode metadata) throws IOException {
    int port = metadata.path("port").asInt(-1); if (port < 1 || port > 65535) throw new IOException("Invalid local runtime port"); return port;
  }
  private static HttpClient http() { return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build(); }
  /** Maven exec and application launchers can place domain classes in a context URLClassLoader. */
  private static String runtimeClasspath() {
    var entries = new LinkedHashSet<String>();
    for (ClassLoader loader = Thread.currentThread().getContextClassLoader(); loader != null; loader = loader.getParent()) {
      if (loader instanceof java.net.URLClassLoader urls) for (var url : urls.getURLs()) {
        if (url.getProtocol().equals("file")) {
          try { entries.add(Path.of(url.toURI()).toString()); }
          catch (java.net.URISyntaxException invalid) { throw new IllegalStateException("Invalid application classpath", invalid); }
        }
      }
    }
    entries.addAll(Arrays.asList(System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator))));
    return String.join(File.pathSeparator, entries);
  }
  private static boolean unlocked(Path directory) throws IOException {
    try (var channel = FileChannel.open(LocalProvider.lifecycleLockPath(directory), StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
      try (var lock = channel.tryLock()) { return lock != null; }
      catch (OverlappingFileLockException active) { return false; }
    }
  }
}
