package com.boostercloud.cli.integration.helper;

import java.nio.file.Path;

/**
 * Small edits of generated Java files. The TypeScript tests replace the placeholders of the generated files (and
 * prepend missing imports) "so the sandbox stays compilable"; Java needs the same edits plus the imports of the
 * classes used in the replacements ({@code All} for {@code authorize = All.class}).
 */
public final class JavaSources {
  /** {@code authorize = All.class}. */
  public static final String ALL = "com.boostercloud.framework.core.decorators.All";

  private JavaSources() {}

  /**
   * {@code content.replace(placeholder, replacement)}: the first occurrence, like JavaScript's
   * {@code String.replace} with a string pattern.
   *
   * @param content the source
   * @param placeholder the text to replace
   * @param replacement the new text
   * @return the edited source
   * @throws IllegalArgumentException when the placeholder is not there (the edit would silently do nothing)
   */
  public static String replaceFirst(String content, String placeholder, String replacement) {
    int index = content.indexOf(placeholder);
    if (index < 0) {
      throw new IllegalArgumentException("'" + placeholder + "' is not in:\n" + content);
    }
    return content.substring(0, index) + replacement + content.substring(index + placeholder.length());
  }

  /**
   * Adds {@code import <fqn>;} as the first import of a compilation unit (right after the package declaration).
   *
   * @param content the source
   * @param fqn the class to import
   * @return the edited source
   */
  public static String withImport(String content, String fqn) {
    int packageEnd = content.indexOf(";\n");
    if (!content.startsWith("package ") || packageEnd < 0) {
      throw new IllegalArgumentException("No package declaration in:\n" + content);
    }
    String head = content.substring(0, packageEnd + 2);
    String rest = content.substring(packageEnd + 2);
    String separator = rest.startsWith("\nimport ") ? "" : "\n";
    return head + "\nimport " + fqn + ";" + separator + rest;
  }

  /**
   * Reads a file, replaces the first occurrence of a placeholder and writes it back.
   *
   * @param file the source file
   * @param placeholder the text to replace
   * @param replacement the new text
   * @param imports classes to import
   */
  public static void edit(Path file, String placeholder, String replacement, String... imports) {
    String content = replaceFirst(FileHelper.readFileContent(file), placeholder, replacement);
    for (String fqn : imports) {
      content = withImport(content, fqn);
    }
    FileHelper.writeFileContent(file, content);
  }
}
