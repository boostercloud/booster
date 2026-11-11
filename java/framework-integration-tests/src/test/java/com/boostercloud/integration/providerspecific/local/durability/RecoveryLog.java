package com.boostercloud.integration.providerspecific.local.durability;

import com.boostercloud.integration.harness.AppProcess;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the local provider's start-up recovery reported in the output of one start of an application
 * ({@code events-adapter#recoverUndispatchedEvents} and {@code LocalInfrastructure#start} log lines).
 *
 * @param interruptedEventIds the events whose processing had started when the previous process died: core had marked
 *     them as dispatched ({@code storeDispatched}), so they are not dispatched again (at-most-once)
 * @param redispatched how many stored but undispatched events were dispatched again
 * @param output the output of that start
 */
record RecoveryLog(List<String> interruptedEventIds, int redispatched, String output) {

  private static final Pattern INTERRUPTED = Pattern.compile(
      "The processing of \\d+ event\\(s\\) was interrupted by the previous shutdown[^\"]*\",\\[([^\\]]*)\\]");
  private static final Pattern EVENT_ID = Pattern.compile("\"([0-9A-Za-z]{16})\"");
  private static final Pattern REDISPATCHED = Pattern.compile(
      "Re-dispatched (\\d+) event\\(s\\) that were stored but not dispatched");

  /**
   * @param app an application launched by the harness
   * @param start the number of the start (1 for the first one)
   * @return what the recovery of that start reported
   */
  static RecoveryLog of(AppProcess app, int start) {
    String output = outputOfStart(app.output(), app.name(), start);
    List<String> interrupted = new ArrayList<>();
    Matcher lines = INTERRUPTED.matcher(output);
    while (lines.find()) {
      Matcher ids = EVENT_ID.matcher(lines.group(1));
      while (ids.find()) {
        interrupted.add(ids.group(1));
      }
    }
    int redispatched = 0;
    Matcher redispatchedLines = REDISPATCHED.matcher(output);
    while (redispatchedLines.find()) {
      redispatched += Integer.parseInt(redispatchedLines.group(1));
    }
    return new RecoveryLog(List.copyOf(interrupted), redispatched, output);
  }

  /**
   * @param output the whole output of an application process (every start)
   * @param name the name of the process
   * @param start the number of the start
   * @return the part written by that start
   */
  static String outputOfStart(String output, String name, int start) {
    String marker = "===== [e2e] start #" + start + " of " + name + " ";
    int from = output.indexOf(marker);
    if (from < 0) {
      throw new IllegalArgumentException("No start #" + start + " of " + name + " in its output");
    }
    int to = output.indexOf("===== [e2e] start #" + (start + 1) + " of " + name + " ", from);
    return to < 0 ? output.substring(from) : output.substring(from, to);
  }

  @Override
  public String toString() {
    return "RecoveryLog{interrupted=" + interruptedEventIds.size() + ", redispatched=" + redispatched + "}";
  }
}
