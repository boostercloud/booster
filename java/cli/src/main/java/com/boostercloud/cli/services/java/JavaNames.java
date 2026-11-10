package com.boostercloud.cli.services.java;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/** Validation and derivation of Java identifiers and package names. */
public final class JavaNames {
  /** The reserved keywords and literals that cannot be used as identifiers. */
  public static final Set<String> KEYWORDS =
      Set.of(
          "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
          "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
          "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
          "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
          "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null", "_");

  private JavaNames() {}

  /**
   * Whether a text is a valid Java identifier (not a keyword).
   *
   * @param name the text
   * @return {@code true} for a valid identifier
   */
  public static boolean isIdentifier(String name) {
    if (name == null || name.isEmpty() || KEYWORDS.contains(name)) {
      return false;
    }
    if (!Character.isJavaIdentifierStart(name.charAt(0))) {
      return false;
    }
    return name.chars().skip(1).allMatch(Character::isJavaIdentifierPart);
  }

  /**
   * Whether a text is a valid dotted Java package name.
   *
   * @param packageName the text
   * @return {@code true} when every segment is a valid identifier
   */
  public static boolean isPackageName(String packageName) {
    if (packageName == null || packageName.isEmpty() || packageName.startsWith(".") || packageName.endsWith(".")) {
      return false;
    }
    return Arrays.stream(packageName.split("\\.", -1)).allMatch(JavaNames::isIdentifier);
  }

  /**
   * Derives a package segment from a project name: only lower-case ASCII letters and digits are kept
   * ({@code my-store} becomes {@code mystore}), and {@code app} is prepended or appended when the result would
   * start with a digit, be empty or be a keyword.
   *
   * @param projectName the project name
   * @return a valid package segment
   */
  public static String packageSegment(String projectName) {
    StringBuilder sb = new StringBuilder();
    for (char c : projectName.toLowerCase(Locale.ROOT).toCharArray()) {
      if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
        sb.append(c);
      }
    }
    String segment = sb.toString();
    if (segment.isEmpty() || Character.isDigit(segment.charAt(0))) {
      segment = "app" + segment;
    }
    if (KEYWORDS.contains(segment)) {
      segment = segment + "app";
    }
    return segment;
  }

  /**
   * The package part of a fully qualified class name.
   *
   * @param qualifiedName for example {@code com.example.Main}
   * @return {@code com.example}, or an empty string for the default package
   */
  public static String packageOf(String qualifiedName) {
    int dot = qualifiedName.lastIndexOf('.');
    return dot < 0 ? "" : qualifiedName.substring(0, dot);
  }

  /**
   * The simple part of a fully qualified class name.
   *
   * @param qualifiedName for example {@code com.example.Main}
   * @return {@code Main}
   */
  public static String simpleNameOf(String qualifiedName) {
    int dot = qualifiedName.lastIndexOf('.');
    return dot < 0 ? qualifiedName : qualifiedName.substring(dot + 1);
  }

  /**
   * Escapes a text so that it can be placed between double quotes in Java source code.
   *
   * @param value the text
   * @return the escaped text (without the quotes)
   */
  public static String escapeStringLiteral(String value) {
    StringBuilder sb = new StringBuilder();
    for (char c : value.toCharArray()) {
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    return sb.toString();
  }
}
