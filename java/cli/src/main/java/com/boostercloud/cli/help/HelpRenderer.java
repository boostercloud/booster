package com.boostercloud.cli.help;

import com.boostercloud.cli.CliVersion;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;

/** Renders help in the format of {@code @oclif/plugin-help}, which the TypeScript CLI uses. */
public final class HelpRenderer {
  /** The CLI description ({@code package.json}). */
  public static final String CLI_DESCRIPTION =
      "CLI of the Booster Framework, the next level of abstraction for cloud-native applications";

  private static final Map<String, String> TOPIC_DESCRIPTIONS =
      Map.of(
          "add", "Add projections and reducers to existing read models and entities",
          "new", "Create a new project, or a new resource in the current project",
          "stub", "Publish the resource templates so that they can be customized");

  private HelpRenderer() {}

  /**
   * The help shown by {@code boost}, {@code boost --help} and {@code boost help}.
   *
   * @param commands every command
   * @return the help text
   */
  public static String root(Collection<CommandSpec> commands) {
    StringBuilder sb = new StringBuilder();
    sb.append(CLI_DESCRIPTION).append("\n\n");
    sb.append("VERSION\n  ").append(CliVersion.versionLine()).append("\n\n");
    sb.append("USAGE\n  $ boost [COMMAND]\n\n");
    Map<String, String> topics = new TreeMap<>();
    Map<String, String> topLevel = new TreeMap<>();
    for (CommandSpec spec : commands) {
      String name = spec.name();
      int colon = name.indexOf(':');
      if (colon > 0) {
        String topic = name.substring(0, colon);
        topics.put(topic, TOPIC_DESCRIPTIONS.getOrDefault(topic, ""));
      } else {
        topLevel.put(name, description(spec));
      }
    }
    topLevel.put("help", "Display help for boost.");
    sb.append("TOPICS\n").append(table(topics)).append('\n');
    sb.append("COMMANDS\n").append(table(topLevel));
    return sb.toString();
  }

  /**
   * The help of a topic ({@code boost new}).
   *
   * @param topic the topic
   * @param commands every command
   * @return the help text
   */
  public static String topic(String topic, Collection<CommandSpec> commands) {
    StringBuilder sb = new StringBuilder();
    sb.append(TOPIC_DESCRIPTIONS.getOrDefault(topic, topic)).append("\n\n");
    sb.append("USAGE\n  $ boost ").append(topic).append(":COMMAND\n\n");
    Map<String, String> rows = new TreeMap<>();
    for (CommandSpec spec : commands) {
      if (spec.name().startsWith(topic + ":")) {
        rows.put(spec.name(), description(spec));
      }
    }
    sb.append("COMMANDS\n").append(table(rows));
    return sb.toString();
  }

  /**
   * The help of a command ({@code boost new:entity --help}).
   *
   * @param spec the command
   * @return the help text
   */
  public static String command(CommandSpec spec) {
    StringBuilder sb = new StringBuilder();
    sb.append(description(spec)).append("\n\n");
    sb.append("USAGE\n  $ boost ").append(usage(spec)).append("\n");
    List<PositionalParamSpec> positionals = spec.positionalParameters();
    if (!positionals.isEmpty()) {
      Map<String, String> rows = new LinkedHashMap<>();
      for (PositionalParamSpec positional : positionals) {
        rows.put(positional.paramLabel(), String.join(" ", positional.description()));
      }
      sb.append("\nARGUMENTS\n").append(table(rows));
    }
    List<OptionSpec> options = sortedOptions(spec);
    if (!options.isEmpty()) {
      Map<String, String> rows = new LinkedHashMap<>();
      for (OptionSpec option : options) {
        rows.put(flagLabel(option), flagDescription(option));
      }
      sb.append("\nFLAGS\n").append(table(rows));
    }
    String[] footer = spec.usageMessage().footer();
    if (footer.length > 0) {
      sb.append("\nEXAMPLES\n");
      for (String example : footer) {
        sb.append("  ").append(example).append('\n');
      }
    }
    return sb.toString();
  }

  private static String usage(CommandSpec spec) {
    String[] custom = spec.usageMessage().customSynopsis();
    if (custom.length > 0) {
      return String.join(" ", custom);
    }
    List<String> parts = new ArrayList<>();
    parts.add(spec.name());
    for (PositionalParamSpec positional : spec.positionalParameters()) {
      parts.add(positional.required() ? positional.paramLabel() : "[" + positional.paramLabel() + "]");
    }
    for (OptionSpec option : sortedOptions(spec)) {
      String name = shortName(option) != null ? shortName(option) : option.longestName();
      String value = option.typeInfo().isBoolean() ? "" : " " + option.paramLabel() + (option.isMultiValue() ? "..." : "");
      parts.add(option.required() ? name + value : "[" + name + value + "]");
    }
    return String.join(" ", parts);
  }

  private static List<OptionSpec> sortedOptions(CommandSpec spec) {
    List<OptionSpec> options = new ArrayList<>(spec.options());
    options.sort(
        Comparator.comparing((OptionSpec o) -> shortName(o) == null)
            .thenComparing(o -> shortName(o) == null ? "" : shortName(o).toLowerCase())
            .thenComparing(o -> shortName(o) == null ? "" : shortName(o))
            .thenComparing(OptionSpec::longestName));
    return options;
  }

  private static String shortName(OptionSpec option) {
    for (String name : option.names()) {
      if (name.startsWith("-") && !name.startsWith("--")) {
        return name;
      }
    }
    return null;
  }

  private static String flagLabel(OptionSpec option) {
    String shortName = shortName(option);
    String prefix = shortName == null ? "    " : shortName + ", ";
    String value =
        option.typeInfo().isBoolean() ? "" : "=" + option.paramLabel() + (option.isMultiValue() ? "..." : "");
    return prefix + option.longestName() + value;
  }

  private static String flagDescription(OptionSpec option) {
    StringBuilder sb = new StringBuilder(String.join(" ", option.description()));
    if (option.required()) {
      sb.append(" (required)");
    }
    if (option.defaultValue() != null && !option.typeInfo().isBoolean()) {
      sb.append(" [default: ").append(option.defaultValue()).append(']');
    }
    return sb.toString();
  }

  private static String description(CommandSpec spec) {
    return String.join(" ", spec.usageMessage().description());
  }

  private static String table(Map<String, String> rows) {
    int width = rows.keySet().stream().mapToInt(String::length).max().orElse(0);
    StringBuilder sb = new StringBuilder();
    rows.forEach(
        (left, right) -> {
          sb.append("  ").append(left);
          if (!right.isEmpty()) {
            sb.append(" ".repeat(width - left.length() + 2)).append(right);
          }
          sb.append('\n');
        });
    return sb.toString();
  }
}
