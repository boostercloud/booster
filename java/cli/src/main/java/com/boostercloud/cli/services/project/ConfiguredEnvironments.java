package com.boostercloud.cli.services.project;

import com.boostercloud.cli.common.CliException;
import com.boostercloud.cli.services.java.JavaLexer;
import com.boostercloud.cli.services.java.JavaLexer.Kind;
import com.boostercloud.cli.services.java.JavaLexer.Token;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The environments a project configures, found statically in its sources.
 *
 * <p>The TypeScript CLI loads the compiled project in-process and asks {@code Booster.configuredEnvironments};
 * the Java CLI does not load user code, so it looks for {@code Booster.configure("<env>", config -> ...)} calls and
 * for {@code config.appName = "<name>"} assignments inside them. When an environment name is not a string literal
 * the set cannot be known, which {@link #hasDynamicNames()} reports so that callers skip the checks that would
 * otherwise be wrong.
 *
 * @param names the literal environment names, in order of appearance
 * @param hasDynamicNames whether some {@code Booster.configure} call uses a non-literal environment name
 * @param appNames the literal {@code appName} assigned in each environment (the last assignment wins)
 */
public record ConfiguredEnvironments(Set<String> names, boolean hasDynamicNames, Map<String, String> appNames) {
  private static final String CONFIGURE = "configure";

  /** Makes the collections immutable (preserving order). */
  public ConfiguredEnvironments {
    names = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(names));
    appNames = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(appNames));
  }

  /**
   * Scans every {@code .java} file of a source directory, in path order.
   *
   * @param sourceDirectory the source root
   * @return the environments found
   */
  public static ConfiguredEnvironments scan(Path sourceDirectory) {
    Set<String> names = new LinkedHashSet<>();
    Map<String, String> appNames = new LinkedHashMap<>();
    boolean dynamic = false;
    if (!Files.isDirectory(sourceDirectory)) {
      return new ConfiguredEnvironments(names, false, appNames);
    }
    try (Stream<Path> files = Files.walk(sourceDirectory)) {
      for (Path file : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        if (!source.contains(CONFIGURE)) {
          continue;
        }
        ConfiguredEnvironments found = parse(source);
        names.addAll(found.names());
        appNames.putAll(found.appNames());
        dynamic |= found.hasDynamicNames();
      }
    } catch (IOException | UncheckedIOException e) {
      throw new CliException("Could not read the sources in " + sourceDirectory + ": " + e.getMessage(), e);
    }
    return new ConfiguredEnvironments(names, dynamic, appNames);
  }

  /**
   * Finds the {@code Booster.configure} calls of one compilation unit.
   *
   * @param source the source code
   * @return the environments found
   */
  public static ConfiguredEnvironments parse(String source) {
    List<Token> tokens = JavaLexer.tokenize(source);
    boolean staticallyImported = hasStaticConfigureImport(tokens);
    Set<String> names = new LinkedHashSet<>();
    Map<String, String> appNames = new LinkedHashMap<>();
    boolean dynamic = false;
    for (int i = 0; i + 1 < tokens.size(); i++) {
      if (!tokens.get(i).is(CONFIGURE) || !tokens.get(i + 1).is("(")) {
        continue;
      }
      boolean qualified = i >= 2 && tokens.get(i - 1).is(".") && tokens.get(i - 2).is("Booster");
      boolean bare = staticallyImported && (i == 0 || !tokens.get(i - 1).is("."));
      if (!qualified && !bare) {
        continue;
      }
      int open = i + 1;
      int close = matchingParenthesis(tokens, open);
      if (open + 1 >= tokens.size() || close < 0) {
        continue;
      }
      Token first = tokens.get(open + 1);
      boolean literal = first.kind() == Kind.STRING && open + 2 < tokens.size() && tokens.get(open + 2).is(",");
      if (!literal) {
        dynamic = true;
        continue;
      }
      String environment = JavaLexer.stringValue(first.text());
      names.add(environment);
      appNameAssignment(tokens, open + 2, close).ifPresent(name -> appNames.put(environment, name));
      i = close;
    }
    return new ConfiguredEnvironments(names, dynamic, appNames);
  }

  private static Optional<String> appNameAssignment(List<Token> tokens, int from, int to) {
    String found = null;
    for (int j = from; j + 2 < to; j++) {
      if (tokens.get(j).is("appName")
          && tokens.get(j + 1).is("=")
          && !tokens.get(j + 2).is("=")
          && tokens.get(j + 2).kind() == Kind.STRING
          && (j + 3 >= tokens.size() || tokens.get(j + 3).is(";"))) {
        found = JavaLexer.stringValue(tokens.get(j + 2).text());
      }
    }
    return Optional.ofNullable(found);
  }

  private static int matchingParenthesis(List<Token> tokens, int open) {
    int depth = 0;
    for (int k = open; k < tokens.size(); k++) {
      if (tokens.get(k).is("(")) {
        depth++;
      } else if (tokens.get(k).is(")")) {
        depth--;
        if (depth == 0) {
          return k;
        }
      }
    }
    return -1;
  }

  private static boolean hasStaticConfigureImport(List<Token> tokens) {
    for (int i = 0; i + 1 < tokens.size(); i++) {
      if (tokens.get(i).is("import") && tokens.get(i + 1).is("static")) {
        StringBuilder name = new StringBuilder();
        for (int j = i + 2; j < tokens.size() && !tokens.get(j).is(";"); j++) {
          name.append(tokens.get(j).text());
        }
        String imported = name.toString();
        if (imported.endsWith("Booster.configure") || imported.endsWith("Booster.*")) {
          return true;
        }
      }
    }
    return false;
  }
}
