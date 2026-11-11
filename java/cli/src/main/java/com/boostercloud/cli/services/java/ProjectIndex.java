package com.boostercloud.cli.services.java;

import com.boostercloud.cli.common.CliException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * An index of the top-level types of a project's source directory, keyed by simple name. It lets the generators
 * import project classes (the TypeScript CLI relies on the TypeScript language service for this).
 */
public final class ProjectIndex {
  /**
   * A source file of the project.
   *
   * @param path the file
   * @param qualifiedName the fully qualified name of its public type (the file is named after it)
   */
  public record Entry(Path path, String qualifiedName) {}

  private final Map<String, List<Entry>> bySimpleName;

  private ProjectIndex(Map<String, List<Entry>> bySimpleName) {
    this.bySimpleName = bySimpleName;
  }

  /**
   * An index without any type.
   *
   * @return the empty index
   */
  public static ProjectIndex empty() {
    return new ProjectIndex(Map.of());
  }

  /**
   * Scans a source directory. Files are visited in a stable (sorted) order.
   *
   * @param sourceDirectory the root of the Java sources, e.g. {@code src/main/java}
   * @return the index (empty when the directory does not exist)
   */
  public static ProjectIndex scan(Path sourceDirectory) {
    Map<String, List<Entry>> index = new LinkedHashMap<>();
    if (!Files.isDirectory(sourceDirectory)) {
      return new ProjectIndex(index);
    }
    try (Stream<Path> files = Files.walk(sourceDirectory)) {
      for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".java")).sorted().toList()) {
        String fileName = file.getFileName().toString();
        String simpleName = fileName.substring(0, fileName.length() - ".java".length());
        if (!JavaNames.isIdentifier(simpleName)) {
          continue;
        }
        String packageName = packageOf(Files.readString(file, StandardCharsets.UTF_8));
        String qualifiedName = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
        index.computeIfAbsent(simpleName, k -> new ArrayList<>()).add(new Entry(file, qualifiedName));
      }
    } catch (IOException | UncheckedIOException e) {
      throw new CliException("Could not read the sources in " + sourceDirectory + ": " + e.getMessage(), e);
    }
    return new ProjectIndex(index);
  }

  /**
   * The source files declaring a type with the given simple name.
   *
   * @param simpleName the type name
   * @return the matching files, in path order
   */
  public List<Entry> find(String simpleName) {
    return bySimpleName.getOrDefault(simpleName, List.of());
  }

  /**
   * The qualified name of a type when exactly one file of the project declares it.
   *
   * @param simpleName the type name
   * @return the qualified name, empty when the type is unknown or ambiguous
   */
  public Optional<String> uniqueQualifiedName(String simpleName) {
    List<Entry> entries = find(simpleName);
    return entries.size() == 1 ? Optional.of(entries.getFirst().qualifiedName()) : Optional.empty();
  }

  /**
   * Reads the package declaration of a compilation unit.
   *
   * @param source the source code
   * @return the package name, or an empty string for the default package
   */
  public static String packageOf(String source) {
    List<JavaLexer.Token> tokens = JavaLexer.tokenize(source);
    int i = 0;
    // skip annotations on the package declaration (package-info.java)
    while (i < tokens.size() && tokens.get(i).is("@")) {
      i = skipAnnotation(tokens, i);
    }
    if (i < tokens.size() && tokens.get(i).is("package")) {
      StringBuilder sb = new StringBuilder();
      for (int j = i + 1; j < tokens.size() && !tokens.get(j).is(";"); j++) {
        sb.append(tokens.get(j).text());
      }
      return sb.toString();
    }
    return "";
  }

  static int skipAnnotation(List<JavaLexer.Token> tokens, int at) {
    int i = at + 1;
    if (i < tokens.size() && tokens.get(i).kind() == JavaLexer.Kind.IDENTIFIER) {
      i++;
      while (i + 1 < tokens.size() && tokens.get(i).is(".") && tokens.get(i + 1).kind() == JavaLexer.Kind.IDENTIFIER) {
        i += 2;
      }
    }
    if (i < tokens.size() && tokens.get(i).is("(")) {
      int depth = 0;
      for (; i < tokens.size(); i++) {
        if (tokens.get(i).is("(")) {
          depth++;
        } else if (tokens.get(i).is(")")) {
          depth--;
          if (depth == 0) {
            return i + 1;
          }
        }
      }
    }
    return i;
  }
}
