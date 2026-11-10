package com.boostercloud.framework.provider.azure.infrastructure.functions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.provider.azure.infrastructure.helper.FunctionZip;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionDefinition;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.ScheduleInterface;
import com.boostercloud.framework.types.concepts.ScheduledCommandMetadata;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Port of {@code test/infrastructure/functions/schedules-functions.test.ts}. */
class SchedulesFunctionsTest {

  static final class ScheduledCommand {}

  private static BoosterConfig buildConfig() {
    return new BoosterConfig("test");
  }

  private static void schedule(BoosterConfig config, String name, ScheduleInterface scheduledOn) {
    config.scheduledCommandHandlers.put(name, new ScheduledCommandMetadata(ScheduledCommand.class, scheduledOn));
  }

  private static ScheduleInterface day(String day) {
    return new ScheduleInterface(null, null, day, null, null, null);
  }

  private static void expectDefinition(FunctionDefinition definition, String name, String nCronTabExpression) {
    assertThat(definition.name()).isEqualTo("scheduleFunction-" + name);
    assertThat(definition.config().bindings()).hasSize(1);
    assertThat(definition.config().bindings().get(0).type()).isEqualTo("timerTrigger");
    assertThat(definition.config().bindings().get(0).name()).isEqualTo(name);
    assertThat(definition.config().bindings().get(0).direction()).isEqualTo("in");
    assertThat(definition.config().bindings().get(0).get("schedule")).isEqualTo(nCronTabExpression);
    assertThat(definition.config().scriptFile()).isEqualTo("../dist/index.js");
    assertThat(definition.config().entryPoint()).isEqualTo("boosterTriggerScheduledCommand");
  }

  @Nested
  class WithoutScheduledCommandHandlers {
    @Test
    void is_an_undefined_object() {
      assertThat(new ScheduledFunctions(buildConfig()).getFunctionDefinitions()).isNull();
    }
  }

  @Nested
  class WithOneScheduledCommandHandler {
    @Test
    void is_one_definition_with_the_proper_fields() {
      BoosterConfig config = buildConfig();
      schedule(config, "test", day("1"));
      List<FunctionDefinition> definitions = new ScheduledFunctions(config).getFunctionDefinitions();
      assertThat(definitions).hasSize(1);
      expectDefinition(definitions.get(0), "test", "0 * * 1 * *");
      assertThat(FunctionZip.functionJson(definitions.get(0).config())).isEqualTo("""
          {
            "bindings": [
              {
                "type": "timerTrigger",
                "name": "test",
                "direction": "in",
                "schedule": "0 * * 1 * *"
              }
            ],
            "scriptFile": "../dist/index.js",
            "entryPoint": "boosterTriggerScheduledCommand"
          }""");
    }
  }

  @Nested
  class WithTwoScheduledCommandHandlers {
    @Test
    void is_two_definitions_with_the_proper_fields() {
      BoosterConfig config = buildConfig();
      schedule(config, "test-1", day("1"));
      schedule(config, "test-2", day("1"));
      List<FunctionDefinition> definitions = new ScheduledFunctions(config).getFunctionDefinitions();
      assertThat(definitions).hasSize(2);
      expectDefinition(definitions.get(0), "test-1", "0 * * 1 * *");
      expectDefinition(definitions.get(1), "test-2", "0 * * 1 * *");
    }
  }

  @Nested
  class WithAllScheduledFields {
    @Test
    void create_the_expected_nCronTab() {
      BoosterConfig config = buildConfig();
      schedule(config, "test", new ScheduleInterface("minute", "hour", "day", "month", "weekDay", "year"));
      expectDefinition(new ScheduledFunctions(config).getFunctionDefinitions().get(0), "test",
          "0 minute hour day month weekDay");
    }
  }

  @Nested
  class WithoutScheduledFields {
    @Test
    void skip_the_function() {
      BoosterConfig config = buildConfig();
      schedule(config, "test", new ScheduleInterface(null, null, null, null, null, null));
      assertThat(new ScheduledFunctions(config).getFunctionDefinitions()).isEmpty();
    }
  }

  @Test
  void keeps_empty_strings_verbatim() {
    assertThat(ScheduledFunctions.createCronExpression(new ScheduleInterface("", null, null, null, null, null)))
        .isEqualTo("0  * * * *");
  }

  @Test
  void fails_like_object_keys_of_undefined_for_a_missing_schedule() {
    BoosterConfig config = buildConfig();
    schedule(config, "test", null);
    assertThatThrownBy(() -> new ScheduledFunctions(config).getFunctionDefinitions())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot convert undefined or null to object");
  }
}
