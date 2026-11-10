package com.boostercloud.framework.core.sensor.health.healthindicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.errors.NotFoundError;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import com.boostercloud.framework.types.provider.ProviderSensorLibrary;
import com.boostercloud.framework.types.sensor.HealthIndicatorConfiguration;
import com.boostercloud.framework.types.sensor.HealthIndicatorMetadata;
import com.boostercloud.framework.types.sensor.HealthIndicatorResult;
import com.boostercloud.framework.types.sensor.HealthStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/** The five built-in {@code booster*} indicators (no TypeScript test of their own; spec 09 §3.9). */
class BoosterHealthIndicatorsTest {

  private BoosterConfig config;
  private ProviderSensorLibrary sensor;

  @BeforeEach
  @SuppressWarnings("deprecation")
  void setUp() {
    config = new BoosterConfig("test");
    config.logger = mock(Logger.class);
    ProviderLibrary provider = mock(ProviderLibrary.class);
    sensor = mock(ProviderSensorLibrary.class);
    when(provider.sensor()).thenReturn(sensor);
    config.setProvider(provider);
    when(sensor.isGraphQLFunctionUp(config)).thenReturn(true);
    when(sensor.isDatabaseEventUp(config)).thenReturn(true);
    when(sensor.areDatabaseReadModelsUp(config)).thenReturn(true);
  }

  private static HealthIndicatorMetadata metadata(Class<?> type, boolean details) {
    return new HealthIndicatorMetadata(type, new HealthIndicatorConfiguration("id", "name", true, details, true));
  }

  @Test
  void boosterIsUpWhenTheFunctionAndBothDatabasesAreUp() {
    HealthIndicatorResult result = new BoosterHealthIndicator().health(config, metadata(BoosterHealthIndicator.class, true));

    assertThat(result.status()).isEqualTo(HealthStatus.UP);
    assertThat(result.details()).containsOnlyKeys("boosterVersion");
    assertThat(result.details().get("boosterVersion")).isEqualTo("");
  }

  @Test
  void boosterAsksTheThreeSensorsInOrderAndIsDownWhenOneIsDown() {
    when(sensor.isGraphQLFunctionUp(config)).thenReturn(false);

    HealthIndicatorResult result = new BoosterHealthIndicator().health(config, metadata(BoosterHealthIndicator.class, false));

    assertThat(result).isEqualTo(new HealthIndicatorResult(HealthStatus.DOWN, null));
    InOrder order = inOrder(sensor);
    order.verify(sensor).isGraphQLFunctionUp(config);
    order.verify(sensor).isDatabaseEventUp(config);
    order.verify(sensor).areDatabaseReadModelsUp(config);
  }

  @Test
  void boosterRequiresTheReadModelsDatabaseToo() {
    when(sensor.areDatabaseReadModelsUp(config)).thenReturn(false);

    assertThat(new BoosterHealthIndicator().health(config, metadata(BoosterHealthIndicator.class, false)).status())
        .isEqualTo(HealthStatus.DOWN);
  }

  @Test
  void anExceptionIsDownWithTheErrorAsDetails() {
    when(sensor.isGraphQLFunctionUp(config)).thenThrow(new NotFoundError("no function", "NO_FUNCTION", Map.of("a", 1)));

    HealthIndicatorResult result = new BoosterHealthIndicator().health(config, metadata(BoosterHealthIndicator.class, true));

    assertThat(result.status()).isEqualTo(HealthStatus.DOWN);
    assertThat(result.details()).isEqualTo(Map.of("code", "NO_FUNCTION", "data", Map.of("a", 1)));
  }

  @Test
  void functionDetailsAreTheOsInformationAndTheGraphQLUrl() {
    when(sensor.graphQLFunctionUrl(config)).thenReturn("http://localhost:3000/graphql");

    HealthIndicatorResult result =
        new BoosterFunctionHealthIndicator().health(config, metadata(BoosterFunctionHealthIndicator.class, true));

    assertThat(result.status()).isEqualTo(HealthStatus.UP);
    assertThat(result.details()).containsOnlyKeys("cpus", "memory", "graphQL_url");
    assertThat(result.details().get("graphQL_url")).isEqualTo("http://localhost:3000/graphql");
    assertThat((List<?>) result.details().get("cpus")).isNotEmpty();
  }

  @Test
  void functionIsDownWhenGraphQLIsDownAndOmitsDetailsWhenDisabled() {
    when(sensor.isGraphQLFunctionUp(config)).thenReturn(false);

    HealthIndicatorResult result =
        new BoosterFunctionHealthIndicator().health(config, metadata(BoosterFunctionHealthIndicator.class, false));

    assertThat(result).isEqualTo(new HealthIndicatorResult(HealthStatus.DOWN, null));
  }

  @Test
  void databaseNeedsBothDatabasesAndReportsTheUrls() {
    when(sensor.databaseUrls(config)).thenReturn(List.of("events.json", "read_models.json"));

    HealthIndicatorResult up =
        new BoosterDatabaseHealthIndicator().health(config, metadata(BoosterDatabaseHealthIndicator.class, true));
    when(sensor.isDatabaseEventUp(config)).thenReturn(false);
    HealthIndicatorResult down =
        new BoosterDatabaseHealthIndicator().health(config, metadata(BoosterDatabaseHealthIndicator.class, true));

    assertThat(up.status()).isEqualTo(HealthStatus.UP);
    assertThat(up.details()).isEqualTo(Map.of("urls", List.of("events.json", "read_models.json")));
    assertThat(down.status()).isEqualTo(HealthStatus.DOWN);
  }

  @Test
  void databaseEventsUseTheProviderDetailsVerbatim() {
    when(sensor.databaseEventsHealthDetails(config)).thenReturn(Map.of("file", "events.json", "count", 6));

    RawHealthIndicatorResult result = new BoosterDatabaseEventsHealthIndicator()
        .health(config, metadata(BoosterDatabaseEventsHealthIndicator.class, true));

    assertThat(result).isEqualTo(new RawHealthIndicatorResult(HealthStatus.UP, Map.of("file", "events.json", "count", 6)));
  }

  @Test
  void databaseReadModelsKeepArrayDetails() {
    List<Map<String, Object>> containers = List.of(Map.of("url", "a", "count", 1), Map.of("url", "b", "count", 2));
    when(sensor.databaseReadModelsHealthDetails(config)).thenReturn(containers);

    RawHealthIndicatorResult result = new BoosterDatabaseReadModelsHealthIndicator()
        .health(config, metadata(BoosterDatabaseReadModelsHealthIndicator.class, true));

    assertThat(result).isEqualTo(new RawHealthIndicatorResult(HealthStatus.UP, containers));
  }

  @Test
  void databaseIndicatorsDoNotAskForDetailsWhenTheyAreDisabled() {
    when(sensor.isDatabaseEventUp(config)).thenReturn(false);
    when(sensor.areDatabaseReadModelsUp(config)).thenReturn(false);
    when(sensor.databaseEventsHealthDetails(config)).thenThrow(new IllegalStateException("not called"));
    when(sensor.databaseReadModelsHealthDetails(config)).thenThrow(new IllegalStateException("not called"));

    assertThat(new BoosterDatabaseEventsHealthIndicator().health(config, metadata(BoosterDatabaseEventsHealthIndicator.class, false)))
        .isEqualTo(new RawHealthIndicatorResult(HealthStatus.DOWN, null));
    assertThat(new BoosterDatabaseReadModelsHealthIndicator()
        .health(config, metadata(BoosterDatabaseReadModelsHealthIndicator.class, false)))
        .isEqualTo(new RawHealthIndicatorResult(HealthStatus.DOWN, null));
  }

  @Test
  void databaseIndicatorsAreDownWhenTheDetailsFail() {
    when(sensor.databaseReadModelsHealthDetails(config)).thenThrow(new IllegalStateException("boom"));

    RawHealthIndicatorResult result = new BoosterDatabaseReadModelsHealthIndicator()
        .health(config, metadata(BoosterDatabaseReadModelsHealthIndicator.class, true));

    assertThat(result.status()).isEqualTo(HealthStatus.DOWN);
    assertThat(result.details()).isEqualTo(Map.of("message", "boom"));
  }
}
