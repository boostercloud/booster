package com.boostercloud.framework.core.sensor.health.healthindicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import com.boostercloud.framework.types.provider.ProviderSensorLibrary;
import com.boostercloud.framework.types.sensor.HealthIndicatorConfiguration;
import com.boostercloud.framework.types.sensor.HealthIndicatorMetadata;
import com.boostercloud.framework.types.sensor.HealthIndicatorsResult;
import com.boostercloud.framework.types.sensor.HealthStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Port of {@code test/sensor/health/rockets-health-indicator.test.ts}. */
class RocketsHealthIndicatorTest {

  private BoosterConfig config;
  private ProviderSensorLibrary sensor;
  private final RocketsHealthIndicator indicator = new RocketsHealthIndicator();

  @BeforeEach
  @SuppressWarnings("deprecation")
  void setUp() {
    config = new BoosterConfig("test");
    ProviderLibrary provider = mock(ProviderLibrary.class);
    sensor = mock(ProviderSensorLibrary.class);
    when(provider.sensor()).thenReturn(sensor);
    config.setProvider(provider);
  }

  private static HealthIndicatorMetadata metadata(String id) {
    return new HealthIndicatorMetadata(RocketsHealthIndicator.class, new HealthIndicatorConfiguration(id, "Rockets", true, true, true));
  }

  private static Map<String, Boolean> rockets(Object... namesAndStatuses) {
    Map<String, Boolean> rockets = new LinkedHashMap<>();
    for (int i = 0; i < namesAndStatuses.length; i += 2) {
      rockets.put((String) namesAndStatuses[i], (Boolean) namesAndStatuses[i + 1]);
    }
    return rockets;
  }

  @Test
  void returnsUnknownWhenThereAreNoRockets() {
    when(sensor.areRocketFunctionsUp(config)).thenReturn(Map.of());

    assertThat(indicator.health(config, metadata("rockets")))
        .isEqualTo(new HealthIndicatorsResult(HealthStatus.UNKNOWN, Map.of("reason", "No Rockets found"), "Rockets", "rockets", null));
  }

  @Test
  void returnsUnknownWhenThereAreNoRocketsEvenForASpecificRocket() {
    when(sensor.areRocketFunctionsUp(config)).thenReturn(Map.of());

    assertThat(indicator.health(config, metadata("rockets/rocket1-func")).status()).isEqualTo(HealthStatus.UNKNOWN);
  }

  @Test
  void returnsUpWhenAllRocketsAreUp() {
    when(sensor.areRocketFunctionsUp(config)).thenReturn(rockets("rocket1-func", true, "rocket2-func", true));

    HealthIndicatorsResult result = indicator.health(config, metadata("rockets"));

    assertThat(result.status()).isEqualTo(HealthStatus.UP);
    assertThat(result.name()).isEqualTo("Rockets");
    assertThat(result.id()).isEqualTo("rockets");
    assertThat(result.components()).extracting(HealthIndicatorsResult::status).containsExactly(HealthStatus.UP, HealthStatus.UP);
  }

  @Test
  void returnsDownWhenAllRocketsAreDown() {
    when(sensor.areRocketFunctionsUp(config)).thenReturn(rockets("rocket1-func", false, "rocket2-func", false));

    HealthIndicatorsResult result = indicator.health(config, metadata("rockets"));

    assertThat(result.status()).isEqualTo(HealthStatus.DOWN);
    assertThat(result.components()).extracting(HealthIndicatorsResult::status).containsExactly(HealthStatus.DOWN, HealthStatus.DOWN);
  }

  @Test
  void returnsPartiallyUpWhenSomeRocketsAreDown() {
    when(sensor.areRocketFunctionsUp(config)).thenReturn(rockets("rocket1-func", true, "rocket2-func", false));

    HealthIndicatorsResult result = indicator.health(config, metadata("rockets"));

    assertThat(result.status()).isEqualTo(HealthStatus.PARTIALLY_UP);
    assertThat(result.components()).containsExactly(
        new HealthIndicatorsResult(HealthStatus.UP, null, "rocket1-func", "rockets/rocket1-func", null),
        new HealthIndicatorsResult(HealthStatus.DOWN, null, "rocket2-func", "rockets/rocket2-func", null));
  }

  @Test
  void returnsTheStatusOfASpecificRocketThatIsUp() {
    when(sensor.areRocketFunctionsUp(config)).thenReturn(rockets("rocket1-func", true, "rocket2-func", false));

    assertThat(indicator.health(config, metadata("rockets/rocket1-func")))
        .isEqualTo(new HealthIndicatorsResult(HealthStatus.UP, null, "rocket1-func", "rockets/rocket1-func", null));
  }

  @Test
  void returnsTheStatusOfASpecificRocketThatIsDown() {
    when(sensor.areRocketFunctionsUp(config)).thenReturn(rockets("rocket1-func", false, "rocket2-func", false));

    assertThat(indicator.health(config, metadata("rockets/rocket1-func")))
        .isEqualTo(new HealthIndicatorsResult(HealthStatus.DOWN, null, "rocket1-func", "rockets/rocket1-func", null));
  }

  @Test
  void failsWhenTheRequestedRocketDoesNotExist() {
    when(sensor.areRocketFunctionsUp(config)).thenReturn(rockets("rocket2-func", true));

    assertThatThrownBy(() -> indicator.health(config, metadata("rockets/rocket1-func")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Rocket \"rocket1-func\" not found");
  }

  @Test
  void usesOnlyTheSecondPathSegmentAndIgnoresAnEmptyOne() {
    when(sensor.areRocketFunctionsUp(config)).thenReturn(rockets("a", true, "b", false));

    assertThat(indicator.health(config, metadata("rockets/a/b")).name()).isEqualTo("a");
    assertThat(indicator.health(config, metadata("rockets/")).components()).hasSize(2);
  }

  @Test
  void aNullStatusIsDown() {
    Map<String, Boolean> results = new LinkedHashMap<>();
    results.put("a", null);
    when(sensor.areRocketFunctionsUp(config)).thenReturn(results);

    assertThat(indicator.health(config, metadata("rockets")).status()).isEqualTo(HealthStatus.DOWN);
    assertThat(indicator.health(config, metadata("rockets/a")).status()).isEqualTo(HealthStatus.DOWN);
    assertThat(List.of(indicator.health(config, metadata("rockets")).components().get(0).status()))
        .containsExactly(HealthStatus.DOWN);
  }
}
