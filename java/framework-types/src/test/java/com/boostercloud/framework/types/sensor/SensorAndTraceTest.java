package com.boostercloud.framework.types.sensor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.instrumentation.TraceActionTypes;
import com.boostercloud.framework.types.instrumentation.TraceConfiguration;
import com.boostercloud.framework.types.instrumentation.TraceTypes;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Health sensor defaults and trace configuration semantics. */
class SensorAndTraceTest {

  @Test
  void hasTheBuiltInIndicatorIds() {
    assertThat(BoosterHealthIndicatorsIds.values()).extracting(BoosterHealthIndicatorsIds::id).containsExactly(
        "booster", "booster/function", "booster/database", "booster/database/events", "booster/database/readmodels",
        "rockets");
    assertThat(BoosterHealthIndicatorsIds.fromId("rockets")).isEqualTo(BoosterHealthIndicatorsIds.ROCKETS);
    assertThatThrownBy(() -> BoosterHealthIndicatorsIds.fromId("x")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void disablesEveryBuiltInIndicatorByDefaultWithIndependentCopies() {
    Map<String, HealthIndicatorConfigurationBase> defaults = SensorDefaults.defaultSensorHealthBoosterConfigurations();
    assertThat(defaults.values()).allSatisfy(indicator ->
        assertThat(indicator).isEqualTo(new HealthIndicatorConfigurationBase(false, true, true)));
    defaults.get("booster").enabled = true;
    assertThat(defaults.get("booster/function").enabled).isFalse();
    assertThat(SensorDefaults.defaultHealthConfigurationBooster().enabled).isFalse();
  }

  @Test
  void serializesHealthResults() {
    HealthIndicatorsResult result = new HealthIndicatorsResult(HealthStatus.UP, null, "Booster", "booster",
        List.of(new HealthIndicatorsResult(HealthStatus.UNKNOWN, null, "Rockets", "rockets", null)));
    assertThat(BoosterJson.toJson(result)).isEqualTo(
        "{\"status\":\"UP\",\"name\":\"Booster\",\"id\":\"booster\",\"components\":"
            + "[{\"status\":\"UNKNOWN\",\"name\":\"Rockets\",\"id\":\"rockets\"}]}");
    HealthIndicatorConfiguration user = new HealthIndicatorConfiguration("application/child", "My", true, true, null);
    assertThat(user.copy()).isEqualTo(user).isNotSameAs(user);
  }

  @Test
  void tracesNothingByDefault() {
    TraceConfiguration configuration = new TraceConfiguration();
    assertThat(configuration.isTraceEnabledFor(TraceActionTypes.COMMAND_HANDLER.name())).isFalse();
  }

  @Test
  void tracesEverythingWhenEnabled() {
    TraceConfiguration configuration = new TraceConfiguration().enableAll();
    assertThat(configuration.isTraceEnabledFor("CHANGE_CART_ITEM_HANDLER")).isTrue();
  }

  @Test
  void tracesOnlyTheListedActionsAndNothingForAnEmptyList() {
    TraceConfiguration configuration = new TraceConfiguration().enableFor("COMMAND_HANDLER");
    assertThat(configuration.isTraceEnabledFor("COMMAND_HANDLER")).isTrue();
    assertThat(configuration.isTraceEnabledFor("MIGRATION_RUN")).isFalse();
    configuration.enableTraceNotification = List.of();
    assertThat(configuration.isTraceEnabledFor("COMMAND_HANDLER")).isFalse();
    configuration.enableTraceNotification = "nonsense";
    assertThatThrownBy(() -> configuration.isTraceEnabledFor("X")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void keepsTheTraceEnums() {
    assertThat(TraceTypes.START.ordinal()).isZero();
    assertThat(TraceTypes.END.ordinal()).isEqualTo(1);
    assertThat(TraceActionTypes.values()).hasSize(23);
    assertThat(TraceActionTypes.SCHEMA_MIGRATOR_MIGRATE.value()).isEqualTo("SCHEMA_MIGRATOR_MIGRATE");
  }
}
