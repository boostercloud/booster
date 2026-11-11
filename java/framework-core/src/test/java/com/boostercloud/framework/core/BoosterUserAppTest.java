package com.boostercloud.framework.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.core.sensor.health.BoosterHealthService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

/** Port of {@code test/index.test.ts}: every entry point calls its delegate once with the raw request. */
class BoosterUserAppTest extends BoosterTestSupport {

  private final Object raw = Map.of("raw", "request");
  private final BoosterUserApp userApp = new BoosterUserApp();

  @Test
  void boosterEventDispatcher() {
    try (MockedStatic<BoosterEventDispatcher> dispatcher = mockStatic(BoosterEventDispatcher.class)) {
      userApp.boosterEventDispatcher(raw);

      dispatcher.verify(() -> BoosterEventDispatcher.dispatch(raw, Booster.config()), times(1));
    }
  }

  @Test
  void boosterServeGraphQL() {
    try (MockedConstruction<BoosterGraphQLDispatcher> dispatchers = mockConstruction(BoosterGraphQLDispatcher.class,
        (dispatcher, context) -> {
          assertThat((java.util.List<Object>) java.util.List.copyOf(context.arguments())).containsExactly((Object) Booster.config());
          when(dispatcher.dispatch(raw)).thenReturn("response");
        })) {
      assertThat(userApp.boosterServeGraphQL(raw)).isEqualTo("response");
      verify(dispatchers.constructed().getFirst(), times(1)).dispatch(raw);
    }
  }

  @Test
  void boosterTriggerScheduledCommandsAndItsDeprecatedAlias() {
    try (MockedConstruction<BoosterScheduledCommandDispatcher> dispatchers =
        mockConstruction(BoosterScheduledCommandDispatcher.class)) {
      userApp.boosterTriggerScheduledCommands(raw);
      @SuppressWarnings("deprecation")
      Runnable alias = () -> userApp.boosterTriggerScheduledCommand(raw);
      alias.run();

      assertThat(dispatchers.constructed()).hasSize(2);
      verify(dispatchers.constructed().get(0), times(1)).dispatch(raw);
      verify(dispatchers.constructed().get(1), times(1)).dispatch(raw);
    }
  }

  @Test
  void boosterNotifySubscribers() {
    try (MockedConstruction<BoosterSubscribersNotifier> notifiers = mockConstruction(BoosterSubscribersNotifier.class)) {
      userApp.boosterNotifySubscribers(raw);

      verify(notifiers.constructed().getFirst(), times(1)).dispatch(raw);
    }
  }

  @Test
  void boosterRocketDispatcher() {
    try (MockedConstruction<BoosterRocketDispatcher> dispatchers = mockConstruction(BoosterRocketDispatcher.class,
        (dispatcher, context) -> when(dispatcher.dispatch(raw)).thenReturn("rocket"))) {
      assertThat(userApp.boosterRocketDispatcher(raw)).isEqualTo("rocket");
      verify(dispatchers.constructed().getFirst(), times(1)).dispatch(raw);
    }
  }

  @Test
  void boosterConsumeAndProduceEventStream() {
    try (MockedStatic<BoosterEventStreamConsumer> consumer = mockStatic(BoosterEventStreamConsumer.class);
        MockedStatic<BoosterEventStreamProducer> producer = mockStatic(BoosterEventStreamProducer.class)) {
      assertThat(userApp.boosterConsumeEventStream(raw)).isNull();
      assertThat(userApp.boosterProduceEventStream(raw)).isNull();

      consumer.verify(() -> BoosterEventStreamConsumer.consume(raw, Booster.config()), times(1));
      producer.verify(() -> BoosterEventStreamProducer.produce(raw, Booster.config()), times(1));
    }
  }

  @Test
  void boosterHealth() {
    try (MockedConstruction<BoosterHealthService> services = mockConstruction(BoosterHealthService.class,
        (service, context) -> when(service.boosterHealth(raw)).thenReturn("healthy"))) {
      assertThat(userApp.boosterHealth(raw)).isEqualTo("healthy");
      verify(services.constructed().getFirst(), times(1)).boosterHealth(raw);
    }
  }

  @Test
  void exposesTheConfiguration() {
    userApp.configureCurrentEnv(config -> config.appName = "via-user-app");
    Booster.configure("local", config -> {});

    assertThat(userApp.config()).isSameAs(Booster.config());
    assertThat(userApp.config().appName).isEqualTo("via-user-app");
    assertThat(userApp.configuredEnvironments()).containsExactly("local");
  }
}
