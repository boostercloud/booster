package com.boostercloud.cli.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.cli.common.ColorSupport;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Port of {@code test/services/user-prompt.test.ts}. The answers come from the standard input instead of a
 * stubbed inquirer.
 */
class PrompterTest {
  private ByteArrayOutputStream output;

  @BeforeEach
  void setUp() {
    ColorSupport.setLevel(0);
    output = new ByteArrayOutputStream();
  }

  private Prompter prompter(String input) {
    return new Prompter(
        new BufferedReader(new StringReader(input)), new PrintStream(output, true, StandardCharsets.UTF_8), null);
  }

  private String printed() {
    return output.toString(StandardCharsets.UTF_8);
  }

  @Test
  void defaultOrPromptWithProvidedValue() {
    assertThat(prompter("").defaultOrPrompt("randomValue", "Enter a value")).isEqualTo("randomValue");
    assertThat(printed()).isEmpty();
  }

  @Test
  void defaultOrPromptWithNullValue() {
    assertThat(prompter("mockedValue\n").defaultOrPrompt(null, "Enter a value")).isEqualTo("mockedValue");
    assertThat(printed()).isEqualTo("? Enter a value ");
  }

  @Test
  void defaultOrPromptWithEmptyValueAsks() {
    assertThat(prompter("mockedValue\n").defaultOrPrompt("", "Enter a value")).isEqualTo("mockedValue");
  }

  @Test
  void defaultOrPromptKeepsSingleQuotes() {
    assertThat(prompter("").defaultOrPrompt("'randomValue'", "Enter a value")).isEqualTo("'randomValue'");
    assertThat(prompter("'mockedValue'\n").defaultOrPrompt(null, "Enter a value")).isEqualTo("'mockedValue'");
  }

  @Test
  void defaultOrPromptKeepsDoubleQuotes() {
    // TypeScript escapes them for package.json; the Java templates escape values for their own format
    assertThat(prompter("").defaultOrPrompt("\"randomValue\"", "Enter a value")).isEqualTo("\"randomValue\"");
    assertThat(prompter("\"mockedValue\"\n").defaultOrPrompt(null, "Enter a value")).isEqualTo("\"mockedValue\"");
  }

  @Test
  void defaultOrPromptAnswersEmptyWhenTheInputIsExhausted() {
    assertThat(prompter("").defaultOrPrompt(null, "Enter a value")).isEmpty();
  }

  @Test
  void defaultOrChooseWithProvidedValue() {
    assertThat(prompter("").defaultOrChoose("value1", "Choose a value", List.of("value1", "value2", "value3")))
        .isEqualTo("value1");
  }

  @Test
  void defaultOrChooseByNumber() {
    assertThat(prompter("2\n").defaultOrChoose(null, "Choose a value", List.of("value1", "value2", "value3")))
        .isEqualTo("value2");
    assertThat(printed()).contains("? Choose a value").contains("  1) value1").contains("  3) value3");
  }

  @Test
  void defaultOrChooseByLabel() {
    assertThat(prompter("value3\n").defaultOrChoose(null, "Choose a value", List.of("value1", "value2", "value3")))
        .isEqualTo("value3");
  }

  @Test
  void defaultOrChooseDefaultsToTheFirstOption() {
    assertThat(prompter("\n").defaultOrChoose(null, "Choose a value", List.of("value1", "value2"))).isEqualTo("value1");
    assertThat(prompter("").defaultOrChoose(null, "Choose a value", List.of("value1", "value2"))).isEqualTo("value1");
  }

  @Test
  void defaultOrChooseAsksAgainOnInvalidAnswers() {
    assertThat(prompter("7\nnope\n2\n").defaultOrChoose(null, "Choose a value", List.of("value1", "value2")))
        .isEqualTo("value2");
    assertThat(printed()).contains("Please enter a number between 1 and 2.");
  }

  @Test
  void confirmPromptReturnsTheAnswer() {
    assertThat(prompter("y\n").confirmPrompt("Delete resource?")).isTrue();
    assertThat(prompter("YES\n").confirmPrompt("Delete resource?")).isTrue();
    assertThat(prompter("n\n").confirmPrompt("Delete resource?")).isFalse();
    assertThat(printed()).contains("? Delete resource? (y/N) ");
  }

  @Test
  void confirmPromptDefaultsToNo() {
    assertThat(prompter("\n").confirmPrompt("Delete resource?")).isFalse();
    assertThat(prompter("").confirmPrompt("Delete resource?")).isFalse();
  }
}
