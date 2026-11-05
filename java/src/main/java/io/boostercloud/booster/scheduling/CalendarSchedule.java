package io.boostercloud.booster.scheduling;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Azure's minute-resolution NCRONTAB calendar (seconds are zero, UTC unless the host is configured otherwise). */
public final class CalendarSchedule {
  private static final Map<String, Integer> MONTHS = monthNames();
  private static final Map<String, Integer> DAYS = dayNames();
  private final String minute, hour, day, month, weekDay, year;
  private final BitSet minutes, hours, days, months, weekDays, years;
  private final boolean enabled;

  /** Java calendar with an explicit year field. Azure source compatibility is available through fromAzureAttributes. */
  public CalendarSchedule(String minute, String hour, String day, String month, String weekDay, String year) {
    this(minute, hour, day, month, weekDay, year, true);
  }
  private CalendarSchedule(String minute, String hour, String day, String month, String weekDay, String year, boolean enabled) {
    this.minute = normalize(minute); this.hour = normalize(hour); this.day = normalize(day);
    this.month = normalize(month); this.weekDay = normalize(weekDay); this.year = normalize(year); this.enabled = enabled;
    minutes = parse(this.minute, 0, 59, Map.of()); hours = parse(this.hour, 0, 23, Map.of());
    days = parse(this.day, 1, 31, Map.of()); months = parse(this.month, 1, 12, MONTHS);
    weekDays = parse(this.weekDay, 0, 6, DAYS); years = parse(this.year, 1970, 9999, Map.of());
  }
  /** The TypeScript Azure provider skips {} and omits year from generated six-field NCRONTAB. */
  public static CalendarSchedule fromAzureAttributes(Map<String, String> attributes) {
    Objects.requireNonNull(attributes);
    if (!Set.of("minute", "hour", "day", "month", "weekDay", "year").containsAll(attributes.keySet()))
      throw new IllegalArgumentException("Unknown schedule attribute");
    return new CalendarSchedule(attributes.get("minute"), attributes.get("hour"), attributes.get("day"),
        attributes.get("month"), attributes.get("weekDay"), "*", !attributes.isEmpty());
  }
  public boolean enabled() { return enabled; }
  public String azureCron() { return "0 " + minute + " " + hour + " " + day + " " + month + " " + weekDay; }
  public String descriptor() { return enabled + ":" + azureCron() + ":" + year + ":UTC"; }

  public boolean matches(Instant occurrence) {
    Objects.requireNonNull(occurrence);
    var time = occurrence.atZone(ZoneOffset.UTC);
    return enabled && time.getSecond() == 0 && time.getNano() == 0 && time.getYear() >= 1970 && time.getYear() <= 9999
        && years.get(time.getYear()) && months.get(time.getMonthValue()) && days.get(time.getDayOfMonth())
        && weekDays.get(time.getDayOfWeek().getValue() % 7) && hours.get(time.getHour()) && minutes.get(time.getMinute());
  }
  /** Next nominal occurrence strictly after the argument; no per-minute scan across long calendar gaps. */
  public Optional<Instant> nextAfter(Instant after) {
    Objects.requireNonNull(after);
    if (!enabled) return Optional.empty();
    if (after.isBefore(Instant.parse("1970-01-01T00:00:00Z"))) after = Instant.parse("1969-12-31T23:59:59.999999999Z");
    if (!after.isBefore(Instant.parse("9999-12-31T23:59:00Z"))) return Optional.empty();
    var candidate = after.truncatedTo(ChronoUnit.MINUTES).plus(1, ChronoUnit.MINUTES).atZone(ZoneOffset.UTC);
    while (candidate.getYear() <= 9999) {
      int year = years.nextSetBit(candidate.getYear());
      if (year < 0) return Optional.empty();
      if (year != candidate.getYear()) candidate = LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC);
      int month = months.nextSetBit(candidate.getMonthValue());
      if (month < 0) { candidate = LocalDate.of(year + 1, 1, 1).atStartOfDay(ZoneOffset.UTC); continue; }
      if (month != candidate.getMonthValue()) candidate = LocalDate.of(year, month, 1).atStartOfDay(ZoneOffset.UTC);
      if (!days.get(candidate.getDayOfMonth()) || !weekDays.get(candidate.getDayOfWeek().getValue() % 7)) {
        candidate = candidate.toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC); continue;
      }
      int hour = hours.nextSetBit(candidate.getHour());
      if (hour < 0) { candidate = candidate.toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC); continue; }
      if (hour != candidate.getHour()) candidate = candidate.withHour(hour).withMinute(0);
      int minute = minutes.nextSetBit(candidate.getMinute());
      if (minute < 0) { candidate = candidate.plusHours(1).withMinute(0); continue; }
      return Optional.of(candidate.withMinute(minute).toInstant());
    }
    return Optional.empty();
  }
  private static String normalize(String value) { return value == null ? "*" : value.trim().toUpperCase(Locale.ROOT); }
  private static BitSet parse(String expression, int minimum, int maximum, Map<String, Integer> names) {
    if (expression.isEmpty() || expression.length() > 256) throw new IllegalArgumentException("Invalid schedule field");
    var result = new BitSet(maximum + 1);
    for (String item : expression.split(",", -1)) {
      String[] stepped = item.split("/", -1);
      if (stepped.length > 2 || stepped[0].isEmpty()) throw new IllegalArgumentException("Invalid schedule step");
      int step = stepped.length == 2 ? number(stepped[1], Map.of()) : 1;
      if (step < 1 || step > maximum - minimum + 1) throw new IllegalArgumentException("Schedule step is out of range");
      int first, last;
      if (stepped[0].equals("*")) { first = minimum; last = maximum; }
      else {
        String[] range = stepped[0].split("-", -1);
        if (range.length > 2) throw new IllegalArgumentException("Invalid schedule range");
        first = number(range[0], names);
        last = range.length == 2 ? number(range[1], names) : stepped.length == 2 ? maximum : first;
      }
      if (first < minimum || last > maximum || first > last) throw new IllegalArgumentException("Schedule range is invalid");
      for (int value = first; value <= last; value += step) result.set(value);
    }
    if (result.isEmpty()) throw new IllegalArgumentException("Empty schedule field");
    return result;
  }
  private static int number(String value, Map<String, Integer> names) {
    Integer named = names.get(value); if (named != null) return named;
    if (!value.matches("[0-9]{1,4}")) throw new IllegalArgumentException("Invalid schedule value");
    return Integer.parseInt(value);
  }
  private static Map<String, Integer> monthNames() {
    var names = new HashMap<String, Integer>();
    for (Month month : Month.values()) { names.put(month.name(), month.getValue()); names.put(month.name().substring(0, 3), month.getValue()); }
    return Map.copyOf(names);
  }
  private static Map<String, Integer> dayNames() {
    var names = new HashMap<String, Integer>();
    for (DayOfWeek day : DayOfWeek.values()) { names.put(day.name(), day.getValue() % 7); names.put(day.name().substring(0, 3), day.getValue() % 7); }
    return Map.copyOf(names);
  }
}
