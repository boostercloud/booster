package com.boostercloud.cli.integration.helper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The assertions the TypeScript CLI suite repeats in every file.
 *
 * <ul>
 *   <li>The progress output: {@code new RegExp([...steps].join('(.|\n)*'))}, i.e. the steps appear in this order,
 *       anything in between. Here each step is a literal ({@link Pattern#quote}) and the gap is {@code [\s\S]*}:
 *       Java's backtracking engine recurses on {@code (.|\n)*} and overflows the stack on long outputs (the Maven
 *       output of a failed build); {@code [\s\S]*} also accepts a carriage return, which JavaScript's {@code .} does
 *       not.</li>
 *   <li>Validation errors: the TypeScript tests read {@code stderr} from a <em>resolved</em> {@code execa} promise,
 *       so the CLI must print the error and still exit with code 0; {@link #assertFailsWithMessage} checks both.</li>
 * </ul>
 */
public final class Expectations {
  private Expectations() {}

  /**
   * @param steps the texts that must appear in this order
   * @return the pattern matching any output that contains them in order
   */
  public static Pattern expectedOutput(String... steps) {
    return Pattern.compile(Arrays.stream(steps).map(Pattern::quote).collect(Collectors.joining("[\\s\\S]*")));
  }

  /**
   * {@code expect(stdout).to.match(expectedOutputRegex)}.
   *
   * @param result a finished invocation
   * @param steps the texts that must appear in this order on the standard output
   */
  public static void assertOutputSteps(CliResult result, String... steps) {
    assertThat(result.stdout()).as(result.describe()).containsPattern(expectedOutput(steps));
  }

  /**
   * A successful invocation: exit code 0 ({@code execa} resolves) and the steps on the standard output.
   *
   * @param result a finished invocation
   * @param steps the texts that must appear in this order on the standard output
   */
  public static void assertSucceeds(CliResult result, String... steps) {
    assertThat(result.exitCode()).as(result.describe()).isZero();
    assertOutputSteps(result, steps);
  }

  /**
   * {@code const { stderr } = await command(...); expect(stderr).to.match(/message/)}: the message on the standard
   * error and exit code 0 (the promise resolved).
   *
   * @param result a finished invocation
   * @param message the expected text
   */
  public static void assertFailsWithMessage(CliResult result, String message) {
    assertThat(result.stderr()).as(result.describe()).contains(message);
    assertThat(result.exitCode()).as(result.describe()).isZero();
  }
}
