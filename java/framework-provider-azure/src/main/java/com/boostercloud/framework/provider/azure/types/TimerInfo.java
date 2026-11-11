package com.boostercloud.framework.provider.azure.types;

import com.boostercloud.framework.types.BoosterJson;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * The {@code timer} object of a timer trigger ({@code AzureTimerFunctionInput.timer} in TypeScript).
 *
 * @param isPastDue whether the invocation is late ({@code null} when the trigger did not say; then the input is not
 *     recognised as a timer input, like TypeScript's {@code typeof timer.isPastDue === 'boolean'} check)
 * @param schedule the schedule settings, optional
 * @param scheduleStatus last/next occurrences, optional
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record TimerInfo(Boolean isPastDue, Schedule schedule, ScheduleStatus scheduleStatus) {

  /**
   * @param adjustForDST whether the schedule adjusts for daylight saving time
   */
  @JsonInclude(JsonInclude.Include.NON_ABSENT)
  public record Schedule(Boolean adjustForDST) {}

  /**
   * @param last the last occurrence
   * @param next the next occurrence
   * @param lastUpdated when the status was updated
   */
  @JsonInclude(JsonInclude.Include.NON_ABSENT)
  public record ScheduleStatus(String last, String next, String lastUpdated) {}

  /**
   * Parses the JSON the Azure Functions Java worker passes to a {@code @TimerTrigger String} parameter
   * ({@code {"Schedule":{"AdjustForDST":true},"ScheduleStatus":{...},"IsPastDue":false}}). Both PascalCase (Java/.NET
   * worker) and camelCase (Node worker) property names are accepted.
   *
   * @param json the timer JSON, or {@code null}/blank
   * @return the timer information ({@code isPastDue = false} when the JSON is absent)
   */
  public static TimerInfo fromJson(String json) {
    if (json == null || json.isBlank()) {
      return new TimerInfo(false, null, null);
    }
    Object parsed = BoosterJson.parse(json);
    if (!(parsed instanceof Map<?, ?> raw)) {
      return new TimerInfo(false, null, null);
    }
    Object pastDue = property(raw, "isPastDue");
    Schedule schedule = null;
    if (property(raw, "schedule") instanceof Map<?, ?> rawSchedule) {
      Object adjust = property(rawSchedule, "adjustForDST");
      schedule = new Schedule(adjust instanceof Boolean b ? b : null);
    }
    ScheduleStatus status = null;
    if (property(raw, "scheduleStatus") instanceof Map<?, ?> rawStatus) {
      status = new ScheduleStatus(
          stringOrNull(property(rawStatus, "last")),
          stringOrNull(property(rawStatus, "next")),
          stringOrNull(property(rawStatus, "lastUpdated")));
    }
    return new TimerInfo(pastDue instanceof Boolean b ? b : Boolean.FALSE, schedule, status);
  }

  private static Object property(Map<?, ?> raw, String camelCaseName) {
    if (raw.containsKey(camelCaseName)) {
      return raw.get(camelCaseName);
    }
    for (Map.Entry<?, ?> entry : raw.entrySet()) {
      if (String.valueOf(entry.getKey()).equalsIgnoreCase(camelCaseName)) {
        return entry.getValue();
      }
    }
    return null;
  }

  private static String stringOrNull(Object value) {
    return value == null ? null : value.toString();
  }
}
