package com.boostercloud.framework.common;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.ConsoleLogger;
import com.boostercloud.framework.types.Level;
import com.boostercloud.framework.types.Logger;
import java.util.Objects;

/**
 * Port of {@code logger.ts} ({@code getLogger}); named {@code Loggers} because {@code Logger} is the interface.
 *
 * <p>The returned logger passes a prefix as the <b>first argument</b> of the sink, followed by the caller's arguments
 * unchanged: {@code getLogger(config, "EventStore#store").debug("x", envelope)} calls
 * {@code sink.debug("[Booster]|EventStore#store: ", "x", envelope)}. The sink is {@code config.logger}, or
 * {@link ConsoleLogger} when it is {@code null}. Levels are a snapshot taken at creation: {@code debug} is active iff
 * {@code config.logLevel <= DEBUG}, {@code info} iff {@code <= INFO}, {@code warn} iff {@code <= WARN};
 * {@code error} is always active.
 */
public final class Loggers {

  /** Prefix used when neither an override nor {@code config.logPrefix} is set. */
  public static final String DEFAULT_LOG_PREFIX = "Booster";

  private Loggers() {}

  /**
   * @param config the configuration
   * @return a logger with prefix {@code [<prefix>]: }
   */
  public static Logger getLogger(BoosterConfig config) {
    return getLogger(config, null, null);
  }

  /**
   * @param config the configuration
   * @param location where the logger is used ({@code "ClassName#method"}); empty or {@code null} for none
   * @return a logger with prefix {@code [<prefix>]|<location>: }
   */
  public static Logger getLogger(BoosterConfig config, String location) {
    return getLogger(config, location, null);
  }

  /**
   * {@code getLogger(config, location?, overridenLogPrefix?)}.
   *
   * @param config the configuration
   * @param location where the logger is used, or {@code null}
   * @param overriddenLogPrefix prefix that wins over {@code config.logPrefix} (an empty string is kept), or {@code null}
   * @return the logger
   */
  public static Logger getLogger(BoosterConfig config, String location, String overriddenLogPrefix) {
    Objects.requireNonNull(config, "config");
    Logger sink = config.logger != null ? config.logger : ConsoleLogger.INSTANCE;
    String logPrefix = overriddenLogPrefix != null
        ? overriddenLogPrefix
        : config.logPrefix != null ? config.logPrefix : DEFAULT_LOG_PREFIX;
    String locationText = location != null && !location.isEmpty() ? "|" + location + ": " : ": ";
    String prefix = "[" + logPrefix + "]" + locationText;
    Level level = config.logLevel == null ? Level.DEBUG : config.logLevel;
    return new PrefixedLogger(sink, prefix, level.enables(Level.DEBUG), level.enables(Level.INFO), level.enables(Level.WARN));
  }

  /**
   * @param config the configuration
   * @param location where the logger is used, or {@code null}
   * @return the prefix {@code getLogger} would use, e.g. {@code "[Booster]|EventStore#store: "}
   */
  public static String prefixFor(BoosterConfig config, String location) {
    return ((PrefixedLogger) getLogger(config, location)).prefix;
  }

  private static final class PrefixedLogger implements Logger {
    private final Logger sink;
    private final String prefix;
    private final boolean debugEnabled;
    private final boolean infoEnabled;
    private final boolean warnEnabled;

    PrefixedLogger(Logger sink, String prefix, boolean debugEnabled, boolean infoEnabled, boolean warnEnabled) {
      this.sink = sink;
      this.prefix = prefix;
      this.debugEnabled = debugEnabled;
      this.infoEnabled = infoEnabled;
      this.warnEnabled = warnEnabled;
    }

    @Override
    public void debug(Object message, Object... optionalParams) {
      if (debugEnabled) {
        sink.debug(prefix, withMessage(message, optionalParams));
      }
    }

    @Override
    public void info(Object message, Object... optionalParams) {
      if (infoEnabled) {
        sink.info(prefix, withMessage(message, optionalParams));
      }
    }

    @Override
    public void warn(Object message, Object... optionalParams) {
      if (warnEnabled) {
        sink.warn(prefix, withMessage(message, optionalParams));
      }
    }

    @Override
    public void error(Object message, Object... optionalParams) {
      sink.error(prefix, withMessage(message, optionalParams));
    }

    private static Object[] withMessage(Object message, Object[] optionalParams) {
      int extra = optionalParams == null ? 0 : optionalParams.length;
      Object[] arguments = new Object[extra + 1];
      arguments[0] = message;
      if (extra > 0) {
        System.arraycopy(optionalParams, 0, arguments, 1, extra);
      }
      return arguments;
    }

    @Override
    public String toString() {
      return "Logger{" + prefix + "}";
    }
  }
}
