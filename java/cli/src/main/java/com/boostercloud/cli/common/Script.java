package com.boostercloud.cli.common;

import com.boostercloud.cli.services.OraLogger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Function;

/**
 * A Script represents the steps of a booster command ({@code common/script.ts}). It stores the initial context and
 * makes it available to every step, printing a spinner line per step.
 *
 * <p>Scripts are immutable: every builder method returns a new script that runs the previous actions and then the
 * new one. Nothing runs until {@link #done()} is called: then the context is resolved first (before the initial
 * message is printed, as in TypeScript, where the context promise is already running), and the actions run
 * sequentially; the first failure stops the script.
 *
 * @param <C> the type of the context shared by the steps
 */
public final class Script<C> {
  /**
   * A step body.
   *
   * @param <C> the context type
   */
  @FunctionalInterface
  public interface Action<C> {
    /**
     * Runs the step.
     *
     * @param context the resolved context
     * @throws Exception any failure, which fails the step
     */
    void run(C context) throws Exception;
  }

  private final OraLogger logger;
  private final Callable<C> contextResolver;
  private final Map<String, Function<Throwable, String>> errorHandlers;
  private final List<Action<C>> actions;

  private Script(
      OraLogger logger,
      Callable<C> contextResolver,
      Map<String, Function<Throwable, String>> errorHandlers,
      List<Action<C>> actions) {
    this.logger = logger;
    this.contextResolver = contextResolver;
    this.errorHandlers = Collections.unmodifiableMap(errorHandlers);
    this.actions = List.copyOf(actions);
  }

  /**
   * Creates a script that prints a welcome message once its context is resolved.
   *
   * @param logger the spinner logger used for every message
   * @param initialMessage the message to show in the console
   * @param contextResolver computes the context of the script (parsing arguments, building the project...)
   * @param <C> the context type
   * @return the new script
   */
  public static <C> Script<C> init(OraLogger logger, String initialMessage, Callable<C> contextResolver) {
    List<Action<C>> actions = new ArrayList<>();
    actions.add(ctx -> logger.info(initialMessage));
    return new Script<>(logger, contextResolver, new LinkedHashMap<>(), actions);
  }

  /**
   * Adds a step: the message is shown with a spinner, the action runs with the context, and the spinner succeeds
   * (or fails, ending the script) with the same message.
   *
   * @param message the message of the spinner
   * @param action the step body
   * @return the new script
   */
  public Script<C> step(String message, Action<C> action) {
    return append(
        ctx -> {
          logger.start(message);
          try {
            action.run(ctx);
          } catch (Exception | Error e) {
            logger.fail(message);
            throw e;
          }
          logger.succeed(message);
        });
  }

  /**
   * Adds a step that is replaced by an informative {@code Skipping: <message>} line when {@code skipCondition} is
   * {@code true}.
   *
   * @param skipCondition when true, the action is skipped
   * @param message the message of the spinner
   * @param action the step body
   * @return the new script
   */
  public Script<C> optionalStep(boolean skipCondition, String message, Action<C> action) {
    if (skipCondition) {
      return info(Brand.mellancholize("Skipping: " + message));
    }
    return step(message, action);
  }

  /**
   * Adds a step that just prints a message with a blue {@code i} in front of it.
   *
   * @param message the message
   * @return the new script
   */
  public Script<C> info(String message) {
    return append(ctx -> logger.info(message));
  }

  /**
   * Adds a handler that formats the message of a specific type of error ({@code catch} in TypeScript). The type is
   * compared with {@link Errors#errorName(Throwable)}.
   *
   * @param errorType the error name, e.g. {@code IllegalStateException}
   * @param handler builds the message of the error thrown by {@link #done()}
   * @return the new script
   */
  public Script<C> catchError(String errorType, Function<Throwable, String> handler) {
    Map<String, Function<Throwable, String>> newHandlers = new LinkedHashMap<>(errorHandlers);
    newHandlers.put(errorType, handler);
    return new Script<>(logger, contextResolver, newHandlers, actions);
  }

  /**
   * Runs the script, handling any error that happens in between.
   *
   * @throws CliException when the context cannot be resolved or a step fails. Its message is produced by the
   *     handler registered for the error, or by default is the stack of the original error, which is kept as the
   *     cause.
   */
  public void done() {
    try {
      C context = contextResolver.call();
      for (Action<C> action : actions) {
        action.run(context);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new CliException(Errors.stackOf(e), e);
    } catch (Exception | Error e) {
      Function<Throwable, String> handler = errorHandlers.getOrDefault(Errors.errorName(e), Errors::stackOf);
      throw new CliException(handler.apply(e), e);
    }
  }

  private Script<C> append(Action<C> action) {
    List<Action<C>> newActions = new ArrayList<>(actions);
    newActions.add(action);
    return new Script<>(logger, contextResolver, new LinkedHashMap<>(errorHandlers), newActions);
  }
}
