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
 *
 * <p>Bounded evaluation (Java-only, a security measure): filters hand client-supplied patterns to the store, and a
 * backtracking engine can take exponential time on a short value ({@code ^(.*a){12}$}). An evaluation over a value of
 * {@code n} characters may read {@link #budget(int) max(10⁸, 4·n²)} characters of it, and never more than
 * {@link #MAX_STEPS} (backtracking reads them again): enough for patterns that scan the value a few times per start
 * position ({@code .*x.*y} is quadratic), which JavaScript runs in comparable time, and a fraction of a second up to a
 * few seconds of CPU. Beyond that, and when the pattern recurses deeper than the thread's stack allows (a repeated
 * alternation over a very long value), it fails with {@link TooComplexError} instead of pinning a thread or throwing
 * {@link StackOverflowError}. JavaScript has no such limit; patterns that stay within it behave exactly the same.
 */
public final class JsRegExp {

  /** The smallest budget of an evaluation, in characters read (about half a second). */
  public static final long MIN_STEPS = 100_000_000L;

  /** The largest budget of an evaluation, in characters read (a few seconds). */
  public static final long MAX_STEPS = 1_000_000_000L;

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
    return find(pattern, value, this::toString);
  }

  /**
   * An unanchored search with the bounded evaluation of {@link JsRegExp}.
   *
   * @param pattern the pattern
   * @param value the string to search
   * @return whether the pattern occurs in the value
   * @throws TooComplexError when the evaluation exceeds its {@link #budget(int) budget} or the stack
   */
  public static boolean find(Pattern pattern, String value) {
    return find(pattern, value, () -> "/" + pattern.pattern() + "/");
  }

  /**
   * @param length the length of the value an evaluation searches
   * @return the characters it may read: {@code max(MIN_STEPS, 4·length²)}, at most {@link #MAX_STEPS}
   */
  public static long budget(int length) {
    return Math.min(MAX_STEPS, Math.max(MIN_STEPS, 4L * length * length));
  }

  private static boolean find(Pattern pattern, String value, java.util.function.Supplier<String> literal) {
    try {
      return pattern.matcher(new BoundedInput(value, budget(value.length()))).find();
    } catch (BoundedInput.Exhausted | StackOverflowError e) {
      throw new TooComplexError("The regular expression " + literal.get() + " is too complex to evaluate against a "
          + "value of " + value.length() + " characters; simplify it");
    }
  }

  /** The input of one evaluation: counts the characters the engine reads and stops it beyond its budget. */
  private static final class BoundedInput implements CharSequence {
    /** Thrown when the budget is spent; preallocated, as it is only a signal. */
    private static final class Exhausted extends RuntimeException {
      private static final long serialVersionUID = 1L;
      private static final Exhausted INSTANCE = new Exhausted();

      private Exhausted() {
        super(null, null, false, false);
      }
    }

    private final String value;
    private long remaining;

    BoundedInput(String value, long budget) {
      this.value = value;
      this.remaining = budget;
    }

    @Override
    public char charAt(int index) {
      if (--remaining < 0) {
        throw Exhausted.INSTANCE;
      }
      return value.charAt(index);
    }

    @Override
    public int length() {
      return value.length();
    }

    @Override
    public CharSequence subSequence(int start, int end) {
      return value.substring(start, end);
    }

    @Override
    public String toString() {
      return value;
    }
  }

  /**
   * A regular expression whose evaluation exceeded its {@link #budget(int) budget} (catastrophic backtracking) or the
   * stack. The search that evaluated it fails with this error.
   */
  public static final class TooComplexError extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    TooComplexError(String message) {
      super(message);
    }
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
