package com.boostercloud.framework.provider.local.store;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A JavaScript {@code RegExp} used as a query value ({@code { field: /abc/ }} or {@code { field: { $regex: /abc/ } }}).
 * Two regular expressions are equal when their source and flags are equal, so translated queries can be compared in
 * tests the way chai compares {@code RegExp}s.
 *
 * <p>The pattern is compiled eagerly, so an invalid source fails where JavaScript's {@code new RegExp(source)} would
 * (with a {@link java.util.regex.PatternSyntaxException} instead of a {@code SyntaxError}). Matching is
 * {@code regex.test(value)}: an unanchored search ({@link java.util.regex.Matcher#find()}). Supported flags:
 * {@code i} (case-insensitive, Unicode-aware like JavaScript), {@code m}, {@code s} and {@code u}; {@code g},
 * {@code y}, {@code d} and {@code v} are accepted and ignored because NeDB's single {@code test} call does not depend
 * on them. The pattern syntax is Java's, which accepts the JavaScript syntax Booster users write ({@code ^}, character
 * classes, quantifiers, groups); exotic differences (for example {@code $} also matching before a final line
 * terminator) are not emulated.
 */
public final class JsRegExp {

  private final String source;
  private final String flags;
  private final transient Pattern pattern;

  /**
   * {@code new RegExp(source, flags)}.
   *
   * @param source the pattern source
   * @param flags the flags ({@code ""} or {@code null} for none)
   */
  public JsRegExp(String source, String flags) {
    this.source = Objects.requireNonNull(source, "source");
    this.flags = flags == null ? "" : flags;
    this.pattern = Pattern.compile(source, toJavaFlags(this.flags));
  }

  /**
   * {@code new RegExp(source)}.
   *
   * @param source the pattern source
   */
  public JsRegExp(String source) {
    this(source, "");
  }

  private static int toJavaFlags(String flags) {
    int result = 0;
    for (char flag : flags.toCharArray()) {
      switch (flag) {
        case 'i' -> result |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        case 'm' -> result |= Pattern.MULTILINE;
        case 's' -> result |= Pattern.DOTALL;
        case 'u' -> result |= Pattern.UNICODE_CASE;
        case 'g', 'y', 'd', 'v' -> {
          // irrelevant for a single test()
        }
        default -> throw new IllegalArgumentException("Invalid flags supplied to RegExp constructor '" + flags + "'");
      }
    }
    return result;
  }

  /**
   * @return the pattern source ({@code regex.source})
   */
  public String source() {
    return source;
  }

  /**
   * @return the flags ({@code regex.flags})
   */
  public String flags() {
    return flags;
  }

  /**
   * @return the compiled Java pattern
   */
  public Pattern pattern() {
    return pattern;
  }

  /**
   * {@code regex.test(value)}.
   *
   * @param value the string to search
   * @return whether the pattern occurs in the value
   */
  public boolean test(String value) {
    return pattern.matcher(value).find();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof JsRegExp regExp && source.equals(regExp.source) && flags.equals(regExp.flags);
  }

  @Override
  public int hashCode() {
    return Objects.hash(source, flags);
  }

  /**
   * @return the JavaScript literal form, {@code /source/flags}
   */
  @Override
  public String toString() {
    return "/" + source + "/" + flags;
  }
}
