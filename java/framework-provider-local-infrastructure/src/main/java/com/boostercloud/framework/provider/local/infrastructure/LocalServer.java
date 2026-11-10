package com.boostercloud.framework.provider.local.infrastructure;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.provider.local.LocalProviderRuntime;
import com.boostercloud.framework.provider.local.Paths;
import com.boostercloud.framework.provider.local.infrastructure.controllers.GraphQLController;
import com.boostercloud.framework.provider.local.infrastructure.controllers.HealthController;
import com.boostercloud.framework.provider.local.infrastructure.web.BodyParsers;
import com.boostercloud.framework.provider.local.infrastructure.web.Cors;
import com.boostercloud.framework.provider.local.infrastructure.web.ErrorHandler;
import com.boostercloud.framework.provider.local.infrastructure.web.HttpServer;
import com.boostercloud.framework.provider.local.infrastructure.web.Router;
import com.boostercloud.framework.provider.local.library.WebSocketServerAdapter;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.InternalInfo;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A running local server: what {@code Infrastructure(...).start(config, port)} sets up in TypeScript, as an object that
 * can be stopped. Created by {@link LocalInfrastructure#startServer(BoosterConfig, int)}.
 *
 * <p>Start-up, in order:
 * <ol>
 *   <li>the router: {@code /sensor/health} ({@link HealthController}), {@code /graphql} ({@link GraphQLController});
 *   <li>the application: JSON bodies up to 6 MB (raw bytes kept as {@code rawBody}), urlencoded bodies up to 6 MB,
 *       {@code cors()}, the headers {@code Access-Control-Allow-Origin: *} and
 *       {@code Access-Control-Allow-Headers: Origin, X-Requested-With, Content-Type, Accept}, the router, and the
 *       default error handler ({@code { title, reason }} with the status of the error class);
 *   <li>the port is bound (failing fast when it is taken), and {@code BOOSTER_INTERNAL_LOCAL_PORT} is set to it
 *       in-process when it differs (port 0);
 *   <li>each rocket's {@code mountStack(config, router, { port })}, in order, on the same router;
 *   <li>Java-only: the existing data files are loaded ({@code LocalProviderRuntime.loadExistingDataFiles}): a torn
 *       last line or a few corrupt lines are skipped with a warning, a file with more than 10 % of corrupt lines fails
 *       the start ({@code CorruptDataFileException}, logged, the port released);
 *   <li>Java-only: {@code recoverUndispatchedEvents} re-dispatches the events a crash left undispatched (errors are
 *       logged and do not stop the start);
 *   <li>the provider's websocket server is started if it is not running yet ({@code ws://localhost:65529/websocket});
 *   <li>the HTTP server starts serving, then the {@link Scheduler} is configured (TypeScript does it in the
 *       {@code listen} callback) and the line {@code Local server listening on http://localhost:<port> ...} is logged.
 * </ol>
 *
 * <p>{@link #close()} is the graceful shutdown (also run on SIGINT/SIGTERM by {@link LocalInfrastructure#start}):
 * the scheduler stops and its running commands are awaited, the HTTP server stops accepting connections and lets the
 * in-flight requests finish, the rockets are unmounted, and the provider runtime (websocket server and data files) is
 * closed.
 */
public final class LocalServer implements AutoCloseable {

  /** The body size limit of the JSON and urlencoded parsers ({@code '6mb'}). */
  public static final long BODY_LIMIT = BodyParsers.parseBytes("6mb");

  /** The {@code Access-Control-Allow-Headers} value the server adds to every response. */
  public static final String ALLOWED_HEADERS = "Origin, X-Requested-With, Content-Type, Accept";

  private final BoosterConfig config;
  private final LocalProviderRuntime runtime;
  private final LocalInfrastructure.Options options;
  private final List<LocalInfrastructureRocket> rockets;
  private final List<LocalInfrastructureRocket> mountedRockets = new ArrayList<>();
  private final Router router = new Router();
  private final Router application = new Router();
  private final HttpServer httpServer;
  private final CountDownLatch stopped = new CountDownLatch(1);
  private final Logger logger;
  private Scheduler scheduler;
  private List<EventEnvelope> recoveredEvents = List.of();
  private int port = -1;
  private boolean closing;

  private LocalServer(BoosterConfig config, int requestedPort, List<LocalInfrastructureRocket> rockets,
      LocalInfrastructure.Options options) {
    this.config = Objects.requireNonNull(config, "config");
    this.options = Objects.requireNonNull(options, "options");
    this.rockets = List.copyOf(rockets);
    this.runtime = Objects.requireNonNull(options.runtime().get(), "runtime");
    this.logger = Loggers.getLogger(config, "LocalInfrastructure#start");
    router.use("/sensor/health", new HealthController(runtime.healthService()).router());
    router.use("/graphql", new GraphQLController(runtime.graphQLService()).router());
    application.use(BodyParsers.json(BODY_LIMIT, (request, response, body) -> request.rawBody(body)));
    application.use(BodyParsers.urlencoded(BODY_LIMIT));
    application.use(Cors.middleware());
    application.use((request, response, next) -> {
      response.header("Access-Control-Allow-Origin", "*");
      response.header("Access-Control-Allow-Headers", ALLOWED_HEADERS);
      next.next();
    });
    application.use(router);
    application.use(defaultErrorHandler());
    this.httpServer = new HttpServer(requestedPort, application, options.shutdownTimeout(),
        () -> Loggers.getLogger(config, "local-infrastructure#finalHandler"));
  }

  /**
   * {@code defaultErrorHandler}: when nothing was sent yet, logs the error and answers with
   * {@link Http#requestFailed}; otherwise passes it on.
   */
  private ErrorHandler defaultErrorHandler() {
    Logger errorLogger = Loggers.getLogger(config, "local-infrastructure#defaultErrorHandler");
    return (error, request, response, next) -> {
      if (response.headersSent()) {
        next.next(error);
        return;
      }
      errorLogger.error(error);
      Http.requestFailed(error, response);
    };
  }

  static LocalServer start(BoosterConfig config, int requestedPort, List<LocalInfrastructureRocket> rockets,
      LocalInfrastructure.Options options) {
    LocalServer server = new LocalServer(config, requestedPort, rockets, options);
    try {
      server.startUp();
    } catch (RuntimeException | Error e) {
      server.abortStartUp();
      throw e;
    }
    return server;
  }

  private void startUp() {
    port = httpServer.open();
    if (!Integer.toString(port).equals(InternalInfo.boosterLocalPort())) {
      InternalInfo.setBoosterLocalPort(port);
    }
    InfrastructureRocketMetadata metadata = new InfrastructureRocketMetadata(port);
    for (LocalInfrastructureRocket rocket : rockets) {
      rocket.mountStack(config, router, metadata);
      mountedRockets.add(rocket);
    }
    try {
      runtime.loadExistingDataFiles();
    } catch (RuntimeException e) {
      logger.error("Could not load the data files of "
          + Paths.resolve(runtime.baseDirectory(), Paths.BOOSTER_FOLDER).toAbsolutePath()
          + "; the server does not start: " + e.getMessage());
      throw e;
    }
    try {
      recoveredEvents = runtime.recoverUndispatchedEvents(config);
      if (!recoveredEvents.isEmpty()) {
        logger.info("Re-dispatched " + recoveredEvents.size() + " event(s) that were stored but not dispatched "
            + "before the application stopped");
      }
    } catch (RuntimeException e) {
      logger.error("Could not re-dispatch the undispatched events; they will be retried on the next start", e);
    }
    WebSocketServerAdapter webSocketServer = runtime.webSocketServerAdapter();
    httpServer.start();
    scheduler = Scheduler.configureScheduler(config, runtime::userApp, options.schedulerClock());
    logger.info("Local server listening on " + url() + " (GraphQL: " + graphqlURL() + ", health: " + healthURL()
        + ", websocket: " + (webSocketServer.isRunning() ? websocketURL() : "not running") + ")");
  }

  private void abortStartUp() {
    httpServer.stop();
    unmountRockets();
    stopped.countDown();
  }

  /**
   * @return the HTTP port
   */
  public int port() {
    return port;
  }

  /**
   * @return {@code http://localhost:<port>}
   */
  public String url() {
    return "http://localhost:" + port;
  }

  /**
   * @return {@code http://localhost:<port>/graphql}
   */
  public String graphqlURL() {
    return url() + "/graphql";
  }

  /**
   * @return {@code http://localhost:<port>/sensor/health/}
   */
  public String healthURL() {
    return url() + "/sensor/health/";
  }

  /**
   * @return {@code ws://localhost:<websocket port>/websocket}
   */
  public String websocketURL() {
    int webSocketPort = runtime.webSocketServerAdapter().port();
    return "ws://localhost:" + (webSocketPort < 0 ? runtime.options().webSocketPort() : webSocketPort)
        + WebSocketServerAdapter.PATH;
  }

  /**
   * @return the router with the health, GraphQL and rocket routes
   */
  public Router router() {
    return router;
  }

  /**
   * @return the whole application (body parsers, CORS, router, error handler)
   */
  public Router application() {
    return application;
  }

  /**
   * @return the scheduler
   */
  public Scheduler scheduler() {
    return scheduler;
  }

  /**
   * @return the provider runtime the server uses
   */
  public LocalProviderRuntime runtime() {
    return runtime;
  }

  /**
   * @return the configuration
   */
  public BoosterConfig config() {
    return config;
  }

  /**
   * @return the events re-dispatched at start-up
   */
  public List<EventEnvelope> recoveredEvents() {
    return recoveredEvents;
  }

  /**
   * @return whether the server is serving
   */
  public boolean isRunning() {
    return httpServer.isRunning() && stopped.getCount() > 0;
  }

  /**
   * Graceful shutdown; idempotent. Returns once everything is stopped.
   */
  @Override
  public void close() {
    synchronized (this) {
      if (closing) {
        awaitStopped();
        return;
      }
      closing = true;
    }
    try {
      logger.info("Stopping the local server on " + url());
      if (scheduler != null && !scheduler.close(options.shutdownTimeout())) {
        logger.warn("Some scheduled commands were still running when the server stopped");
      }
      httpServer.stop();
      unmountRockets();
      if (options.closeRuntimeOnStop()) {
        runtime.close();
      }
      logger.info("Local server stopped");
    } finally {
      stopped.countDown();
    }
  }

  private void unmountRockets() {
    for (LocalInfrastructureRocket rocket : mountedRockets) {
      try {
        rocket.unmountStack();
      } catch (RuntimeException e) {
        logger.error("Could not unmount the rocket " + rocket.getClass().getName(), e);
      }
    }
    mountedRockets.clear();
  }

  /**
   * Blocks until the server is stopped (by {@link #close()} or a signal). Returns early, keeping the interrupt flag,
   * when the thread is interrupted.
   */
  public void awaitStopped() {
    try {
      stopped.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * @param timeout the maximum wait
   * @return whether the server stopped in time
   */
  public boolean awaitStopped(Duration timeout) {
    try {
      return stopped.await(timeout.toNanos(), TimeUnit.NANOSECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }
}
