package com.boostercloud.framework.core.integration;

import com.boostercloud.framework.types.ConsoleLogger;
import com.boostercloud.framework.types.Level;
import com.boostercloud.framework.types.Logger;
import java.util.ArrayList;
import java.util.List;

/**
 * The logger of the integration fixture application ({@code config.logger}): keeps every line so tests can check what
 * the framework logged where it swallows failures, and echoes them to the console when the system property
 * {@code booster.it.verbose} is {@code true}.
 */
public final class RecordingLogger implements Logger {

  /** The instance the fixture configuration installs. */
  public static final RecordingLogger INSTANCE = new RecordingLogger();

  private final List<Line> lines = new ArrayList<>();

  private RecordingLogger() {}

  /**
   * A logged line.
   *
   * @param level the level
   * @param text the formatted message and parameters
   * @param parameters the raw parameters
   */
  public record Line(Level level, String text, List<Object> parameters) {}

  /** Forgets the recorded lines. */
  public synchronized void clear() {
    lines.clear();
  }

  /**
   * @param level a level
   * @return the lines logged at that level, oldest first
   */
  public synchronized List<Line> lines(Level level) {
    return lines.stream().filter(line -> line.level() == level).toList();
  }

  /**
   * @return the errors logged so far
   */
  public List<Line> errors() {
    return lines(Level.ERROR);
  }

  private synchronized void record(Level level, Object message, Object... optionalParams) {
    String text = ConsoleLogger.format(message, optionalParams);
    List<Object> parameters = new ArrayList<>();
    parameters.add(message);
    for (Object parameter : optionalParams) {
      parameters.add(parameter);
    }
    lines.add(new Line(level, text, parameters));
    if (Boolean.getBoolean("booster.it.verbose")) {
      System.out.println(level + " " + text);
    }
  }

  @Override
  public void debug(Object message, Object... optionalParams) {
    record(Level.DEBUG, message, optionalParams);
  }

  @Override
  public void info(Object message, Object... optionalParams) {
    record(Level.INFO, message, optionalParams);
  }

  @Override
  public void warn(Object message, Object... optionalParams) {
    record(Level.WARN, message, optionalParams);
  }

  @Override
  public void error(Object message, Object... optionalParams) {
    record(Level.ERROR, message, optionalParams);
  }
}
