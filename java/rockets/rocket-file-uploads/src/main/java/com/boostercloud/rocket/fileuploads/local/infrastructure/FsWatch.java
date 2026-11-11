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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
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
 * a file written right after its directory was created is not missed (each directory is registered before it is
 * listed, so nothing created meanwhile falls in between); when the JDK drops events of a busy directory
 * ({@code OVERFLOW}) its entries are notified again. The service is native on Linux and Windows and
 * polls every 2 seconds on macOS, so notifications may arrive that much later than with Node. That is why the local
 * server also notifies its own uploads right away ({@link #changed(Path)}), so that an upload it acknowledged is never
 * left to a watch event that a stop could discard. A failing dispatch is logged (TypeScript leaves an unhandled
 * promise rejection). {@link #close()} first handles the changes the service already reported, then stops watching
 * and waits for the dispatches in flight.
 */
public final class FsWatch implements AutoCloseable {

  /**
   * {@code /(^|[/\\])\../}: a path segment starting with a dot followed by any character ({@code a/.} itself is not
   * hidden, {@code a/..} is).
   */
  static final Pattern HIDDEN_SEGMENT = Pattern.compile("(^|[/\\\\])\\.[^\\n\\r\\u2028\\u2029]");

  /** How long {@link #close()} waits for the reported changes and the running dispatches. */
  static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(10);

  /** How often the watch loop wakes up without changes (to see that {@link #close()} was called). */
  private static final Duration LOOP_WAKE_UP = Duration.ofMillis(200);

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
  /** Set by {@link #close()}: the loop handles what is already reported, then ends. */
  private volatile boolean closing;
  /** Set once the loop ended: no more dispatches (guarded by {@link #dispatches} for writes). */
  private volatile boolean closed;
  private volatile Consumer<Path> listed = directory -> {};

  /**
   * For tests: called with each directory of a tree being registered once its entries are listed, before they are
   * notified (to create a file "right after the listing").
   */
  void afterListing(Consumer<Path> hook) {
    this.listed = hook;
  }

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

  /**
   * Java-only: notifies a change the caller made itself under the container, right away instead of when the
   * {@link WatchService} reports it (up to 2 seconds later on macOS). The local server calls it for every upload
   * before answering, so the notification is already running when the client is told the upload succeeded, and
   * {@link #close()} waits for it: an acknowledged upload is notified even when the server stops right after. The
   * path goes through the filters of {@link #onChange}; the watch event still comes later (no debouncing, as in
   * TypeScript, so the upload gets one more notification). Nothing is dispatched once the watch is closed.
   *
   * @param file the file written or removed
   * @return the dispatched request, or {@code null} when ignored (filtered out, outside the container, closed)
   */
  public Map<String, Object> changed(Path file) {
    Path container = root.toAbsolutePath().normalize();
    Path changed = file.toAbsolutePath().normalize();
    if (closed || !changed.startsWith(container) || changed.equals(container)) {
      return null;
    }
    try {
      return onChange(container.relativize(changed).toString());
    } catch (RuntimeException e) {
      logger.error("Error handling the file change", changed, e);
      return null;
    }
  }

  private void dispatch(Map<String, Object> request) {
    Thread thread = Thread.ofVirtual().name("rocket-files-dispatch").unstarted(() -> {
      try {
        dispatcher.accept(request);
      } catch (RuntimeException | Error e) {
        logger.error("Error dispatching the file notification", request, e);
      } finally {
        dispatches.remove(Thread.currentThread());
      }
    });
    // started under the lock, so close() either refuses it or joins it
    synchronized (dispatches) {
      if (closed) {
        return;
      }
      dispatches.add(thread);
      thread.start();
    }
  }

  private static String joinWithPlatformSeparator(String storageName, String containerName, String filename) {
    String joined = NodePath.join(storageName, containerName, filename.replace(File.separatorChar, '/'));
    return File.separatorChar == '/' ? joined : joined.replace('/', File.separatorChar);
  }

  /**
   * The watch loop. Once {@link #close()} was called it handles the keys the service already reported (without
   * waiting for new ones) and ends, so that a change reported just before the stop is not thrown away with the
   * service.
   */
  private void run() {
    while (true) {
      WatchKey key;
      try {
        key = closing ? watchService.poll() : watchService.poll(LOOP_WAKE_UP.toMillis(), TimeUnit.MILLISECONDS);
      } catch (InterruptedException | ClosedWatchServiceException e) {
        return;
      }
      if (key == null) {
        if (closing) {
          return;
        }
        continue;
      }
      Path directory = keys.get(key);
      if (directory != null) {
        boolean overflowed = false;
        for (WatchEvent<?> event : key.pollEvents()) {
          if (event.kind() == OVERFLOW) {
            overflowed = true;
            continue;
          }
          if (!(event.context() instanceof Path name)) {
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
        if (overflowed) {
          rescan(directory);
        }
      }
      if (!key.reset()) {
        keys.remove(key);
      }
    }
  }

  /**
   * After an {@code OVERFLOW} (the JDK keeps at most 512 pending events per directory and drops the rest; Node's
   * recursive watch loses nothing in that case): notifies every entry of the directory again, and registers (and
   * notifies the contents of) the sub-directories that are not watched yet, so no upload goes unnoticed.
   */
  private void rescan(Path directory) {
    List<Path> children = new ArrayList<>();
    try (Stream<Path> list = Files.list(directory)) {
      list.forEach(children::add);
    } catch (IOException | UncheckedIOException e) {
      logger.debug("Could not rescan", directory, e);
      return;
    }
    for (Path child : children) {
      handle(child);
      if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS) && !keys.containsValue(child)) {
        try {
          registerTree(child, true);
        } catch (IOException | UncheckedIOException e) {
          logger.debug("Could not watch", child, e);
        }
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

  /**
   * Registers a directory and its sub-directories; for a new directory, notifies what it already contains (files and
   * folders, not following links). Each directory is registered <em>before</em> it is listed: an entry created after
   * the registration produces an event, one created before it is in the listing, so none falls in between (listing
   * first and registering later lost the files created meanwhile: inotify adds the watch after them, and the polling
   * service of macOS takes them into its first snapshot).
   *
   * @throws IOException when {@code start} cannot be registered or listed; a sub-directory removed meanwhile is
   *     skipped
   */
  private void registerTree(Path start, boolean notifyEntries) throws IOException {
    Deque<Path> pending = new ArrayDeque<>();
    pending.push(start);
    while (!pending.isEmpty()) {
      Path directory = pending.pop();
      List<Path> entries;
      try {
        keys.put(directory.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY), directory);
        try (Stream<Path> list = Files.list(directory)) {
          entries = list.toList();
        }
      } catch (ClosedWatchServiceException e) {
        return;
      } catch (IOException | UncheckedIOException e) {
        if (directory.equals(start)) {
          throw e;
        }
        logger.debug("Could not watch", directory, e);
        continue;
      }
      listed.accept(directory);
      for (Path entry : entries) {
        if (notifyEntries) {
          handle(entry);
        }
        if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
          pending.push(entry);
        }
      }
    }
  }

  /**
   * Stops watching, within 10 seconds in all: the watch loop first handles the changes the service already reported
   * (without waiting for new ones; closing the service would throw them away), then the service is closed, further
   * notifications are refused and the dispatches in flight are awaited. Changes made by other means that the service
   * has not reported yet (the last 2 seconds on macOS) are not notified, and the next start notifies nothing, as in
   * TypeScript; the local server's own uploads do not depend on it ({@link #changed(Path)}).
   */
  @Override
  public void close() {
    synchronized (this) {
      if (closing) {
        return;
      }
      closing = true;
    }
    long deadline = System.nanoTime() + CLOSE_TIMEOUT.toNanos();
    boolean interrupted = false;
    if (Thread.currentThread() != loop) {
      try {
        loop.join(Duration.ofNanos(Math.max(1, deadline - System.nanoTime())));
      } catch (InterruptedException e) {
        interrupted = true;
      }
    }
    synchronized (dispatches) {
      closed = true;
    }
    try {
      watchService.close();
    } catch (IOException e) {
      logger.debug("Could not close the watch service", e);
    }
    loop.interrupt();
    for (Thread thread : List.copyOf(dispatches)) {
      long remaining = deadline - System.nanoTime();
      if (interrupted || remaining <= 0) {
        break;
      }
      try {
        thread.join(Duration.ofNanos(remaining));
      } catch (InterruptedException e) {
        interrupted = true;
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }
}
