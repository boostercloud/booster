package com.boostercloud.cli.services;

import com.boostercloud.cli.common.ColorSupport;
import java.io.PrintStream;

/**
 * A single-line spinner logger with the behaviour of the {@code ora} 3 instance used by the TypeScript CLI
 * ({@code oraLogger}).
 *
 * <ul>
 *   <li>{@link #start(String)} shows an animated spinner on an interactive terminal; otherwise it prints
 *       {@code - <text>}.
 *   <li>{@link #succeed}, {@link #fail}, {@link #info} and {@link #warn} stop the spinner and persist one line with
 *       a green {@code ✔}, red {@code ✖}, blue {@code ℹ} or yellow {@code ⚠} symbol.
 * </ul>
 *
 * Everything is written to standard output, as ora is configured to do.
 */
public class OraLogger {
  private static final String[] FRAMES = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
  private static final String CLEAR_LINE = "\r\u001b[2K";
  private static final String HIDE_CURSOR = "\u001b[?25l";
  private static final String SHOW_CURSOR = "\u001b[?25h";

  private final PrintStream stream;
  private final boolean animated;
  private final boolean unicode;
  private String text;
  private Thread animation;
  private int frame;

  /**
   * Creates a logger.
   *
   * @param stream where to write (standard output)
   * @param animated whether to animate the spinner (only on interactive terminals)
   * @param unicode whether the terminal supports unicode symbols
   */
  public OraLogger(PrintStream stream, boolean animated, boolean unicode) {
    this.stream = stream;
    this.animated = animated;
    this.unicode = unicode;
  }

  /**
   * Starts the spinner with a text.
   *
   * @param newText the text shown next to the spinner
   * @return this logger
   */
  public synchronized OraLogger start(String newText) {
    this.text = newText;
    if (!animated) {
      stream.println("- " + newText);
      stream.flush();
      return this;
    }
    if (animation == null) {
      stream.print(HIDE_CURSOR);
      animation = Thread.ofVirtual().name("boost-spinner").start(this::animate);
    }
    render();
    return this;
  }

  /**
   * Stops the spinner with a green tick and the text.
   *
   * @param message the text to persist
   * @return this logger
   */
  public OraLogger succeed(String message) {
    return stopAndPersist(ColorSupport.green(unicode ? "✔" : "√"), message);
  }

  /**
   * Stops the spinner with a red cross and the text.
   *
   * @param message the text to persist
   * @return this logger
   */
  public OraLogger fail(String message) {
    return stopAndPersist(ColorSupport.red(unicode ? "✖" : "×"), message);
  }

  /**
   * Stops the spinner with a blue information symbol and the text.
   *
   * @param message the text to persist
   * @return this logger
   */
  public OraLogger info(String message) {
    return stopAndPersist(ColorSupport.blue(unicode ? "ℹ" : "i"), message);
  }

  /**
   * Stops the spinner with a yellow warning symbol and the text.
   *
   * @param message the text to persist
   * @return this logger
   */
  public OraLogger warn(String message) {
    return stopAndPersist(ColorSupport.yellow(unicode ? "⚠" : "‼"), message);
  }

  /**
   * Stops the spinner (if any) and persists a line made of a symbol and a text.
   *
   * @param symbol the symbol in front of the text
   * @param message the text
   * @return this logger
   */
  public synchronized OraLogger stopAndPersist(String symbol, String message) {
    stopAnimation();
    stream.println(symbol + " " + (message == null ? (text == null ? "" : text) : message));
    stream.flush();
    text = null;
    return this;
  }

  /**
   * Stops the animation without clearing the step, persisting a {@code - <text>} line. Used before prompting the
   * user or before handing the terminal to a child process, so that their output does not collide with the
   * spinner.
   */
  public synchronized void pause() {
    if (animation != null) {
      stopAnimation();
      if (text != null) {
        stream.println("- " + text);
        stream.flush();
      }
    }
  }

  /** Stops the spinner and clears its line without persisting anything. */
  public synchronized void stop() {
    stopAnimation();
    text = null;
  }

  /**
   * Whether the spinner is currently animated.
   *
   * @return {@code true} while an animated spinner is shown
   */
  public synchronized boolean isSpinning() {
    return animation != null;
  }

  private void stopAnimation() {
    if (animation != null) {
      Thread thread = animation;
      animation = null;
      thread.interrupt();
      stream.print(CLEAR_LINE + SHOW_CURSOR);
    }
  }

  private void animate() {
    while (true) {
      try {
        Thread.sleep(80);
      } catch (InterruptedException e) {
        return;
      }
      synchronized (this) {
        if (animation != Thread.currentThread()) {
          return;
        }
        frame = (frame + 1) % FRAMES.length;
        render();
      }
    }
  }

  private void render() {
    stream.print(CLEAR_LINE + ColorSupport.cyan(FRAMES[frame]) + " " + text);
    stream.flush();
  }
}
