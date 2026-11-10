package com.boostercloud.cli.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class-name/file-name helpers and resource-name validation ({@code common/filenames.ts}).
 *
 * <p>Java source files must be named after their public class, so the generators write {@code Name.java}; the
 * dasherizing helpers are kept for parity with the TypeScript API.
 */
public final class Filenames {
  private static final Pattern INVALID_CHARACTERS = Pattern.compile("^[\\d-]|[#$-/:-?{-~!\"^_`\\[\\]]");
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");
  private static final Pattern WORDS = Pattern.compile("[a-zA-Z\\d]+");

  private Filenames() {}

  /**
   * Turns a class name into a dasherized file name ({@code inflected.dasherize(inflected.underscore(name))}):
   * {@code PostReadModel} becomes {@code post-read-model}.
   *
   * @param name a class name
   * @return the dasherized name
   */
  public static String classNameToFileName(String name) {
    String result = name.replaceAll("([A-Z\\d]+)([A-Z][a-z])", "$1_$2");
    result = result.replaceAll("([a-z\\d])([A-Z])", "$1_$2");
    result = result.replace('-', '_').toLowerCase(Locale.ROOT);
    return result.replace('_', '-');
  }

  /**
   * The lower-cased dasherized file name of a class with the given extension.
   *
   * @param name a class name
   * @param extension the extension without the dot
   * @return for example {@code test-file-name.ts}
   */
  public static String fileNameWithExtension(String name, String extension) {
    return (classNameToFileName(name) + "." + extension).toLowerCase(Locale.ROOT);
  }

  /**
   * {@link #fileNameWithExtension(String, String)} with the {@code java} extension (TypeScript defaults to
   * {@code ts}).
   *
   * @param name a class name
   * @return for example {@code test-file-name.java}
   */
  public static String fileNameWithExtension(String name) {
    return fileNameWithExtension(name, "java");
  }

  /**
   * Checks that a resource name is a PascalCase identifier with valid characters.
   *
   * @param name the resource name
   * @throws CliException when the name is not valid
   */
  public static void checkResourceNameIsValid(String name) {
    if (!hasValidResourceName(name)) {
      throw new CliException(
          "'" + name + "' is not valid resource name. Please use PascalCase name with valid characters.");
    }
  }

  static boolean hasValidResourceName(String name) {
    return name.equals(formatResourceName(name));
  }

  static String formatResourceName(String name) {
    String resourceName = INVALID_CHARACTERS.matcher(name).replaceAll(" ");
    resourceName = WHITESPACE.matcher(resourceName).replaceAll(" ").strip();
    if (resourceName.isEmpty()) {
      return null;
    }
    if (resourceName.length() == 1) {
      return resourceName.toUpperCase(Locale.ROOT);
    }
    Matcher matcher = WORDS.matcher(resourceName);
    List<String> words = new ArrayList<>();
    while (matcher.find()) {
      words.add(matcher.group());
    }
    if (!words.isEmpty()) {
      StringBuilder sb = new StringBuilder();
      words.forEach(word -> sb.append(titleCaseString(word)));
      return sb.toString();
    }
    return resourceName;
  }

  static String titleCaseString(String value) {
    return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
  }
}
