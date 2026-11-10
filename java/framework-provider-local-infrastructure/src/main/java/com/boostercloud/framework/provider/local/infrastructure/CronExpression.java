package com.boostercloud.framework.provider.local.infrastructure;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.BitSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Unix cron expression with the semantics of {@code cron-parser} 4, the parser behind {@code node-schedule}, which
 * the TypeScript local provider uses for scheduled commands.
 *
 * <p>Fields: {@code [second] minute hour day-of-month month day-of-week}; with five fields (what the scheduler
 * builds) the second is {@code 0}, and missing leading fields take {@code cron-parser}'s defaults. Each field accepts
 * {@code *}, numbers, ranges ({@code 1-5}), steps ({@code *}{@code /15}, {@code 0/5} meaning {@code 0-59/5},
 * {@code 10-40/10}) and lists ({@code 1,15,30}); months and days of week accept three-letter names ({@code JAN},
 * {@code MON}, any case); day of week {@code 0} and {@code 7} are Sunday; {@code ?} is {@code *} in the day fields;
 * {@code L} is the last day of the month in day-of-month, {@code 5L} the last Friday and {@code 5#2} the second Friday
 * in day-of-week. When both day-of-month and day-of-week are restricted, a day matching either runs (Unix rule).
 *
 * <p>Times are evaluated in the zone of the {@link ZonedDateTime} given to {@link #next(ZonedDateTime)}.
 */
public final class CronExpression {

  private static final String[] FIELDS = {"second", "minute", "hour", "dayOfMonth", "month", "dayOfWeek"};
  private static final String[] DEFAULTS = {"0", "*", "*", "*", "*", "*"};
  private static final int[][] CONSTRAINTS = {{0, 59}, {0, 59}, {0, 23}, {1, 31}, {1, 12}, {0, 7}};
  private static final Map<String, Integer> MONTHS = Map.ofEntries(Map.entry("jan", 1), Map.entry("feb", 2),
      Map.entry("mar", 3), Map.entry("apr", 4), Map.entry("may", 5), Map.entry("jun", 6), Map.entry("jul", 7),
      Map.entry("aug", 8), Map.entry("sep", 9), Map.entry("oct", 10), Map.entry("nov", 11), Map.entry("dec", 12));
  private static final Map<String, Integer> DAYS = Map.of("sun", 0, "mon", 1, "tue", 2, "wed", 3, "thu", 4,
      "fri", 5, "sat", 6);
  private static final Pattern ALIAS = Pattern.compile("[a-z]{3}", Pattern.CASE_INSENSITIVE);
  private static final Pattern SIMPLE_CHARACTERS = Pattern.compile("^[\\d/*,-]+$");
  private static final Pattern DAY_OF_MONTH_CHARACTERS = Pattern.compile("^[\\d/*,?L-]+$");
  private static final Pattern DAY_OF_WEEK_CHARACTERS = Pattern.compile("^[\\d/*,?L#-]+$");
  private static final int SEARCH_YEARS = 8;

  private final String expression;
  private final BitSet seconds;
  private final BitSet minutes;
  private final BitSet hours;
  private final BitSet daysOfMonth;
  private final BitSet months;
  private final BitSet daysOfWeek;
  private final boolean lastDayOfMonth;
  private final BitSet lastWeekdaysOfMonth;
  private final int nthWeekday;
  private final int nthOccurrence;
  private final boolean dayOfMonthWildcard;
  private final boolean dayOfWeekWildcard;

  private CronExpression(String expression, BitSet[] fields, boolean lastDayOfMonth, BitSet lastWeekdaysOfMonth,
      int nthWeekday, int nthOccurrence) {
    this.expression = expression;
    this.seconds = fields[0];
    this.minutes = fields[1];
    this.hours = fields[2];
    this.daysOfMonth = fields[3];
    this.months = fields[4];
    this.daysOfWeek = fields[5];
    this.lastDayOfMonth = lastDayOfMonth;
    this.lastWeekdaysOfMonth = lastWeekdaysOfMonth;
    this.nthWeekday = nthWeekday;
    this.nthOccurrence = nthOccurrence;
    this.dayOfMonthWildcard = !lastDayOfMonth && daysOfMonth.cardinality() == 31;
    this.dayOfWeekWildcard = lastWeekdaysOfMonth.isEmpty() && nthWeekday < 0 && daysOfWeek.cardinality() == 7;
  }

  /**
   * @param expression a cron expression
   * @return the parsed expression
   * @throws IllegalArgumentException with {@code cron-parser}'s message when it is invalid (TypeScript's
   *     {@code scheduleJob} then silently schedules nothing)
   */
  public static CronExpression parse(String expression) {
    Objects.requireNonNull(expression, "expression");
    String trimmed = expression.trim();
    String[] atoms = trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+");
    if (atoms.length > FIELDS.length) {
      throw new IllegalArgumentException("Invalid cron expression");
    }
    int start = FIELDS.length - atoms.length;
    BitSet[] fields = new BitSet[FIELDS.length];
    boolean lastDay = false;
    BitSet lastWeekdays = new BitSet(7);
    int nthWeekday = -1;
    int nthOccurrence = -1;
    for (int i = 0; i < FIELDS.length; i++) {
      String value = i < start ? DEFAULTS[i] : atoms[i - start];
      String field = FIELDS[i];
      int[] constraint = CONSTRAINTS[i];
      if (field.equals("month") || field.equals("dayOfWeek")) {
        value = replaceAliases(value, field.equals("month") ? MONTHS : DAYS);
      }
      Pattern valid = field.equals("dayOfMonth") ? DAY_OF_MONTH_CHARACTERS
          : field.equals("dayOfWeek") ? DAY_OF_WEEK_CHARACTERS : SIMPLE_CHARACTERS;
      if (!valid.matcher(value).matches()) {
        throw new IllegalArgumentException("Invalid characters, got value: " + value);
      }
      String full = constraint[0] + "-" + constraint[1];
      if (value.contains("*")) {
        value = value.replace("*", full);
      } else if (value.contains("?")) {
        value = value.replace("?", full);
      }
      BitSet set = new BitSet(constraint[1] + 1);
      if (field.equals("dayOfWeek") && value.contains("#")) {
        if (value.contains(",")) {
          throw new IllegalArgumentException(
              "Constraint error, invalid dayOfWeek `#` and `,` special characters are incompatible");
        }
        String[] parts = value.split("#", -1);
        if (parts.length != 2) {
          throw new IllegalArgumentException("Constraint error, invalid dayOfWeek occurrence number (#)");
        }
        int weekday = parseNumber(parts[0], constraint, field);
        int occurrence = parseNumber(parts[1], new int[] {1, 5}, field);
        nthWeekday = weekday % 7;
        nthOccurrence = occurrence;
        set.set(nthWeekday);
      } else {
        for (String atom : value.split(",", -1)) {
          if (field.equals("dayOfMonth") && atom.equals("L")) {
            lastDay = true;
          } else if (field.equals("dayOfWeek") && atom.endsWith("L")) {
            lastWeekdays.set(parseNumber(atom.substring(0, atom.length() - 1), constraint, field) % 7);
          } else {
            parseRepeat(atom, constraint, set);
          }
        }
      }
      if (field.equals("dayOfWeek") && set.get(7)) {
        set.clear(7);
        set.set(0);
      }
      fields[i] = set;
    }
    return new CronExpression(expression, fields, lastDay, lastWeekdays, nthWeekday, nthOccurrence);
  }

  private static String replaceAliases(String value, Map<String, Integer> aliases) {
    Matcher matcher = ALIAS.matcher(value);
    StringBuilder result = new StringBuilder();
    while (matcher.find()) {
      String alias = matcher.group().toLowerCase(Locale.ROOT);
      Integer number = aliases.get(alias);
      if (number == null) {
        throw new IllegalArgumentException("Validation error, cannot resolve alias \"" + alias + "\"");
      }
      matcher.appendReplacement(result, number.toString());
    }
    matcher.appendTail(result);
    return result.toString();
  }

  private static void parseRepeat(String value, int[] constraint, BitSet set) {
    String[] atoms = value.split("/", -1);
    if (atoms.length > 2) {
      throw new IllegalArgumentException("Invalid repeat: " + value);
    }
    if (atoms.length == 2) {
      String range = atoms[0].matches("\\d+") ? atoms[0] + "-" + constraint[1] : atoms[0];
      parseRange(range, atoms[1], constraint, set);
    } else {
      parseRange(value, "1", constraint, set);
    }
  }

  private static void parseRange(String value, String repeat, int[] constraint, BitSet set) {
    int step;
    try {
      step = Integer.parseInt(repeat);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Constraint error, cannot repeat at every " + repeat + " time.");
    }
    if (step <= 0) {
      throw new IllegalArgumentException("Constraint error, cannot repeat at every " + repeat + " time.");
    }
    String[] atoms = value.split("-", -1);
    if (atoms.length == 2) {
      int min = parseInt(atoms[0]);
      int max = parseInt(atoms[1]);
      if (min < constraint[0] || max > constraint[1]) {
        throw new IllegalArgumentException("Constraint error, got range " + atoms[0] + "-" + atoms[1]
            + " expected range " + constraint[0] + "-" + constraint[1]);
      }
      if (min > max) {
        throw new IllegalArgumentException("Invalid range: " + value);
      }
      for (int i = min; i <= max; i += step) {
        set.set(i);
      }
    } else if (atoms.length == 1) {
      int number = parseInt(value);
      if (number < constraint[0] || number > constraint[1]) {
        throw new IllegalArgumentException("Constraint error, got value " + value + " expected range "
            + constraint[0] + "-" + constraint[1]);
      }
      set.set(number);
    } else {
      throw new IllegalArgumentException("Invalid range: " + value);
    }
  }

  private static int parseNumber(String value, int[] constraint, String field) {
    int number = parseInt(value);
    if (number < constraint[0] || number > constraint[1]) {
      throw new IllegalArgumentException("Constraint error, got value " + value + " expected range "
          + constraint[0] + "-" + constraint[1] + " for " + field);
    }
    return number;
  }

  private static int parseInt(String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return Integer.MIN_VALUE;
    }
  }

  /**
   * @return the expression as given
   */
  public String expression() {
    return expression;
  }

  /**
   * The first time strictly after {@code after} that matches, in the zone of {@code after}. Wall-clock times skipped
   * by a daylight-saving gap run at the first instant after the gap.
   *
   * @param after the reference time
   * @return the next run, or empty when the expression never matches (for example {@code 0 0 30 2 *})
   */
  public Optional<ZonedDateTime> next(ZonedDateTime after) {
    LocalDateTime limit = after.toLocalDateTime().plusYears(SEARCH_YEARS);
    LocalDateTime t = after.toLocalDateTime().truncatedTo(ChronoUnit.SECONDS).plusSeconds(1);
    while (!t.isAfter(limit)) {
      if (!months.get(t.getMonthValue())) {
        t = t.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS).plusMonths(1);
        continue;
      }
      if (!dayMatches(t)) {
        t = t.truncatedTo(ChronoUnit.DAYS).plusDays(1);
        continue;
      }
      if (!hours.get(t.getHour())) {
        t = t.truncatedTo(ChronoUnit.HOURS).plusHours(1);
        continue;
      }
      if (!minutes.get(t.getMinute())) {
        t = t.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1);
        continue;
      }
      if (!seconds.get(t.getSecond())) {
        t = t.plusSeconds(1);
        continue;
      }
      ZonedDateTime candidate = ZonedDateTime.ofLocal(t, after.getZone(), after.getOffset());
      if (candidate.isAfter(after)) {
        return Optional.of(candidate);
      }
      t = t.plusSeconds(1);
    }
    return Optional.empty();
  }

  private boolean dayMatches(LocalDateTime t) {
    int day = t.getDayOfMonth();
    int length = YearMonth.from(t).lengthOfMonth();
    boolean dayOfMonthMatch = daysOfMonth.get(day) || (lastDayOfMonth && day == length);
    int weekday = t.getDayOfWeek() == DayOfWeek.SUNDAY ? 0 : t.getDayOfWeek().getValue();
    boolean dayOfWeekMatch;
    if (nthWeekday >= 0) {
      dayOfWeekMatch = weekday == nthWeekday && (day - 1) / 7 + 1 == nthOccurrence;
    } else {
      dayOfWeekMatch = daysOfWeek.get(weekday) || (lastWeekdaysOfMonth.get(weekday) && day + 7 > length);
    }
    if (dayOfMonthWildcard && dayOfWeekWildcard) {
      return true;
    }
    if (dayOfMonthWildcard) {
      return dayOfWeekMatch;
    }
    if (dayOfWeekWildcard) {
      return dayOfMonthMatch;
    }
    return dayOfMonthMatch || dayOfWeekMatch;
  }

  @Override
  public String toString() {
    return expression;
  }
}
