package com.boostercloud.framework.core.integration.app;

import com.boostercloud.framework.types.concepts.UUID;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * What the fixture application's handlers observed, for the tests to check (handlers may run on several threads, so
 * every list is thread-safe). {@link #reset()} runs before each test.
 */
public final class AppState {
  /** Simple names of every event the global event handler received, in arrival order. */
  public static final List<String> EVENTS = new CopyOnWriteArrayList<>();
  /** The carts the {@code CartAbandoned} handler received. */
  public static final List<UUID> ABANDONED_CARTS = new CopyOnWriteArrayList<>();
  /** The users of the registers given to the {@code CartAbandoned} handler. */
  public static final List<String> ABANDONED_BY = new CopyOnWriteArrayList<>();
  /** Messages of the errors the {@code onDispatchEventHandlerError} hook received. */
  public static final List<String> EVENT_HANDLER_ERRORS = new CopyOnWriteArrayList<>();
  /** Messages of the errors the {@code onError} hook received. */
  public static final List<String> ERRORS = new CopyOnWriteArrayList<>();
  /** Names of the data migrations that ran. */
  public static final List<String> MIGRATIONS = new CopyOnWriteArrayList<>();
  /** Whether the payments health indicator reports {@code UP}. */
  public static volatile boolean paymentsUp = true;

  private AppState() {}

  /** Forgets everything. */
  public static void reset() {
    EVENTS.clear();
    ABANDONED_CARTS.clear();
    ABANDONED_BY.clear();
    EVENT_HANDLER_ERRORS.clear();
    ERRORS.clear();
    MIGRATIONS.clear();
    paymentsUp = true;
  }
}
