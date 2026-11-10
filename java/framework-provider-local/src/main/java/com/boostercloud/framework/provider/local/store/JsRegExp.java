package com.boostercloud.framework.provider.local.store;

import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A JavaScript {@code RegExp} used as a query value ({@code { field: /abc/ }} or {@code { field: { $regex: /abc/ } }}).
 * Two regular expressions are equal when their source and flags are equal, so translated queries can be compared in
 * tests the way chai compares {@code RegExp}s.
 *
 * <p>The source is JavaScript syntax: it is translated into an equivalent Java {@link Pattern}
 * ({@link JsRegExpTranslator}), so that literals such as a lone {@code {}, the Unicode {@code \s}, {@code $} at the
 * very end of the input, identity escapes ({@code \e}), legacy octal escapes, {@code []} and {@code [^]} behave as in
 * JavaScript, and Java-only syntax ({@code a*+}, {@code (?i)}) is rejected. The pattern is compiled eagerly, so an
 * invalid source fails where JavaScript's {@code new RegExp(source)} would, with a {@link SyntaxError} carrying V8's
 * message ({@code Invalid regular expression: /(/: Unterminated group}). Matching is {@code regex.test(value)}: an
 * unanchored search ({@link java.util.regex.Matcher#find()}). Flags: {@code i}, {@code m}, {@code s}, {@code u} and
 * {@code v} change matching; {@code g}, {@code y} and {@code d} are accepted and ignored because NeDB's single
 * {@code test} call does not depend on them; unknown or repeated flags are rejected like in JavaScript.
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
    validateFlags(this.flags);
    JsRegExpTranslator.Translation translation;
    try {
      translation = JsRegExpTranslator.translate(source, this.flags);
    } catch (JsRegExpTranslator.Failure failure) {
      throw new SyntaxError(source, this.flags, failure.getMessage(), failure.index);
    }
    try {
      this.pattern = Pattern.compile(translation.pattern(), translation.flags());
    } catch (PatternSyntaxException e) {
      throw new SyntaxError(source, this.flags, e.getDescription(), e.getIndex());
    }
  }

  /**
   * {@code new RegExp(source)}.
   *
   * @param source the pattern source
   */
  public JsRegExp(String source) {
    this(source, "");
  }

  private static void validateFlags(String flags) {
    for (int i = 0; i < flags.length(); i++) {
      char flag = flags.charAt(i);
      if ("dgimsuvy".indexOf(flag) < 0 || flags.indexOf(flag, i + 1) >= 0
          || (flag == 'u' && flags.indexOf('v') >= 0)) {
        throw new SyntaxError("Invalid flags supplied to RegExp constructor '" + flags + "'");
      }
    }
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
   * @return the compiled Java pattern (the translation of the JavaScript source)
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

  /**
   * JavaScript's {@code SyntaxError} for an invalid regular expression or invalid flags. It is a
   * {@link PatternSyntaxException} whose message is V8's ({@code Invalid regular expression: /[/: Unterminated
   * character class}); its simple class name gives the title {@code Syntax Error}, as in TypeScript.
   */
  public static final class SyntaxError extends PatternSyntaxException {
    private static final long serialVersionUID = 1L;

    private final String message;

    SyntaxError(String source, String flags, String description, int index) {
      super(description, source, index);
      this.message = "Invalid regular expression: /" + source + "/" + flags + ": " + description;
    }

    SyntaxError(String message) {
      super(message, "", -1);
      this.message = message;
    }

    @Override
    public String getMessage() {
      return message;
    }
  }
}
