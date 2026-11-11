package com.boostercloud.cli.common;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Terminal colour support, the Java counterpart of the {@code chalk}/{@code supports-color} pair used by the
 * TypeScript CLI.
 *
 * <p>Levels follow chalk: {@code 0} no colours, {@code 1} basic 16 colours, {@code 2} 256 colours and {@code 3}
 * true colour. The level is global (as chalk's is) and is detected once at start-up by {@code Main}; tests force it
 * with {@link #setLevel(int)}.
 */
public final class ColorSupport {
  private static final String CLOSE_FOREGROUND = "\u001b[39m";
  private static final Pattern NEWLINE = Pattern.compile("\r?\n");
  private static volatile int level = -1;

  private ColorSupport() {}

  /**
   * The colour level in use, detecting it from the process environment the first time.
   *
   * @return a level between 0 and 3
   */
  public static int level() {
    int current = level;
    if (current < 0) {
      current = detect(System.getenv(), isTerminal(), System.getProperty("os.name", ""));
      level = current;
    }
    return current;
  }

  /**
   * Forces the colour level (used by {@code Main} after detection and by tests).
   *
   * @param newLevel a level between 0 and 3
   */
  public static void setLevel(int newLevel) {
    level = Math.max(0, Math.min(3, newLevel));
  }

  /**
   * Detects the colour level the way {@code supports-color} 5 does (simplified), also honouring {@code NO_COLOR}.
   *
   * @param env the process environment
   * @param tty whether standard output is a terminal
   * @param osName the {@code os.name} system property
   * @return a level between 0 and 3
   */
  public static int detect(Map<String, String> env, boolean tty, String osName) {
    String force = env.get("FORCE_COLOR");
    Integer forced = null;
    if (force != null) {
      String value = force.trim().toLowerCase(Locale.ROOT);
      switch (value) {
        case "false", "0" -> {
          return 0;
        }
        case "", "true", "1" -> forced = 1;
        case "2" -> forced = 2;
        case "3" -> forced = 3;
        default -> forced = 1;
      }
    }
    if (forced == null && env.containsKey("NO_COLOR")) {
      return 0;
    }
    if (forced == null && !tty) {
      return 0;
    }
    int min = forced == null ? 0 : forced;
    String term = env.getOrDefault("TERM", "");
    if ("dumb".equals(term)) {
      return min;
    }
    if (osName.toLowerCase(Locale.ROOT).startsWith("windows")) {
      return Math.max(min, env.containsKey("WT_SESSION") ? 3 : 1);
    }
    if (env.containsKey("CI")) {
      return Math.max(min, 1);
    }
    String colorTerm = env.getOrDefault("COLORTERM", "");
    if ("truecolor".equals(colorTerm) || "24bit".equals(colorTerm)) {
      return 3;
    }
    String termProgram = env.getOrDefault("TERM_PROGRAM", "");
    if ("iTerm.app".equals(termProgram)) {
      return 3;
    }
    if ("Apple_Terminal".equals(termProgram)) {
      return Math.max(min, 2);
    }
    if (term.matches("(?i).*-256(color)?$")) {
      return Math.max(min, 2);
    }
    if (term.matches("(?i)^(screen|xterm|vt100|vt220|rxvt).*") || term.matches("(?i).*(color|ansi|cygwin|linux).*")) {
      return Math.max(min, 1);
    }
    if (!colorTerm.isEmpty()) {
      return Math.max(min, 1);
    }
    return min;
  }

  /**
   * Whether standard output is connected to an interactive terminal.
   *
   * @return {@code true} for an interactive terminal
   */
  public static boolean isTerminal() {
    var console = System.console();
    return console != null && console.isTerminal();
  }

  /**
   * Colours a text with a hexadecimal colour ({@code chalk.hex(color)(text)}), downgrading it to the level in use.
   *
   * @param hex a colour such as {@code #FA6E59}
   * @param text the text to colour
   * @return the coloured text (unchanged at level 0)
   */
  public static String hex(String hex, String text) {
    int current = level();
    if (current == 0) {
      return text;
    }
    int rgb = Integer.parseInt(hex.substring(1), 16);
    int r = (rgb >> 16) & 0xFF;
    int g = (rgb >> 8) & 0xFF;
    int b = rgb & 0xFF;
    String open =
        switch (current) {
          case 3 -> "\u001b[38;2;" + r + ";" + g + ";" + b + "m";
          case 2 -> "\u001b[38;5;" + ansi256(r, g, b) + "m";
          default -> "\u001b[" + ansi16(r, g, b) + "m";
        };
    return wrap(open, CLOSE_FOREGROUND, text);
  }

  /** @param text the text @return the text in blue (chalk {@code blue}) */
  public static String blue(String text) {
    return basic(34, text);
  }

  /** @param text the text @return the text in green (chalk {@code green}) */
  public static String green(String text) {
    return basic(32, text);
  }

  /** @param text the text @return the text in red (chalk {@code red}) */
  public static String red(String text) {
    return basic(31, text);
  }

  /** @param text the text @return the text in yellow (chalk {@code yellow}) */
  public static String yellow(String text) {
    return basic(33, text);
  }

  /** @param text the text @return the text in cyan (chalk {@code cyan}) */
  public static String cyan(String text) {
    return basic(36, text);
  }

  /** @param text the text @return the text in bold (chalk {@code bold}) */
  public static String bold(String text) {
    return level() == 0 ? text : wrap("\u001b[1m", "\u001b[22m", text);
  }

  private static String basic(int code, String text) {
    return level() == 0 ? text : wrap("\u001b[" + code + "m", CLOSE_FOREGROUND, text);
  }

  // chalk closes and re-opens the style around line breaks so that every line is coloured
  private static String wrap(String open, String close, String text) {
    String body = NEWLINE.matcher(text).replaceAll(m -> java.util.regex.Matcher.quoteReplacement(close + m.group() + open));
    return open + body + close;
  }

  static int ansi256(int r, int g, int b) {
    if (r == g && g == b) {
      if (r < 8) {
        return 16;
      }
      if (r > 248) {
        return 231;
      }
      return (int) Math.round(((r - 8) / 247.0) * 24) + 232;
    }
    return 16
        + (36 * (int) Math.round(r / 255.0 * 5))
        + (6 * (int) Math.round(g / 255.0 * 5))
        + (int) Math.round(b / 255.0 * 5);
  }

  static int ansi16(int r, int g, int b) {
    double value = Math.max(r, Math.max(g, b)) / 255.0 * 100;
    long rounded = Math.round(value / 50);
    if (rounded == 0) {
      return 30;
    }
    int ansi = 30 + (((int) Math.round(b / 255.0) << 2) | ((int) Math.round(g / 255.0) << 1) | (int) Math.round(r / 255.0));
    if (rounded == 2) {
      ansi += 60;
    }
    return ansi;
  }
}
