package com.boostercloud.cli.integration.helper;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One answer for an interactive prompt of the CLI: when the output printed since the previous answer contains
 * {@code prompt} (a regular expression, like the keys of the {@code PromptAnswers} object of
 * {@code cli.project.integration.ts}), {@code answer} followed by {@link #INTRO} is written to the standard input.
 *
 * @param prompt what identifies the prompt
 * @param answer the line to type ({@code ""} accepts the default, or the first choice of a list)
 */
public record PromptAnswer(String prompt, String answer) {
  /** "Hit intro" ({@code INTRO} in TypeScript). */
  public static final String INTRO = "\r\n";

  /** Validates the components. */
  public PromptAnswer {
    Objects.requireNonNull(prompt, "prompt");
    Objects.requireNonNull(answer, "answer");
  }

  /**
   * @param prompt what identifies the prompt
   * @param answer the line to type
   * @return the answer
   */
  public static PromptAnswer of(String prompt, String answer) {
    return new PromptAnswer(prompt, answer);
  }

  boolean matches(CharSequence output) {
    return Pattern.compile(prompt).matcher(output).find();
  }
}
