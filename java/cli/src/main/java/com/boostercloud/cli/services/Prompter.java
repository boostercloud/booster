package com.boostercloud.cli.services;

import com.boostercloud.cli.common.CliException;
import com.boostercloud.cli.common.ColorSupport;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Locale;

/**
 * Interactive prompts ({@code services/user-prompt.ts}, an inquirer wrapper).
 *
 * <p>Prompts read whole lines from the standard input: a list prompt shows numbered choices (inquirer uses arrow
 * keys, which need a raw terminal). An exhausted standard input answers with the default (empty text, the first
 * choice, or "no"), so the CLI never hangs when it is driven by a script.
 */
public class Prompter {
  private final BufferedReader in;
  private final PrintStream out;
  private final OraLogger oraLogger;

  /**
   * Creates a prompter.
   *
   * @param in the standard input
   * @param out the standard output
   * @param oraLogger the spinner, paused before every prompt (may be {@code null})
   */
  public Prompter(BufferedReader in, PrintStream out, OraLogger oraLogger) {
    this.in = in;
    this.out = out;
    this.oraLogger = oraLogger;
  }

  /**
   * Returns the value when it is not empty; otherwise asks for it with a free-text prompt.
   *
   * <p>Unlike TypeScript, double quotes are not escaped: every generated file escapes values for its own format.
   *
   * @param value the value given through a flag, may be {@code null}
   * @param promptMessage the question
   * @return the value or the answer (possibly empty)
   */
  public String defaultOrPrompt(String value, String promptMessage) {
    if (value != null && !value.isEmpty()) {
      return value;
    }
    ask(promptMessage + " ");
    String answer = readLine();
    return answer == null ? "" : answer;
  }

  /**
   * Returns the value when it is not empty; otherwise lets the user choose one of the options. The answer may be
   * the number of an option or its exact text; an empty answer selects the first option.
   *
   * @param value the value given through a flag, may be {@code null}
   * @param promptMessage the question
   * @param options the choices
   * @return the value or the chosen option
   */
  public String defaultOrChoose(String value, String promptMessage, List<String> options) {
    if (value != null && !value.isEmpty()) {
      return value;
    }
    if (options.isEmpty()) {
      throw new CliException("There are no options to choose from for: " + promptMessage);
    }
    ask(promptMessage + System.lineSeparator());
    for (int i = 0; i < options.size(); i++) {
      out.println("  " + ColorSupport.cyan((i + 1) + ")") + " " + options.get(i));
    }
    while (true) {
      out.print("  Answer [1-" + options.size() + "] (1): ");
      out.flush();
      String answer = readLine();
      if (answer == null || answer.isBlank()) {
        return options.getFirst();
      }
      String trimmed = answer.strip();
      if (options.contains(trimmed)) {
        return trimmed;
      }
      try {
        int index = Integer.parseInt(trimmed);
        if (index >= 1 && index <= options.size()) {
          return options.get(index - 1);
        }
      } catch (NumberFormatException ignored) {
        // fall through and ask again
      }
      out.println("  Please enter a number between 1 and " + options.size() + ".");
    }
  }

  /**
   * Asks a yes/no question whose default answer is "no" ({@code confirmPrompt} in TypeScript, where it is static).
   *
   * @param message the question
   * @return {@code true} only when the user answers {@code y} or {@code yes}
   */
  public boolean confirmPrompt(String message) {
    ask(message + " " + "(y/N) ");
    String answer = readLine();
    if (answer == null) {
      return false;
    }
    String normalized = answer.strip().toLowerCase(Locale.ROOT);
    return normalized.equals("y") || normalized.equals("yes");
  }

  private void ask(String question) {
    if (oraLogger != null) {
      oraLogger.pause();
    }
    out.print(ColorSupport.green("?") + " " + ColorSupport.bold(question));
    out.flush();
  }

  private String readLine() {
    try {
      String line = in.readLine();
      if (line == null) {
        out.println();
      }
      return line;
    } catch (IOException e) {
      throw new CliException("Could not read the answer from the standard input: " + e.getMessage(), e);
    }
  }
}
