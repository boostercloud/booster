package com.boostercloud.framework.types;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Port of {@code schedule.ts}: when a scheduled command runs, as cron fields. Every field is optional; consumers
 * treat an absent field as {@code *}. The local provider builds a 5-field expression and ignores {@code year}.
 *
 * @param minute cron minute field, e.g. {@code "0/5"}
 * @param hour cron hour field
 * @param day cron day-of-month field
 * @param month cron month field
 * @param weekDay cron day-of-week field, e.g. {@code "MON-FRI"}
 * @param year cron year field (only used by cloud providers that support it)
 */
public record ScheduleInterface(String minute, String hour, String day, String month, String weekDay, String year) {

  /**
   * @return whether no field is set (TypeScript {@code {}}; the Azure provider skips such schedules)
   */
  @JsonIgnore
  public boolean isEmpty() {
    return minute == null && hour == null && day == null && month == null && weekDay == null && year == null;
  }

  /**
   * The 5-field cron expression used by the local provider ({@code minute hour day month weekDay}, absent fields
   * as {@code *}).
   *
   * @return the expression
   */
  public String toFiveFieldCron() {
    return orStar(minute) + " " + orStar(hour) + " " + orStar(day) + " " + orStar(month) + " " + orStar(weekDay);
  }

  private static String orStar(String value) {
    return value == null ? "*" : value;
  }
}
