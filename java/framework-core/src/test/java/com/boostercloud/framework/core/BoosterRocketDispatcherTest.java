package com.boostercloud.framework.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import com.boostercloud.framework.types.provider.ProviderRocketLibrary;
import com.boostercloud.framework.types.rockets.RocketEnvelope;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@code BoosterRocketDispatcher} (behaviour from the source, spec 05 §3.8). */
class BoosterRocketDispatcherTest {

  private BoosterConfig config;
  private ProviderRocketLibrary rockets;

  @BeforeEach
  @SuppressWarnings("deprecation")
  void setUp() {
    config = new BoosterConfig("test");
    ProviderLibrary provider = mock(ProviderLibrary.class);
    rockets = mock(ProviderRocketLibrary.class);
    when(provider.rockets()).thenReturn(rockets);
    config.setProvider(provider);
  }

  @Test
  void callsTheRegisteredFunctionWithTheRawRequest() {
    Object raw = Map.of("body", "x");
    when(rockets.rawToEnvelopes(config, raw)).thenReturn(new RocketEnvelope("my-rocket"));
    config.registerRocketFunction("my-rocket", (configuration, request) -> Map.of("config", configuration, "request", request));

    assertThat(new BoosterRocketDispatcher(config).dispatch(raw)).isEqualTo(Map.of("config", config, "request", raw));
  }

  @Test
  void failsWithoutAnId() {
    when(rockets.rawToEnvelopes(config, "raw")).thenReturn(new RocketEnvelope(null));

    assertThatThrownBy(() -> new BoosterRocketDispatcher(config).dispatch("raw"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Attempt to execute a rocket function but the ID is missing.");
  }

  @Test
  void failsForUnknownIds() {
    when(rockets.rawToEnvelopes(config, "raw")).thenReturn(new RocketEnvelope("other"));

    assertThatThrownBy(() -> new BoosterRocketDispatcher(config).dispatch("raw"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Rocket function with ID \"other\" not found. Did you forget registering the function with"
            + " \"config.registerRocketFunction()\"?");
  }
}
