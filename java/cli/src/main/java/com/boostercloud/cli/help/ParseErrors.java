package com.boostercloud.cli.help;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import picocli.CommandLine;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.OptionSpec;

/**
 * Translates picocli parsing errors into the messages of oclif, which the TypeScript CLI (and its tests) use:
 * {@code Flag --fields expects a value}, {@code Nonexistent flag: --x}, {@code Unexpected argument: X},
 * {@code Missing required flag read-model}... Flags are always reported by their long name.
 */
public final class ParseErrors {
  private ParseErrors() {}

  /**
   * The oclif message of a parsing error.
   *
   * @param ex the picocli error
   * @return the message (possibly several lines)
   */
  public static String message(CommandLine.ParameterException ex) {
    if (ex instanceof CommandLine.MissingParameterException missing) {
      List<String> lines = new ArrayList<>();
      boolean requiredOptions = String.valueOf(missing.getMessage()).startsWith("Missing required option");
      for (ArgSpec arg : missing.getMissing()) {
        if (arg instanceof OptionSpec option) {
          String longName = option.longestName();
          lines.add(
              requiredOptions
                  ? "Missing required flag " + longName.replaceFirst("^-+", "")
                  : "Flag " + longName + " expects a value");
        } else if (arg.isPositional()) {
          lines.add("Missing 1 required arg:\n" + ((CommandLine.Model.PositionalParamSpec) arg).paramLabel().toLowerCase());
        }
      }
      if (!lines.isEmpty()) {
        return requiredOptions ? String.join("\n", lines) : lines.getFirst();
      }
    }
    if (ex instanceof CommandLine.UnmatchedArgumentException unmatched) {
      List<String> flags = new ArrayList<>();
      List<String> arguments = new ArrayList<>();
      for (String token : unmatched.getUnmatched()) {
        if (token.startsWith("-") && token.length() > 1) {
          flags.add(token.contains("=") ? token.substring(0, token.indexOf('=')) : token);
        } else {
          arguments.add(token);
        }
      }
      if (!flags.isEmpty()) {
        return (flags.size() == 1 ? "Nonexistent flag: " : "Nonexistent flags: ") + String.join(", ", flags);
      }
      if (!arguments.isEmpty()) {
        return (arguments.size() == 1 ? "Unexpected argument: " : "Unexpected arguments: ")
            + String.join(", ", arguments);
      }
    }
    if (ex instanceof CommandLine.OverwrittenOptionException overwritten
        && overwritten.getOverwritten() instanceof OptionSpec option) {
      return "Flag " + option.longestName() + " can only be specified once";
    }
    if (ex.getCause() instanceof CommandLine.TypeConversionException && ex.getArgSpec() instanceof OptionSpec option) {
      return "Parsing " + option.longestName() + " \n\tExpected an integer but received: " + ex.getValue();
    }
    return ex.getMessage() == null
        ? ex.getClass().getSimpleName()
        : ex.getMessage().lines().collect(Collectors.joining("\n"));
  }
}
