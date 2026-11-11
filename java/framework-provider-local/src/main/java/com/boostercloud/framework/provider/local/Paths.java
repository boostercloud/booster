package com.boostercloud.framework.provider.local;

import java.nio.file.Path;

/**
 * The data files of the local provider. Port of {@code paths.ts}: every file lives in the {@code .booster} folder of
 * the project, and the paths are relative ({@code .booster/events.json}), resolved against the working directory of
 * the process (the project directory when the application is started with {@code boost start}) unless a base
 * directory is given to {@link #resolve(Path, String)}.
 */
public final class Paths {

  /** The folder holding every data file, relative to the project directory. */
  public static final String BOOSTER_FOLDER = ".booster";

  /** {@code registeredUsersDatabase} (declared by TypeScript, unused by the provider). */
  public static final String REGISTERED_USERS_DATABASE = internalPath("registered_users.json");

  /** {@code authenticatedUsersDatabase} (declared by TypeScript, unused by the provider). */
  public static final String AUTHENTICATED_USERS_DATABASE = internalPath("authenticated_users.json");

  /** {@code eventsDatabase}: events and entity snapshots. */
  public static final String EVENTS_DATABASE = internalPath("events.json");

  /** {@code readModelsDatabase}: every read model, of every type. */
  public static final String READ_MODELS_DATABASE = internalPath("read_models.json");

  /** {@code connectionsDatabase}: websocket connection data. */
  public static final String CONNECTIONS_DATABASE = internalPath("connections.json");

  /** {@code subscriptionDatabase}: GraphQL subscriptions. */
  public static final String SUBSCRIPTION_DATABASE = internalPath("subscriptions.json");

  /**
   * Java-only: events stored but not yet completely dispatched, used to re-dispatch them after a crash (see
   * {@code DispatchJournal}).
   */
  public static final String DISPATCH_JOURNAL_DATABASE = internalPath("dispatch_journal.json");

  /**
   * Java-only: the file a running server locks so that a second process cannot use the same data files (see
   * {@code LocalProviderRuntime.lockDataDirectory}).
   */
  public static final String LOCK_FILE = internalPath("booster.lock");

  private Paths() {}

  /** {@code path.normalize(path.join('.', '.booster', filename))}. */
  private static String internalPath(String filename) {
    return Path.of(".", BOOSTER_FOLDER, filename).normalize().toString();
  }

  /**
   * @param baseDirectory the project directory, or {@code null} for the working directory
   * @param relativePath one of the constants of this class
   * @return the path of the file
   */
  public static Path resolve(Path baseDirectory, String relativePath) {
    return baseDirectory == null ? Path.of(relativePath) : baseDirectory.resolve(relativePath);
  }
}
