package com.boostercloud.framework.provider.local.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@link JsRegExp} matches like JavaScript's {@code RegExp}. The expected results were computed with Node.js 24
 * ({@code new RegExp(source, flags).test(input)}, or the {@code SyntaxError} message), so these rows pin V8's
 * behaviour, including the Annex B syntax that Java reads differently.
 */
class JsRegExpTest {

  private static Arguments test(String source, String flags, String input, boolean expected) {
    return Arguments.of(source, flags, input, expected);
  }

  private static Arguments error(String source, String flags, String message) {
    return Arguments.of(source, flags, message);
  }

  static Stream<Arguments> matches() {
    return Stream.of(
        test("{", "", "a{b", true),
        test("{", "", "ab", false),
        test("a{", "", "a{", true),
        test("a{", "", "a", false),
        test("a{1", "", "a{1", true),
        test("a{1", "", "a", false),
        test("a{,5}", "", "a{,5}", true),
        test("a{,5}", "", "aaaaa", false),
        test("a{2}", "", "aa", true),
        test("a{2}", "", "a", false),
        test("a{2,}", "", "aaa", true),
        test("a{2,}", "", "a", false),
        test("a{1,2}?b", "", "aab", true),
        test("}", "", "}", true),
        test("}", "", "x", false),
        test("]", "", "]", true),
        test("]", "", "x", false),
        test("price $5", "", "price $5", false),
        test("price $5", "", "price 5", false),
        test("\\$5", "", "$5", true),
        test("c$", "", "abc", true),
        test("c$", "", "abc\n", false),
        test("c$", "", "c\nd", false),
        test("c$", "m", "abc", true),
        test("c$", "m", "abc\n", true),
        test("c$", "m", "c\nd", true),
        test("c$", "m", "c\rd", true),
        test("c$", "m", "c\u2028d", true),
        test("c$", "m", "c\u0085d", false),
        test("^d", "m", "c\nd", true),
        test("^d", "m", "c\rd", true),
        test("^d", "m", "c\u2028d", true),
        test("^d", "m", "c\u0085d", false),
        test("^d", "m", "xd", false),
        test("^$", "m", "a\n", true),
        test("^$", "m", "\n", true),
        test("^$", "m", "a", false),
        test("a.c", "", "abc", true),
        test("a.c", "", "a\nc", false),
        test("a.c", "", "a\u0085c", true),
        test("a.c", "", "a\u2028c", false),
        test("a.c", "", "a\rc", false),
        test("a.c", "s", "a\nc", true),
        test("a.c", "s", "a\u0085c", true),
        test("\\s", "", "\u00a0", true),
        test("\\s", "", "\u3000", true),
        test("\\s", "", "\ufeff", true),
        test("\\s", "", "\u0085", false),
        test("\\s", "", " ", true),
        test("\\s", "", "x", false),
        test("\\s", "", "\u000b", true),
        test("\\S", "", "\u00a0", false),
        test("\\S", "", "x", true),
        test("[\\s]", "", "\u00a0", true),
        test("[\\s]", "", "x", false),
        test("[\\S]", "", "\u00a0", false),
        test("[\\S]", "", "x", true),
        test("[^\\S]", "", "\u00a0", true),
        test("[^\\S]", "", "x", false),
        test("[^\\S]", "", " ", true),
        test("[^\\s]", "", "\u00a0", false),
        test("[^\\s]", "", "x", true),
        test("[x\\S]", "", " ", false),
        test("[x\\S]", "", "x", true),
        test("[x\\S]", "", "y", true),
        test("\\v", "", "\u000b", true),
        test("\\v", "", "\n", false),
        test("\\e", "", "e", true),
        test("\\e", "", "\u001b", false),
        test("\\a", "", "a", true),
        test("\\a", "", "\u0007", false),
        test("\\q", "", "q", true),
        test("\\x", "", "x", true),
        test("\\x4", "", "x4", true),
        test("\\x41", "", "A", true),
        test("\\u", "", "u", true),
        test("\\u004", "", "u004", true),
        test("\\u0041", "", "A", true),
        test("\\c", "", "\\c", true),
        test("\\c", "", "c", false),
        test("\\cA", "", "\u0001", true),
        test("\\ca", "", "\u0001", true),
        test("\\ca", "", "!", false),
        test("[\\c1]", "", "\u0011", true),
        test("[\\c1]", "", "c", false),
        test("\\0", "", "\u0000", true),
        test("\\0", "", "0", false),
        test("\\012", "", "\n", true),
        test("\\012", "", "012", false),
        test("\\101", "", "A", true),
        test("\\8", "", "8", true),
        test("(a)\\1", "", "aa", true),
        test("(a)\\1", "", "a1", false),
        test("(a)\\2", "", "a\u0002", true),
        test("(a)\\2", "", "a2", false),
        test("(a)\\10", "", "aa0", false),
        test("(a)\\10", "", "a\b", true),
        test("\\Q", "", "Q", true),
        test("\\p{L}", "", "p{L}", true),
        test("\\p{L}", "", "x", false),
        test("\\p{L}", "u", "x", true),
        test("\\p{L}", "u", "1", false),
        test("\\k", "", "k", true),
        test("(?<n>a)\\k<n>", "", "aa", true),
        test("(?<n>a)\\k<n>", "", "ab", false),
        test("(?<$x_1>a)\\k<$x_1>", "", "aa", true),
        test("[]", "", "a", false),
        test("[]", "", "", false),
        test("[^]", "", "a", true),
        test("[^]", "", "\n", true),
        test("a[]*b", "", "ab", true),
        test("[[]", "", "[", true),
        test("[a&&b]", "", "&", true),
        test("[a&&b]", "", "a", true),
        test("[\\b]", "", "\b", true),
        test("[\\b]", "", "b", false),
        test("[\\d-z]", "", "-", true),
        test("[\\d-z]", "", "5", true),
        test("[\\d-z]", "", "z", true),
        test("[\\d-z]", "", "m", false),
        test("[a-]", "", "-", true),
        test("[-a]", "", "-", true),
        test("[\\B]", "", "B", true),
        test("[\\k]", "", "k", true),
        test("[\\1]", "", "\u0001", true),
        test("[\\1]", "", "1", false),
        test("[\\9]", "", "9", true),
        test("[a^]", "", "^", true),
        test("[.]", "", ".", true),
        test("[.]", "", "a", false),
        test("(?=a)*b", "", "b", true),
        test("A", "i", "a", true),
        test("\u00e9", "i", "\u00c9", true),
        test("[a-z]", "i", "Q", true),
        test("\\w", "", "\u00e9", false),
        test("\\w", "", "_", true),
        test("\\bfoo\\b", "", "a foo b", true),
        test("\\bfoo\\b", "", "afoo", false),
        test("\\bfoo\\b", "", "\u00e9foo", true),
        test("\\d", "", "\u0663", false),
        test("\\d", "", "3", true),
        test("#", "", "#", true),
        test("a b", "", "a b", true),
        test("/", "", "/", true),
        test("\\/", "", "/", true),
        test("a|", "", "x", true),
        test("(|a)", "", "x", true),
        test("a{2147483648}", "", "a", false),
        test("foo[bar]", "", "foor", true),
        test("foo[bar]", "", "foo[bar]", false),
        test("{name}", "", "{name}", true),
        test("{name}", "", "name", false),
        test("50%", "", "50%", true),
        test("user@example.com", "", "user@example.com", true),
        test("user@example.com", "", "user@exampleXcom", true),
        test("(test)", "", "a test", true),
        test("[^a-z]", "i", "Q", false),
        test("[^a-z]", "i", "1", true),
        test("^Bar", "i", "bar baz", true),
        test("^Bar", "i", "a bar", false),
        test("\\p{Lu}", "u", "A", true),
        test("\\p{Lu}", "u", "a", false),
        test("[\\p{Lu}\\d]", "u", "A", true),
        test("[\\p{Lu}\\d]", "u", "1", true),
        test("[\\p{Lu}\\d]", "u", "a", false),
        test("\\P{L}", "u", "1", true),
        test("\\P{L}", "u", "a", false),
        test("\\p{Script=Greek}", "u", "\u03b1", true),
        test("\\p{Script=Greek}", "u", "a", false),
        test("\\u{1F600}", "u", "\ud83d\ude00", true),
        test("\\u{1F600}", "u", "x", false),
        test("[\\p{L}]", "", "p", true),
        test("[\\p{L}]", "", "L", true),
        test("[\\p{L}]", "", "{", true),
        test("[\\p{L}]", "", "x", false)
    );
  }

  static Stream<Arguments> errors() {
    return Stream.of(
        error("C++", "", "Invalid regular expression: /C++/: Nothing to repeat"),
        error("(", "", "Invalid regular expression: /(/: Unterminated group"),
        error(")", "", "Invalid regular expression: /)/: Unmatched ')'"),
        error("[", "", "Invalid regular expression: /[/: Unterminated character class"),
        error("*", "", "Invalid regular expression: /*/: Nothing to repeat"),
        error("a**", "", "Invalid regular expression: /a**/: Nothing to repeat"),
        error("a*+", "", "Invalid regular expression: /a*+/: Nothing to repeat"),
        error("a{2}+", "", "Invalid regular expression: /a{2}+/: Nothing to repeat"),
        error("+a", "", "Invalid regular expression: /+a/: Nothing to repeat"),
        error("?", "", "Invalid regular expression: /?/: Nothing to repeat"),
        error("a|*", "", "Invalid regular expression: /a|*/: Nothing to repeat"),
        error("(*)", "", "Invalid regular expression: /(*)/: Nothing to repeat"),
        error("{1}", "", "Invalid regular expression: /{1}/: Nothing to repeat"),
        error("^*", "", "Invalid regular expression: /^*/: Nothing to repeat"),
        error("$+", "", "Invalid regular expression: /$+/: Nothing to repeat"),
        error("\\b*", "", "Invalid regular expression: /\\b*/: Nothing to repeat"),
        error("(?<=a)*", "", "Invalid regular expression: /(?<=a)*/: Invalid quantifier"),
        error("a{2,1}", "", "Invalid regular expression: /a{2,1}/: numbers out of order in {} quantifier"),
        error("[z-a]", "", "Invalid regular expression: /[z-a]/: Range out of order in character class"),
        error("\\", "", "Invalid regular expression: /\\/: \\ at end of pattern"),
        error("(?i)a", "", "Invalid regular expression: /(?i)a/: Invalid group"),
        error("(?>a)", "", "Invalid regular expression: /(?>a)/: Invalid group"),
        error("(?<1>a)", "", "Invalid regular expression: /(?<1>a)/: Invalid capture group name"),
        error("(?<n>a)\\k<m>", "", "Invalid regular expression: /(?<n>a)\\k<m>/: Invalid named capture referenced"),
        error("(?<n>a)\\k", "", "Invalid regular expression: /(?<n>a)\\k/: Invalid named reference"),
        error("{", "u", "Invalid regular expression: /{/u: Lone quantifier brackets"),
        error("\\e", "u", "Invalid regular expression: /\\e/u: Invalid escape"),
        error("a", "gg", "Invalid flags supplied to RegExp constructor 'gg'"),
        error("a", "x", "Invalid flags supplied to RegExp constructor 'x'"),
        error("a", "uv", "Invalid flags supplied to RegExp constructor 'uv'")
    );
  }

  @ParameterizedTest(name = "/{0}/{1}.test({2}) === {3}")
  @MethodSource("matches")
  void testsLikeJavaScript(String source, String flags, String input, boolean expected) {
    assertThat(new JsRegExp(source, flags).test(input)).isEqualTo(expected);
  }

  @ParameterizedTest(name = "new RegExp({0}, {1}) throws {2}")
  @MethodSource("errors")
  void rejectsWhatJavaScriptRejects(String source, String flags, String message) {
    assertThatThrownBy(() -> new JsRegExp(source, flags))
        .isInstanceOf(JsRegExp.SyntaxError.class)
        .isInstanceOf(PatternSyntaxException.class)
        .hasMessage(message);
  }

  @Test
  void catastrophicBacktrackingStopsWithAnErrorInsteadOfPinningTheThread() {
    JsRegExp regExp = new JsRegExp("^(.*a){20}$");
    String value = "a".repeat(40) + "!";
    long start = System.nanoTime();
    org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(30), () ->
        assertThatThrownBy(() -> regExp.test(value))
            .isInstanceOf(JsRegExp.TooComplexError.class)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("The regular expression /^(.*a){20}$/ is too complex to evaluate against a value of 41 "
                + "characters; simplify it"));
    System.out.println("Budget of " + JsRegExp.budget(value.length()) + " steps spent in "
        + (System.nanoTime() - start) / 1_000_000 + " ms");
    // raw Java patterns handed to the store are bounded the same way
    assertThatThrownBy(() -> JsRegExp.find(java.util.regex.Pattern.compile("^(.*a){20}$"), value))
        .isInstanceOf(JsRegExp.TooComplexError.class)
        .hasMessageStartingWith("The regular expression /^(.*a){20}$/ is too complex");
  }

  @Test
  void patternsWithinTheBudgetAreUnaffected() {
    String value = "a".repeat(4000) + "x" + "b".repeat(4000) + "y";
    assertThat(new JsRegExp(".*x.*y").test(value)).isTrue();
    // quadratic backtracking over 8000 characters stays within the budget, which grows with the value
    assertThat(new JsRegExp(".*x.*z").test(value)).isFalse();
    assertThat(JsRegExp.budget(41)).isEqualTo(JsRegExp.MIN_STEPS);
    assertThat(JsRegExp.budget(8002)).isEqualTo(4L * 8002 * 8002);
    assertThat(JsRegExp.budget(1_000_000)).isEqualTo(JsRegExp.MAX_STEPS);
    assertThat(new JsRegExp("^(a+)+$").test("a".repeat(30) + "!")).isFalse();
    assertThat(new JsRegExp("y$").test(value)).isTrue();
  }

  @Test
  void recursionDeeperThanTheStackBecomesTheSameError() {
    assertThatThrownBy(() -> new JsRegExp("(a|b)*c").test("ab".repeat(500_000)))
        .isInstanceOf(JsRegExp.TooComplexError.class)
        .hasMessage("The regular expression /(a|b)*c/ is too complex to evaluate against a value of 1000000 "
            + "characters; simplify it");
  }

  @Test
  void syntaxErrorsAreTitledLikeTypeScript() {
    assertThatThrownBy(() -> new JsRegExp("(")).satisfies(e -> assertThat(e.getClass().getSimpleName())
        .isEqualTo("SyntaxError"));
  }

  @Test
  void equalityIsBySourceAndFlags() {
    assertThat(new JsRegExp("a{", "i")).isEqualTo(new JsRegExp("a{", "i")).hasToString("/a{/i");
    assertThat(new JsRegExp("a")).isNotEqualTo(new JsRegExp("a", "i"));
    assertThat(new JsRegExp("a{").source()).isEqualTo("a{");
    assertThat(List.of(new JsRegExp("x").flags(), new JsRegExp("x", null).flags())).containsOnly("");
  }
}
