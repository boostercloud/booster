package com.boostercloud.framework.provider.local.infrastructure;

import com.boostercloud.framework.provider.local.LocalProviderRuntime;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.provider.ProviderInfrastructure;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The {@link ProviderInfrastructure} of the local provider: the object TypeScript's {@code Infrastructure(rockets)}
 * returns. Only {@link #start(BoosterConfig, int)} is supported; {@code deploy}, {@code nuke} and {@code synth} fail
 * with the message the TypeScript CLI prints for a provider without them.
 */
public final class LocalInfrastructure implements ProviderInfrastructure {

  /** The message of the unsupported operations ({@code provider-service.ts}). */
  public static final String UNSUPPORTED_OPERATION_MESSAGE = "Attempted to perform the '%s' operation with a provider "
      + "that does not support this feature, please check your environment configuration.";

  /**
   * How the server is run.
   *
   * @param runtime the provider runtime to serve ({@link LocalProviderRuntime#shared()} by default, the one the
   *     application's provider uses)
   * @param closeRuntimeOnStop whether stopping the server closes the runtime (websocket server and data files)
   * @param schedulerClock the clock of the scheduler
   * @param shutdownTimeout how long a graceful shutdown waits for in-flight requests and running scheduled commands
   * @param registerShutdownHook whether {@link #start} stops the server on SIGINT/SIGTERM (a JVM shutdown hook)
   */
  public record Options(Supplier<LocalProviderRuntime> runtime, boolean closeRuntimeOnStop,
      SchedulerClock schedulerClock, Duration shutdownTimeout, boolean registerShutdownHook) {

    /** The default graceful shutdown timeout. */
    public static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

    /** Canonical constructor: fills the defaults for {@code null} values. */
    public Options {
      runtime = runtime == null ? LocalProviderRuntime::shared : runtime;
      schedulerClock = schedulerClock == null ? SchedulerClock.system() : schedulerClock;
      shutdownTimeout = shutdownTimeout == null ? DEFAULT_SHUTDOWN_TIMEOUT : shutdownTimeout;
    }

    /**
     * @return the shared runtime, closed on stop, the system clock, 5 s of grace, a shutdown hook
     */
    public static Options defaults() {
      return new Options(null, true, null, null, true);
    }

    /**
     * @param newRuntime the runtime to serve
     * @return a copy using it
     */
    public Options withRuntime(LocalProviderRuntime newRuntime) {
      Objects.requireNonNull(newRuntime, "runtime");
      return new Options(() -> newRuntime, closeRuntimeOnStop, schedulerClock, shutdownTimeout,
          registerShutdownHook);
    }

    /**
     * @param close whether stopping closes the runtime
     * @return a copy with that setting
     */
    public Options withCloseRuntimeOnStop(boolean close) {
      return new Options(runtime, close, schedulerClock, shutdownTimeout, registerShutdownHook);
    }

    /**
     * @param clock the scheduler clock
     * @return a copy using it
     */
    public Options withSchedulerClock(SchedulerClock clock) {
      return new Options(runtime, closeRuntimeOnStop, clock, shutdownTimeout, registerShutdownHook);
    }

    /**
     * @param timeout the graceful shutdown timeout
     * @return a copy using it
     */
    public Options withShutdownTimeout(Duration timeout) {
      return new Options(runtime, closeRuntimeOnStop, schedulerClock, timeout, registerShutdownHook);
    }

    /**
     * @param register whether {@link #start} installs a shutdown hook
     * @return a copy with that setting
     */
    public Options withShutdownHook(boolean register) {
      return new Options(runtime, closeRuntimeOnStop, schedulerClock, shutdownTimeout, register);
    }
  }

  private final List<LocalInfrastructureRocket> rockets;
  private final Options options;

  /**
   * @param rockets the loaded infrastructure rockets
   * @param options how the server runs
   */
  public LocalInfrastructure(List<LocalInfrastructureRocket> rockets, Options options) {
    this.rockets = rockets == null ? List.of() : List.copyOf(rockets);
    this.options = Objects.requireNonNull(options, "options");
  }

  /**
   * @return the rockets mounted on every server
   */
  public List<LocalInfrastructureRocket> rockets() {
    return rockets;
  }

  /**
   * @return the options
   */
  public Options options() {
    return options;
  }

  /**
   * {@code start(config, port)}: starts the local server and blocks until it stops. With the default options the
   * server stops gracefully on SIGINT/SIGTERM (any JVM shutdown), and then this method returns.
   *
   * @param config the application configuration
   * @param port the HTTP port
   */
  @Override
  public void start(BoosterConfig config, int port) {
    LocalServer server = startServer(config, port);
    Thread hook = null;
    if (options.registerShutdownHook()) {
      hook = Thread.ofPlatform().name("booster-local-shutdown").unstarted(server::close);
      Runtime.getRuntime().addShutdownHook(hook);
    }
    server.awaitStopped();
    if (hook != null) {
      try {
        Runtime.getRuntime().removeShutdownHook(hook);
      } catch (IllegalStateException e) {
        // the JVM is shutting down: the hook is running or ran already
      }
    }
  }

  /**
   * Starts the local server without blocking (tests, embedding).
   *
   * @param config the application configuration
   * @param port the HTTP port ({@code 0} picks a free one)
   * @return the running server
   * @throws IllegalStateException when the port cannot be bound
   */
  public LocalServer startServer(BoosterConfig config, int port) {
    return LocalServer.start(config, port, rockets, options);
  }

  /**
   * Not supported locally.
   *
   * @param config the configuration
   * @throws UnsupportedOperationException always
   */
  @Override
  public void deploy(BoosterConfig config) {
    throw unsupported(Operation.DEPLOY);
  }

  /**
   * Not supported locally.
   *
   * @param config the configuration
   * @throws UnsupportedOperationException always
   */
  @Override
  public void nuke(BoosterConfig config) {
    throw unsupported(Operation.NUKE);
  }

  /**
   * Not supported locally.
   *
   * @param config the configuration
   * @throws UnsupportedOperationException always
   */
  @Override
  public void synth(BoosterConfig config) {
    throw unsupported(Operation.SYNTH);
  }

  /**
   * @param operation an operation
   * @return {@code true} only for {@code START} (TypeScript's object has only {@code start})
   */
  @Override
  public boolean supports(Operation operation) {
    return operation == Operation.START;
  }

  private static UnsupportedOperationException unsupported(Operation operation) {
    return new UnsupportedOperationException(String.format(UNSUPPORTED_OPERATION_MESSAGE, operation.methodName()));
  }
}
