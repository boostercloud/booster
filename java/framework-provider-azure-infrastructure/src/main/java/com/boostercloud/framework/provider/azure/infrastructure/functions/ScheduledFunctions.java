package com.boostercloud.framework.provider.azure.infrastructure.functions;

import com.boostercloud.framework.provider.azure.infrastructure.types.Binding;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionConfig;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionDefinition;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.ScheduleInterface;
import com.boostercloud.framework.types.concepts.ScheduledCommandMetadata;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Port of {@code functions/scheduled-functions.ts}: the legacy timer definitions of the scheduled commands (pinned by
 * tests) and the NCRONTAB conversion shared with the Java worker definitions.
 */
public final class ScheduledFunctions {

  private final BoosterConfig config;

  /**
   * @param config the configuration
   */
  public ScheduledFunctions(BoosterConfig config) {
    this.config = config;
  }

  /**
   * One {@code scheduleFunction-<Command>} per scheduled command with a non-empty schedule: a {@code timerTrigger}
   * named after the command, {@code ../dist/index.js} / {@code boosterTriggerScheduledCommand}.
   *
   * @return the definitions, {@code null} when there are no scheduled commands at all (TypeScript {@code undefined}),
   *     an empty list when every schedule is empty
   * @throws IllegalArgumentException {@code Cannot convert undefined or null to object} when a command has no schedule
   *     ({@code Object.keys(undefined)} in TypeScript)
   */
  public List<FunctionDefinition> getFunctionDefinitions() {
    if (config.scheduledCommandHandlers.isEmpty()) {
      return null;
    }
    List<FunctionDefinition> definitions = new ArrayList<>();
    for (Map.Entry<String, ScheduledCommandMetadata> entry : config.scheduledCommandHandlers.entrySet()) {
      ScheduleInterface scheduledOn = entry.getValue() == null ? null : entry.getValue().scheduledOn();
      if (scheduledOn == null) {
        throw new IllegalArgumentException("Cannot convert undefined or null to object");
      }
      if (scheduledOn.isEmpty()) {
        continue;
      }
      String name = entry.getKey();
      definitions.add(new FunctionDefinition("scheduleFunction-" + name, new FunctionConfig(
          List.of(Binding.of(LegacyFunctions.ordered(
              "type", "timerTrigger", "name", name, "direction", "in", "schedule", createCronExpression(scheduledOn)))),
          config.functionRelativePath,
          LegacyFunctions.entryPoint(config.scheduledTaskHandler))));
    }
    return definitions;
  }

  /**
   * {@code createCronExpression(schedule)}: the 6-field NCRONTAB expression of Azure Functions
   * ({@code {second} {minute} {hour} {day} {month} {day-of-week}}) with the seconds fixed to {@code 0} and absent fields
   * as {@code *}; {@code year} is ignored and values are copied verbatim (an empty string stays empty).
   *
   * @param schedule the schedule
   * @return the expression, e.g. {@code 0 0/5 * * * *}
   */
  public static String createCronExpression(ScheduleInterface schedule) {
    return "0 " + orStar(schedule.minute()) + " " + orStar(schedule.hour()) + " " + orStar(schedule.day()) + " "
        + orStar(schedule.month()) + " " + orStar(schedule.weekDay());
  }

  private static String orStar(String value) {
    return value == null ? "*" : value;
  }
}
