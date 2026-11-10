package com.boostercloud.rocket.fileuploads.local.infrastructure;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.UserAppRegistry;
import com.boostercloud.framework.types.rockets.Rockets;
import com.boostercloud.rocket.fileuploads.local.LocalStorage;
import com.boostercloud.rocket.fileuploads.types.NodePath;
import com.boostercloud.rocket.fileuploads.types.RocketFilesParams;
import com.boostercloud.rocket.fileuploads.types.Utils;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Port of {@code fs-watch.ts}: watches {@code <project>/<storage>/<container>} recursively and notifies the rocket
 * function of every change under an allowed directory, the local counterpart of the Azure blob trigger.
 *
 * <p>For every change the path relative to the container ({@code client1/myfile.txt}) is checked as in TypeScript:
 * paths with a segment starting with a dot are ignored, and so are paths whose parent directory ({@code client1}) does
 * not match the directory patterns. The rest are dispatched, each on its own virtual thread and not awaited, as
 * {@code boosterRocketDispatcher({ BOOSTER_ROCKET_FUNCTION_ID: 'rocket-files', uri, name })} with
 * {@code uri = http://localhost:<port>/<storage>/<container>/<name>}. Creations, modifications, deletions and renames
 * are all dispatched (Node's {@code 'rename'} and {@code 'change'} events are handled alike), with no debouncing, so
 * one upload usually produces more than one notification.
 *
 * <p>Java implementation: the JDK {@link WatchService} with every directory of the tree registered (it is not
 * recursive); directories created later are registered when they appear and their existing entries are notified, so
 * a file written right after its directory was created is not missed. The service is native on Linux and Windows and
 * polls every 2 seconds on macOS, so notifications may arrive that much later than with Node. A failing dispatch is
 * logged (TypeScript leaves an unhandled promise rejection). {@link #close()} stops watching and waits for the
 * dispatches in flight.
 */
public final class FsWatch implements AutoCloseable {

  /** {@code /(^|[/\\])\../}: a path segment starting with a dot. */
  static final Pattern HIDDEN_SEGMENT = Pattern.compile("(^|[/\\\\])\\.");

  /** How long {@link #close()} waits for running dispatches. */
  static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(10);

  private final Path root;
  private final String storageName;
  private final String containerName;
  private final int port;
  private final List<String> directories;
  private final Consumer<Map<String, Object>> dispatcher;
  private final Logger logger;
  private final WatchService watchService;
  private final Map<WatchKey, Path> keys = new ConcurrentHashMap<>();
  private final Set<Thread> dispatches = ConcurrentHashMap.newKeySet();
  private final Thread loop;
  private volatile boolean closed;

  private FsWatch(BoosterConfig config, Path root, String storageName, String containerName, int port,
      List<String> directories, Consumer<Map<String, Object>> dispatcher) {
    this.root = root;
    this.storageName = storageName;
    this.containerName = containerName;
    this.port = port;
    this.directories = List.copyOf(directories);
    this.dispatcher = dispatcher;
    this.logger = Loggers.getLogger(config, "RocketFiles#fsWatch");
    try {
      Files.createDirectories(root);
      this.watchService = root.getFileSystem().newWatchService();
      registerTree(root, false);
    } catch (IOException e) {
      throw LocalStorage.nodeError(e, "watch", root);
    }
    this.loop = Thread.ofVirtual().name("rocket-files-watch-" + storageName + "-" + containerName).start(this::run);
  }

  /**
   * {@code fsWatch(storageName, containerName, port, directories)}: creates the container directory when it does not
   * exist and starts watching it; notifications go to the running application
   * ({@code UserAppRegistry.get().boosterRocketDispatcher(request)}).
   *
   * @param config the application configuration (for the logger)
   * @param projectDirectory the project directory
   * @param storageName the storage
   * @param containerName the container
   * @param port the port of the local server (for the {@code uri})
   * @param directories the directory patterns
   * @return the running watch
   */
  public static FsWatch fsWatch(BoosterConfig config, Path projectDirectory, String storageName, String containerName,
      int port, List<String> directories) {
    return fsWatch(config, projectDirectory, storageName, containerName, port, directories,
        request -> UserAppRegistry.get().boosterRocketDispatcher(request));
  }

  /**
   * {@link #fsWatch(BoosterConfig, Path, String, String, int, List)} with another dispatcher (tests, embedding).
   *
   * @param config the application configuration (for the logger)
   * @param projectDirectory the project directory
   * @param storageName the storage
   * @param containerName the container
   * @param port the port of the local server
   * @param directories the directory patterns
   * @param dispatcher receives the rocket function requests
   * @return the running watch
   */
  public static FsWatch fsWatch(BoosterConfig config, Path projectDirectory, String storageName, String containerName,
      int port, List<String> directories, Consumer<Map<String, Object>> dispatcher) {
    return new FsWatch(config, LocalStorage.containerDirectory(projectDirectory, storageName, containerName),
        storageName, containerName, port, directories, dispatcher);
  }

  /**
   * @return the watched directory
   */
  public Path root() {
    return root;
  }

  /**
   * The watch callback of {@code fs-watch.ts} for one change: filters the path and dispatches the notification.
   *
   * @param filename the path relative to the container, with the platform separator ({@code null} is ignored)
   * @return the dispatched request, or {@code null} when the change was ignored
   */
  Map<String, Object> onChange(String filename) {
    if (filename == null) {
      return null;
    }
    String dir = NodePath.parseDir(filename);
    if (HIDDEN_SEGMENT.matcher(filename).find()) {
      return null;
    }
    if (!Utils.isValidDirectory(dir, directories)) {
      return null;
    }
    String uri = "http://localhost:" + port + "/" + joinWithPlatformSeparator(storageName, containerName, filename);
    Map<String, Object> request = new LinkedHashMap<>();
    request.put(Rockets.ROCKET_FUNCTION_ID_ENV_VAR, RocketFilesParams.FUNCTION_ID);
    request.put("uri", uri);
    request.put("name", filename);
    dispatch(request);
    return request;
  }

  private void dispatch(Map<String, Object> request) {
    if (closed) {
      return;
    }
    Thread thread = Thread.ofVirtual().name("rocket-files-dispatch").unstarted(() -> {
      try {
        dispatcher.accept(request);
      } catch (RuntimeException | Error e) {
        logger.error("Error dispatching the file notification", request, e);
      } finally {
        dispatches.remove(Thread.currentThread());
      }
    });
    dispatches.add(thread);
    thread.start();
  }

  private static String joinWithPlatformSeparator(String storageName, String containerName, String filename) {
    String joined = NodePath.join(storageName, containerName, filename.replace(File.separatorChar, '/'));
    return File.separatorChar == '/' ? joined : joined.replace('/', File.separatorChar);
  }

  private void run() {
    while (!closed) {
      WatchKey key;
      try {
        key = watchService.take();
      } catch (InterruptedException | ClosedWatchServiceException e) {
        return;
      }
      Path directory = keys.get(key);
      if (directory != null) {
        for (WatchEvent<?> event : key.pollEvents()) {
          if (event.kind() == OVERFLOW || !(event.context() instanceof Path name)) {
            continue;
          }
          Path child = directory.resolve(name);
          handle(child);
          if (event.kind() == ENTRY_CREATE && Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
            try {
              registerTree(child, true);
            } catch (IOException | UncheckedIOException e) {
              logger.debug("Could not watch", child, e);
            }
          }
        }
      }
      if (!key.reset()) {
        keys.remove(key);
      }
    }
  }

  private void handle(Path changed) {
    if (closed) {
      return;
    }
    try {
      onChange(root.relativize(changed).toString());
    } catch (RuntimeException e) {
      logger.error("Error handling the file change", changed, e);
    }
  }

  /** Registers a directory and its sub-directories; for a new directory, notifies what it already contains. */
  private void registerTree(Path start, boolean notifyEntries) throws IOException {
    List<Path> entries = new ArrayList<>();
    try (Stream<Path> walk = Files.walk(start)) {
      walk.forEach(entries::add);
    }
    for (Path entry : entries) {
      if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
        try {
          keys.put(entry.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY), entry);
        } catch (ClosedWatchServiceException e) {
          return;
        }
      }
      if (notifyEntries && !entry.equals(start)) {
        handle(entry);
      }
    }
  }

  /** Stops watching and waits (up to 10 seconds) for the dispatches in flight. */
  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    try {
      watchService.close();
    } catch (IOException e) {
      logger.debug("Could not close the watch service", e);
    }
    loop.interrupt();
    long deadline = System.nanoTime() + CLOSE_TIMEOUT.toNanos();
    for (Thread thread : List.copyOf(dispatches)) {
      long remaining = deadline - System.nanoTime();
      if (remaining <= 0) {
        break;
      }
      try {
        thread.join(Duration.ofNanos(remaining));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }
}
