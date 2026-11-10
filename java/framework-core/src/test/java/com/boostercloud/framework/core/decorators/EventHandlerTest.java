package com.boostercloud.framework.core.decorators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.BoosterTestSupport;
import com.boostercloud.framework.types.concepts.Register;
import org.junit.jupiter.api.Test;

/** Port of {@code test/decorators/event-handler.test.ts}, plus {@code @GlobalEventHandler}. */
class EventHandlerTest extends BoosterTestSupport {

  @Event
  public record SomeEvent(String id) {}

  @Event
  public record OtherEvent(String id) {}

  @EventHandler(SomeEvent.class)
  public static class SomeEventHandler {
    public static void handle(SomeEvent event, Register register) {}
  }

  @EventHandler(SomeEvent.class)
  @EventHandler(OtherEvent.class)
  public static class SeveralEventsHandler {
    public static void handle(Object event, Register register) {}
  }

  @GlobalEventHandler
  public static class GlobalHandler {
    public static void handle(Object event, Register register) {}
  }

  @EventHandler(SomeEvent.class)
  public static class WithoutHandle {}

  @Test
  void addsTheHandlerToTheEventHandlers() {
    Decorators.registerClass(SomeEvent.class);
    Decorators.registerClass(SomeEventHandler.class);

    assertThat(Booster.config().eventHandlers.get("SomeEvent")).containsExactly(SomeEventHandler.class);
  }

  @Test
  void keepsTheOrderAndIgnoresTheSameClassTwice() {
    Decorators.registerClass(SomeEventHandler.class);
    Decorators.registerClass(SeveralEventsHandler.class);
    Decorators.registerEventHandler(SomeEvent.class, SomeEventHandler.class);

    assertThat(Booster.config().eventHandlers.get("SomeEvent")).containsExactly(SomeEventHandler.class, SeveralEventsHandler.class);
    assertThat(Booster.config().eventHandlers.get("OtherEvent")).containsExactly(SeveralEventsHandler.class);
  }

  @Test
  void registersGlobalHandlersUnderTheGlobalKey() {
    Decorators.registerClass(GlobalHandler.class);

    assertThat(Booster.config().eventHandlers.get(GlobalEventHandler.BOOSTER_GLOBAL_EVENT_HANDLERS))
        .containsExactly(GlobalHandler.class);
    assertThat(GlobalEventHandler.BOOSTER_GLOBAL_EVENT_HANDLERS).isEqualTo("BOOSTER_GLOBAL_EVENT_HANDLERS");
  }

  @Test
  void failsWithoutAStaticHandle() {
    assertThatThrownBy(() -> Decorators.registerClass(WithoutHandle.class))
        .hasMessage("The event handler WithoutHandle must declare a static method called \"handle\"");
  }
}
