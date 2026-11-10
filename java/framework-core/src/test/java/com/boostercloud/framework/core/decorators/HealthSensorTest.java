package com.boostercloud.framework.core.decorators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.BoosterTestSupport;
import com.boostercloud.framework.core.sensor.health.healthindicators.BoosterHealthIndicator;
import com.boostercloud.framework.core.sensor.health.healthindicators.RocketsHealthIndicator;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.sensor.HealthIndicatorConfiguration;
import com.boostercloud.framework.types.sensor.HealthIndicatorInterface;
import com.boostercloud.framework.types.sensor.HealthIndicatorMetadata;
import com.boostercloud.framework.types.sensor.HealthIndicatorResult;
import com.boostercloud.framework.types.sensor.HealthStatus;
import org.junit.jupiter.api.Test;

/** {@code @HealthSensor} (no TypeScript test). */
class HealthSensorTest extends BoosterTestSupport {

  @HealthSensor(id = "myApplication/child", name = "My Application child", enabled = true, details = true)
  public static class ApplicationChildHealthIndicator implements HealthIndicatorInterface {
    @Override
    public HealthIndicatorResult health(BoosterConfig config, HealthIndicatorMetadata metadata) {
      return HealthIndicatorResult.of(HealthStatus.UP);
    }
  }

  @HealthSensor(id = "booster", name = "Mine", enabled = false, details = false, showChildren = false)
  public static class ReplacedRoot implements HealthIndicatorInterface {
    @Override
    public HealthIndicatorResult health(BoosterConfig config, HealthIndicatorMetadata metadata) {
      return HealthIndicatorResult.of(HealthStatus.UP);
    }
  }

  @HealthSensor(id = "bad", name = "Bad", enabled = true, details = true)
  public static class WithoutHealth {}

  @Test
  void seedsTheBuiltInIndicatorsWithTheCurrentSettingsAndAddsTheUserOne() {
    Booster.config().sensorConfiguration.health.booster.get("booster").enabled = true;

    Decorators.registerClass(ApplicationChildHealthIndicator.class);

    var indicators = Booster.config().userHealthIndicators;
    assertThat(indicators).containsOnlyKeys("booster", "booster/function", "booster/database", "booster/database/events",
        "booster/database/readmodels", "rockets", "myApplication/child");
    assertThat(indicators.get("booster").type()).isEqualTo(BoosterHealthIndicator.class);
    assertThat(indicators.get("booster").healthIndicatorConfiguration())
        .isEqualTo(new HealthIndicatorConfiguration("booster", "Booster", true, true, true));
    assertThat(indicators.get("rockets").type()).isEqualTo(RocketsHealthIndicator.class);
    assertThat(indicators.get("booster/database/readmodels").healthIndicatorConfiguration().name)
        .isEqualTo("Booster Database ReadModels");
    assertThat(indicators.get("myApplication/child")).isEqualTo(new HealthIndicatorMetadata(
        ApplicationChildHealthIndicator.class,
        new HealthIndicatorConfiguration("myApplication/child", "My Application child", true, true, true)));
  }

  @Test
  void aUserIndicatorReplacesABuiltInOneInPlace() {
    Decorators.registerClass(ApplicationChildHealthIndicator.class);
    Decorators.registerClass(ReplacedRoot.class);

    var indicators = Booster.config().userHealthIndicators;
    assertThat(indicators.keySet()).first().isEqualTo("booster");
    assertThat(indicators.get("booster")).isEqualTo(new HealthIndicatorMetadata(ReplacedRoot.class,
        new HealthIndicatorConfiguration("booster", "Mine", false, false, false)));
  }

  @Test
  void requiresAHealthMethod() {
    assertThatThrownBy(() -> Decorators.registerClass(WithoutHealth.class))
        .hasMessage("The health indicator WithoutHealth must declare a public instance method"
            + " \"health(BoosterConfig config, HealthIndicatorMetadata metadata)\"");
  }
}
