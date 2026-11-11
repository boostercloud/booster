package com.boostercloud.framework.core.decorators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.BoosterTestSupport;
import com.boostercloud.framework.types.concepts.EventMetadata;
import org.junit.jupiter.api.Test;

/** Port of {@code test/decorators/event.test.ts}. */
class EventTest extends BoosterTestSupport {

  @Event
  public static class AnEvent {
    public String foo;
  }

  @Test
  void addsTheEventClassAsAnEvent() {
    Decorators.registerClass(AnEvent.class);

    assertThat(Booster.config().events.get("AnEvent")).isEqualTo(new EventMetadata(AnEvent.class));
  }

  @Test
  void failsWhenRegisteredTwice() {
    Decorators.registerClass(AnEvent.class);

    assertThatThrownBy(() -> Decorators.registerEvent(AnEvent.class))
        .hasMessage("A event called AnEvent is already registered.\n"
            + "        If you think that this is an error, try performing a clean build.");
  }
}
