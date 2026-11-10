package com.boostercloud.framework.provider.local.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** {@code cron-parser} semantics of {@link CronExpression}. */
class CronExpressionTest {

  private static final ZoneId UTC = ZoneId.of("UTC");

  private static ZonedDateTime at(String localDateTime) {
    return ZonedDateTime.of(java.time.LocalDateTime.parse(localDateTime), UTC);
  }

  private static List<String> next(String expression, String from, int count) {
    CronExpression cron = CronExpression.parse(expression);
    List<String> times = new ArrayList<>();
    ZonedDateTime time = at(from);
    for (int i = 0; i < count; i++) {
      time = cron.next(time).orElseThrow();
      times.add(time.toLocalDateTime().toString());
    }
    return times;
  }

  @Test
  void everyMinuteRunsAtSecondZero() {
    assertThat(next("* * * * *", "2024-01-31T10:20:30", 3))
        .containsExactly("2024-01-31T10:21", "2024-01-31T10:22", "2024-01-31T10:23");
  }

  @Test
  void theNextRunIsStrictlyAfterTheReference() {
    assertThat(next("* * * * *", "2024-01-31T10:20:00", 1)).containsExactly("2024-01-31T10:21");
  }

  @Test
  void aSingleValueStepRepeatsFromThatValue() {
    assertThat(next("0/5 * * * *", "2024-01-31T10:03:00", 3))
        .containsExactly("2024-01-31T10:05", "2024-01-31T10:10", "2024-01-31T10:15");
    assertThat(next("*/20 * * * *", "2024-01-31T10:00:00", 3))
        .containsExactly("2024-01-31T10:20", "2024-01-31T10:40", "2024-01-31T11:00");
    assertThat(next("10-30/10 * * * *", "2024-01-31T10:00:00", 4))
        .containsExactly("2024-01-31T10:10", "2024-01-31T10:20", "2024-01-31T10:30", "2024-01-31T11:10");
  }

  @Test
  void listsAndHours() {
    assertThat(next("0 1,5,10 * * *", "2024-01-31T02:00:00", 3))
        .containsExactly("2024-01-31T05:00", "2024-01-31T10:00", "2024-02-01T01:00");
  }

  @Test
  void monthsAndDaysOfWeekAcceptNames() {
    assertThat(next("0 9 * jan,MAR MON-fri", "2024-01-31T10:00:00", 2))
        .containsExactly("2024-03-01T09:00", "2024-03-04T09:00");
  }

  @Test
  void sevenIsSunday() {
    assertThat(next("0 0 * * 7", "2024-01-31T00:00:00", 1)).containsExactly("2024-02-04T00:00");
    assertThat(next("0 0 * * 0", "2024-01-31T00:00:00", 1)).containsExactly("2024-02-04T00:00");
  }

  @Test
  void restrictedDayOfMonthAndDayOfWeekAreCombinedWithOr() {
    // the 15th or any Monday
    assertThat(next("0 0 15 * 1", "2024-02-01T00:00:00", 4))
        .containsExactly("2024-02-05T00:00", "2024-02-12T00:00", "2024-02-15T00:00", "2024-02-19T00:00");
  }

  @Test
  void specialDaysOfMonthAndWeek() {
    assertThat(next("0 0 L * *", "2024-02-01T00:00:00", 2)).containsExactly("2024-02-29T00:00", "2024-03-31T00:00");
    assertThat(next("0 0 * * 5L", "2024-02-01T00:00:00", 1)).containsExactly("2024-02-23T00:00");
    assertThat(next("0 0 * * 5#2", "2024-02-01T00:00:00", 2)).containsExactly("2024-02-09T00:00", "2024-03-08T00:00");
    assertThat(next("0 0 ? * *", "2024-02-01T00:00:00", 1)).containsExactly("2024-02-02T00:00");
  }

  @Test
  void sixFieldsStartWithSeconds() {
    assertThat(next("*/30 * * * * *", "2024-01-31T10:20:00", 3))
        .containsExactly("2024-01-31T10:20:30", "2024-01-31T10:21", "2024-01-31T10:21:30");
  }

  @Test
  void missingLeadingFieldsTakeTheDefaults() {
    // cron-parser aligns the fields to the right: "5 * * *" is "0 * 5 * * *"
    assertThat(next("5 * * *", "2024-01-31T10:20:00", 1)).containsExactly("2024-02-01T05:00");
  }

  @Test
  void impossibleDatesNeverMatch() {
    assertThat(CronExpression.parse("0 0 30 2 *").next(at("2024-01-01T00:00:00"))).isEmpty();
  }

  @Test
  void leapDaysAreFound() {
    assertThat(next("0 0 29 2 *", "2024-03-01T00:00:00", 1)).containsExactly("2028-02-29T00:00");
  }

  @Test
  void daylightSavingGapsRunAfterTheGap() {
    ZoneId madrid = ZoneId.of("Europe/Madrid");
    CronExpression cron = CronExpression.parse("30 2 * * *");
    ZonedDateTime next = cron.next(ZonedDateTime.of(2024, 3, 31, 0, 0, 0, 0, madrid)).orElseThrow();
    assertThat(next.toLocalDateTime().toString()).isEqualTo("2024-03-31T03:30");
  }

  @ParameterizedTest
  @CsvSource(delimiter = ';', value = {
      "60 * * * *; Constraint error, got value 60 expected range 0-59",
      "* 24 * * *; Constraint error, got value 24 expected range 0-23",
      "* * 0 * *; Constraint error, got value 0 expected range 1-31",
      "* * * 13 *; Constraint error, got value 13 expected range 1-12",
      "5-1 * * * *; Invalid range: 5-1",
      "*/0 * * * *; Constraint error, cannot repeat at every 0 time.",
      "a * * * *; Invalid characters, got value: a",
      "* * * foo *; Validation error, cannot resolve alias \"foo\"",
      "1/2/3 * * * *; Invalid repeat: 1/2/3",
      "* * * * * * *; Invalid cron expression"})
  void invalidExpressionsAreRejected(String expression, String message) {
    assertThatThrownBy(() -> CronExpression.parse(expression))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(message);
  }
}
