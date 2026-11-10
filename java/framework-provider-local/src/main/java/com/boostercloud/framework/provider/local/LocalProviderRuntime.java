package com.boostercloud.framework.provider.local;

import com.boostercloud.framework.provider.local.library.EventsAdapter;
import com.boostercloud.framework.provider.local.library.WebSocketServerAdapter;
import com.boostercloud.framework.provider.local.services.DispatchJournal;
import com.boostercloud.framework.provider.local.services.EventRegistry;
import com.boostercloud.framework.provider.local.services.GraphQLService;
import com.boostercloud.framework.provider.local.services.HealthService;
import com.boostercloud.framework.provider.local.services.ReadModelRegistry;
import com.boostercloud.framework.provider.local.services.WebSocketRegistry;
import com.boostercloud.framework.provider.local.store.CorruptDataFileException;
import com.boostercloud.framework.provider.local.store.Datastore;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.framework.types.UserApp;
import com.boostercloud.framework.types.UserAppRegistry;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The process-wide state of the local provider: the registries over the {@code .booster} files, the dispatch
 * journal, the GraphQL service and the websocket server. TypeScript creates these as module-level singletons when the
 * provider package is first required; {@link #shared()} is that singleton, created on first use, and
 * {@link #create(Options)} makes independent instances (tests, embedding).
 *
 * <p>The websocket server is created and started together with the first {@code LocalProvider} that uses the runtime
 * (TypeScript starts it in {@code Provider(...)}), at most once per runtime.
 */
public final class LocalProviderRuntime implements AutoCloseable {

  /**
   * How to build a runtime.
   *
   * @param baseDirectory the project directory holding {@code .booster}, or {@code null} for the working directory
   * @param startWebSocketServer whether the websocket server listens
   * @param webSocketPort the websocket port ({@code "0"} picks a free one)
   * @param userApp resolves the running application ({@code UserAppRegistry::get} by default)
   */
  public record Options(Path baseDirectory, boolean startWebSocketServer, String webSocketPort,
      Supplier<UserApp> userApp) {

    /** Canonical constructor with defaults. */
    public Options {
      webSocketPort = webSocketPort == null
          ? EnvironmentVariables.get(WebSocketServerAdapter.LOCAL_ENVIRONMENT_WEBSOCKET_SERVER_PORT)
              .orElse(WebSocketServerAdapter.DEFAULT_PORT)
          : webSocketPort;
      userApp = userApp == null ? UserAppRegistry::get : userApp;
    }

    /**
     * @return TypeScript's defaults: the working directory, the websocket server on
     *     {@code LOCAL_ENVIRONMENT_WEBSOCKET_SERVER_PORT} or 65529, the registered application
     */
    public static Options defaults() {
      return new Options(null, true, null, null);
    }

    /**
     * @param directory the project directory
     * @return a copy using that directory
     */
    public Options withBaseDirectory(Path directory) {
      return new Options(directory, startWebSocketServer, webSocketPort, userApp);
    }

    /**
     * @param start whether the websocket server listens
     * @param port its port
     * @return a copy with those websocket settings
     */
    public Options withWebSocketServer(boolean start, String port) {
      return new Options(baseDirectory, start, port, userApp);
    }

    /**
     * @param app resolves the application
     * @return a copy using that application
     */
    public Options withUserApp(Supplier<UserApp> app) {
      return new Options(baseDirectory, startWebSocketServer, webSocketPort, app);
    }
  }

  private static volatile LocalProviderRuntime shared;

  private final Options options;
  private final EventRegistry eventRegistry;
  private final ReadModelRegistry readModelRegistry;
  private final WebSocketRegistry connectionRegistry;
  private final WebSocketRegistry subscriptionRegistry;
  private final DispatchJournal dispatchJournal;
  private final GraphQLService graphQLService;
  private final HealthService healthService;
  private WebSocketServerAdapter webSocketServerAdapter;

  private LocalProviderRuntime(Options options) {
    this.options = Objects.requireNonNull(options, "options");
    Path base = options.baseDirectory();
    this.eventRegistry = new EventRegistry(Paths.resolve(base, Paths.EVENTS_DATABASE));
    this.readModelRegistry = new ReadModelRegistry(Paths.resolve(base, Paths.READ_MODELS_DATABASE));
    this.connectionRegistry = new WebSocketRegistry(Paths.resolve(base, Paths.CONNECTIONS_DATABASE));
    this.subscriptionRegistry = new WebSocketRegistry(Paths.resolve(base, Paths.SUBSCRIPTION_DATABASE));
    this.dispatchJournal = new DispatchJournal(Paths.resolve(base, Paths.DISPATCH_JOURNAL_DATABASE));
    this.graphQLService = new GraphQLService(options.userApp());
    this.healthService = new HealthService(options.userApp());
  }

  /**
   * @return the process-wide runtime with {@link Options#defaults()}, created on first use
   */
  public static LocalProviderRuntime shared() {
    LocalProviderRuntime current = shared;
    if (current == null) {
      synchronized (LocalProviderRuntime.class) {
        current = shared;
        if (current == null) {
          current = new LocalProviderRuntime(Options.defaults());
          shared = current;
        }
      }
    }
    return current;
  }

  /**
   * @param options how to build it
   * @return a new, independent runtime
   */
  public static LocalProviderRuntime create(Options options) {
    return new LocalProviderRuntime(options);
  }

  /**
   * @return the options
   */
  public Options options() {
    return options;
  }

  /**
   * @return the project directory, or {@code null} for the working directory
   */
  public Path baseDirectory() {
    return options.baseDirectory();
  }

  /**
   * @return the events and snapshots registry
   */
  public EventRegistry eventRegistry() {
    return eventRegistry;
  }

  /**
   * @return the read models registry
   */
  public ReadModelRegistry readModelRegistry() {
    return readModelRegistry;
  }

  /**
   * @return the websocket connections registry
   */
  public WebSocketRegistry connectionRegistry() {
    return connectionRegistry;
  }

  /**
   * @return the subscriptions registry
   */
  public WebSocketRegistry subscriptionRegistry() {
    return subscriptionRegistry;
  }

  /**
   * @return the dispatch journal
   */
  public DispatchJournal dispatchJournal() {
    return dispatchJournal;
  }

  /**
   * @return the GraphQL service
   */
  public GraphQLService graphQLService() {
    return graphQLService;
  }

  /**
   * @return the health service
   */
  public HealthService healthService() {
    return healthService;
  }

  /**
   * @return the running application
   */
  public UserApp userApp() {
    return options.userApp().get();
  }

  /**
   * The websocket server, created (and started, unless disabled) on first use.
   *
   * @return the websocket server adapter
   */
  public synchronized WebSocketServerAdapter webSocketServerAdapter() {
    if (webSocketServerAdapter == null) {
      webSocketServerAdapter = new WebSocketServerAdapter(graphQLService, this::loggingConfig, options.webSocketPort(),
          options.startWebSocketServer());
    }
    return webSocketServerAdapter;
  }

  private BoosterConfig loggingConfig() {
    try {
      return options.userApp().get().config();
    } catch (RuntimeException e) {
      return new BoosterConfig(EnvironmentVariables.boosterEnvironment().orElse("local"));
    }
  }

  /**
   * Re-dispatches the events stored before a crash whose dispatch never started (see
   * {@link EventsAdapter#recoverUndispatchedEvents}).
   *
   * @param config the configuration
   * @return the re-dispatched events
   */
  public List<EventEnvelope> recoverUndispatchedEvents(BoosterConfig config) {
    return EventsAdapter.recoverUndispatchedEvents(userApp(), eventRegistry, dispatchJournal, config);
  }

  /**
   * Java-only: loads now every data file of {@code .booster} that exists (events, read models, connections,
   * subscriptions and the dispatch journal), so that a damaged file is found when the application starts instead of by
   * the first request that touches it. Each file is replayed and compacted as on any load: a torn last line (a write
   * cut by a crash) is discarded and up to 10 % of other corrupt lines are skipped, with a warning. Files that do not
   * exist yet are left alone: they are still created on first use, as in TypeScript (Q19).
   *
   * @throws CorruptDataFileException when more than 10 % of the lines of a file are corrupt (NeDB's
   *     {@code corruptAlertThreshold}); that file is left untouched
   */
  public void loadExistingDataFiles() {
    if (hasDataFile(eventRegistry.events())) {
      eventRegistry.loadDatabaseIfNeeded();
    }
    if (hasDataFile(readModelRegistry.readModels())) {
      readModelRegistry.loadDatabaseIfNeeded();
    }
    if (hasDataFile(connectionRegistry.datastore())) {
      connectionRegistry.loadDatabaseIfNeeded();
    }
    if (hasDataFile(subscriptionRegistry.datastore())) {
      subscriptionRegistry.loadDatabaseIfNeeded();
    }
    if (hasDataFile(dispatchJournal.datastore())) {
      dispatchJournal.datastore().loadDatabaseIfNeeded();
    }
  }

  /** Whether the datastore's file, or the {@code <file>~} a crash during a compaction can leave, exists. */
  private static boolean hasDataFile(Datastore datastore) {
    Path file = datastore.filename();
    return file != null && (Files.exists(file) || Files.exists(Path.of(file + "~")));
  }

  /** Stops the websocket server and releases the data files. */
  @Override
  public synchronized void close() {
    if (webSocketServerAdapter != null) {
      webSocketServerAdapter.close();
    }
    eventRegistry.events().close();
    readModelRegistry.readModels().close();
    connectionRegistry.datastore().close();
    subscriptionRegistry.datastore().close();
    dispatchJournal.datastore().close();
    synchronized (LocalProviderRuntime.class) {
      if (shared == this) {
        shared = null;
      }
    }
  }
}
