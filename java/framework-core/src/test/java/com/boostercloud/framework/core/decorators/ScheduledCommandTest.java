package com.boostercloud.framework.core.decorators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.BoosterTestSupport;
import com.boostercloud.framework.types.ScheduleInterface;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.ScheduledCommandMetadata;
import org.junit.jupiter.api.Test;

/** {@code @ScheduledCommand} (no TypeScript test). */
class ScheduledCommandTest extends BoosterTestSupport {

  @ScheduledCommand(minute = "0/1")
  public static class CheckCartCount {
    public static void handle(Register register) {}
  }

  @ScheduledCommand(minute = "0", hour = "1", day = "2", month = "3", weekDay = "MON", year = "2030")
  public static class FullSchedule {
    public static void handle(Register register) {}
  }

  @Test
  void registersTheScheduleWithAbsentFields() {
    Decorators.registerClass(CheckCartCount.class);

    assertThat(Booster.config().scheduledCommandHandlers.get("CheckCartCount")).isEqualTo(
        new ScheduledCommandMetadata(CheckCartCount.class, new ScheduleInterface("0/1", null, null, null, null, null)));
  }

  @Test
  void registersEveryField() {
    Decorators.registerClass(FullSchedule.class);

    assertThat(Booster.config().scheduledCommandHandlers.get("FullSchedule").scheduledOn())
        .isEqualTo(new ScheduleInterface("0", "1", "2", "3", "MON", "2030"));
  }

  @Test
  void failsWhenRegisteredTwiceWithTheCommandWording() {
    Decorators.registerClass(CheckCartCount.class);

    assertThatThrownBy(() -> Decorators.registerClass(CheckCartCount.class))
        .hasMessage("A command called CheckCartCount is already registered.\n"
            + "        If you think that this is an error, try performing a clean build.");
  }
}
