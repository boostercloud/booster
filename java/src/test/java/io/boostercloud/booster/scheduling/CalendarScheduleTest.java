package io.boostercloud.booster.scheduling;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CalendarScheduleTest {
  @Test void azureAttributesPreserveEmptyDisabledAndIgnoredYear() {
    var disabled = CalendarSchedule.fromAzureAttributes(Map.of());
    assertFalse(disabled.enabled()); assertTrue(disabled.nextAfter(Instant.EPOCH).isEmpty());
    var yearOnly = CalendarSchedule.fromAzureAttributes(Map.of("year", "2020"));
    assertEquals("0 * * * * *", yearOnly.azureCron());
    assertTrue(yearOnly.matches(Instant.parse("2026-11-04T12:00:00Z")));
  }
  @Test void rangesListsStepsAndNamedMonthsAndWeekDaysSelectTheNextCalendarTime() {
    var schedule = new CalendarSchedule("0,15-45/15", "9-17/2", "*", "NOV,DEC", "MON-FRI", "*");
    assertEquals(Instant.parse("2026-11-09T09:00:00Z"), schedule.nextAfter(Instant.parse("2026-11-06T17:45:00Z")).orElseThrow());
    assertEquals(Instant.parse("2026-11-09T09:30:00Z"), schedule.nextAfter(Instant.parse("2026-11-09T09:15:00.001Z")).orElseThrow());
    assertFalse(schedule.matches(Instant.parse("2026-11-09T09:30:01Z")));
    var fullNames = CalendarSchedule.fromAzureAttributes(Map.of("minute", "30", "month", "November", "weekDay", "Monday"));
    assertTrue(fullNames.matches(Instant.parse("2026-11-09T09:30:00Z")));
  }
  @Test void azureDayAndWeekDayAreBothRequiredAndLeapDatesAreRealDates() {
    var friday13 = CalendarSchedule.fromAzureAttributes(Map.of("minute", "0", "hour", "0", "day", "13", "weekDay", "FRI"));
    assertEquals(Instant.parse("2026-02-13T00:00:00Z"), friday13.nextAfter(Instant.parse("2026-01-01T00:00:00Z")).orElseThrow());
    var leap = new CalendarSchedule("0", "0", "29", "FEB", "*", "2028,2032");
    assertEquals(Instant.parse("2028-02-29T00:00:00Z"), leap.nextAfter(Instant.parse("2026-01-01T00:00:00Z")).orElseThrow());
    assertEquals(Instant.parse("2032-02-29T00:00:00Z"), leap.nextAfter(Instant.parse("2028-02-29T00:00:00Z")).orElseThrow());
    assertTrue(leap.nextAfter(Instant.parse("2032-02-29T00:00:00Z")).isEmpty());
  }
  @Test void invalidExpressionsFailAtConfigurationAndNextIsStrictlyLater() {
    for (String minute : new String[] {"", "60", "*/0", "5-2", "?", "1,,2", "1/", "one"})
      assertThrows(IllegalArgumentException.class, () -> new CalendarSchedule(minute, "*", "*", "*", "*", "*"));
    assertThrows(IllegalArgumentException.class, () -> CalendarSchedule.fromAzureAttributes(Map.of("seconds", "*")));
    var everyMinute = CalendarSchedule.fromAzureAttributes(Map.of("minute", "*"));
    assertEquals(Instant.parse("2026-11-04T12:01:00Z"), everyMinute.nextAfter(Instant.parse("2026-11-04T12:00:00Z")).orElseThrow());
    assertTrue(everyMinute.nextAfter(Instant.parse("9999-12-31T23:59:00Z")).isEmpty());
  }
}
