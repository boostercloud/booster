package io.boostercloud.booster.local;

import io.boostercloud.booster.store.JdbcEventStore;
import io.boostercloud.booster.store.JdbcStateStore;
import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Durable local services, with exclusive process ownership of the application directory. */
public final class LocalProvider implements AutoCloseable {
  static final String MARKER = ".booster-local-provider";
  private final Path directory;
  private final FileChannel lockChannel;
  private final FileLock lock;
  private final JdbcEventStore events;
  private final JdbcStateStore state;
  private final List<AutoCloseable> workers = new ArrayList<>();
  private boolean closed;
  public LocalProvider(Path directory) throws IOException {
    this.directory = directory.toAbsolutePath().normalize();
    verifyNoLinkedAncestors(this.directory);
    Path lifecycleLock = lifecycleLockPath(this.directory);
    Files.createDirectories(lifecycleLock.getParent());
    verifyNoLinkedAncestors(lifecycleLock.getParent());
    // This inode survives data cleanup; replacing a lock inside the deleted directory
    // would permit old and new processes to hold different locks for the same path.
    lockChannel = FileChannel.open(lifecycleLock, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
    try {
      lock = lockChannel.tryLock();
      if (lock == null) throw new IOException("Another process owns the local provider: " + this.directory);
    } catch (IOException | OverlappingFileLockException failure) {
      lockChannel.close(); throw new IOException("Another process owns the local provider: " + this.directory, failure);
    }
    JdbcEventStore eventStore = null;
    try {
      Files.createDirectories(this.directory);
      if (!Files.exists(this.directory.resolve(MARKER), LinkOption.NOFOLLOW_LINKS)) {
        try (var children = Files.list(this.directory)) {
          if (children.findAny().isPresent())
            throw new IOException("Refusing to adopt a nonempty directory without a local provider marker: " + this.directory);
        }
        Files.writeString(this.directory.resolve(MARKER), "booster-java-local-v1\n", StandardOpenOption.CREATE_NEW);
      }
      verifyOwned(this.directory);
      rejectLinkedDatabaseFiles(this.directory);
      eventStore = new JdbcEventStore(this.directory.resolve("events"));
      state = new JdbcStateStore(this.directory.resolve("state")); events = eventStore;
    } catch (IOException | RuntimeException failure) {
      try { if (eventStore != null) eventStore.close(); }
      catch (RuntimeException closing) { failure.addSuppressed(closing); }
      try { lock.release(); } catch (IOException closing) { failure.addSuppressed(closing); }
      try { lockChannel.close(); } catch (IOException closing) { failure.addSuppressed(closing); }
      throw failure;
    }
  }
  /** Stable lock retained after nuke, shared by creation, runtime ownership and cleanup. */
  static Path lifecycleLockPath(Path directory) {
    Path normalized = directory.toAbsolutePath().normalize();
    if (normalized.getParent() == null) throw new IllegalArgumentException("Filesystem root cannot be a local data directory");
    try {
      String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(normalized.toString().getBytes(StandardCharsets.UTF_8)));
      return normalized.getParent().resolve(".booster-java-" + digest + ".lock");
    } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
  }
  static void verifyOwned(Path directory) throws IOException {
    verifyNoLinkedAncestors(directory.toAbsolutePath().normalize());
    Path marker = directory.resolve(MARKER);
    if (Files.isSymbolicLink(directory) || Files.isSymbolicLink(marker) || !Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)
        || Files.size(marker) > 64 || !Files.readString(marker).equals("booster-java-local-v1\n"))
      throw new IOException("Directory is not owned by the Booster local provider: " + directory);
  }
  private static void verifyNoLinkedAncestors(Path directory) throws IOException {
    for (Path current = directory; current != null; current = current.getParent())
      if (Files.isSymbolicLink(current)) throw new IOException("Local data path must not contain symbolic links: " + current);
  }
  private static void rejectLinkedDatabaseFiles(Path directory) throws IOException {
    try (var children = Files.list(directory)) {
      for (Path file : children.toList()) {
        String name = file.getFileName().toString();
        if ((name.equals("events") || name.startsWith("events.") || name.equals("state") || name.startsWith("state.")
            || name.equals("runtime.log") || name.equals(".runtime.json"))
            && Files.isSymbolicLink(file)) throw new IOException("Local database files must not be symbolic links: " + file);
      }
    }
  }
  public Path directory() { return directory; }
  public JdbcEventStore eventStore() { return events; }
  public JdbcStateStore stateStore() { return state; }
  public synchronized <T extends AutoCloseable> T own(T worker) {
    if (closed) throw new IllegalStateException("Local provider is closed"); workers.add(Objects.requireNonNull(worker)); return worker;
  }
  /** Stop provider-owned background work before closing the runtime/journal. Safe to call more than once. */
  public synchronized void stopWorkers() {
    RuntimeException failure = null;
    var closing = new ArrayList<>(workers); workers.clear();
    for (var worker : closing.reversed()) try { worker.close(); }
    catch (Exception error) {
      if (failure == null) failure = new IllegalStateException("Local worker shutdown failed", error);
      else failure.addSuppressed(error);
    }
    if (failure != null) throw failure;
  }
  @Override public synchronized void close() {
    if (closed) return; closed = true;
    RuntimeException failure = null;
    try { stopWorkers(); } catch (RuntimeException error) { failure = error; }
    try { state.close(); } catch (RuntimeException error) { if (failure == null) failure = error; }
    try { events.close(); } catch (RuntimeException error) { if (failure == null) failure = error; }
    try { lock.release(); } catch (IOException error) { if (failure == null) failure = new IllegalStateException(error); }
    try { lockChannel.close(); } catch (IOException error) { if (failure == null) failure = new IllegalStateException(error); }
    if (failure != null) throw failure;
  }
}
