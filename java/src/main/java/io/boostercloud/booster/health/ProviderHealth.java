package io.boostercloud.booster.health;

import io.boostercloud.booster.core.Booster;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Standard sensor IDs backed by actual provider reads, plus configured rocket checks. */
public final class ProviderHealth {
  private ProviderHealth() {}

  /**
   * Register the baseline sensors disabled by default. Enable selected sensors explicitly
   * with Builder.configure/configureAll, and set authorization before exposing details.
   * functionUp and rocket probes must describe the actual application/provider lifecycle.
   */
  public static HealthService.Builder defaults(Booster booster, BooleanSupplier functionUp,
      Supplier<String> graphQLUrl, List<String> databaseUrls, Map<String, BooleanSupplier> rocketChecks) {
    Objects.requireNonNull(booster); Objects.requireNonNull(functionUp); Objects.requireNonNull(graphQLUrl);
    var urls = List.copyOf(databaseUrls);
    var rockets = new LinkedHashMap<String, BooleanSupplier>(rocketChecks);
    var builder = HealthService.builder();
    builder.indicator(configuration("booster", "Booster"), (config, request) -> {
      boolean available = functionUp.getAsBoolean() && eventsUp(booster) && modelsUp(booster);
      return new HealthResult(status(available), config.details()
          ? io.boostercloud.booster.core.Json.MAPPER.valueToTree(Map.of("boosterVersion", "0.1.0-SNAPSHOT", "javaVersion", Runtime.version().toString())) : null);
    });
    builder.indicator(configuration("booster/function", "Booster Function"), (config, request) -> {
      boolean available = functionUp.getAsBoolean();
      if (!config.details()) return new HealthResult(status(available));
      Runtime runtime = Runtime.getRuntime();
      var details = new LinkedHashMap<String, Object>();
      details.put("graphQL_url", graphQLUrl.get());
      details.put("cpus", runtime.availableProcessors());
      details.put("memory", Map.of("totalBytes", runtime.totalMemory(), "freeBytes", runtime.freeMemory(), "maxBytes", runtime.maxMemory()));
      return HealthResult.of(status(available), details);
    });
    builder.indicator(configuration("booster/database", "Booster Database"), (config, request) -> {
      boolean available = eventsUp(booster) && modelsUp(booster);
      return config.details() ? HealthResult.of(status(available), Map.of("urls", urls)) : new HealthResult(status(available));
    });
    builder.indicator(configuration("booster/database/events", "Booster Database Events"), (config, request) -> {
      long position = booster.eventStore().lastPosition();
      return config.details() ? HealthResult.of(HealthStatus.UP, Map.of("journalPosition", position)) : new HealthResult(HealthStatus.UP);
    });
    builder.indicator(configuration("booster/database/readmodels", "Booster Database ReadModels"), (config, request) -> {
      modelsUp(booster);
      return config.details() ? HealthResult.of(HealthStatus.UP, Map.of("models", booster.readModels().stream().map(model -> model.name()).toList()))
          : new HealthResult(HealthStatus.UP);
    });
    builder.indicator(configuration("rockets", "Rockets"), (config, request) -> {
      if (rockets.isEmpty()) return config.details()
          ? HealthResult.of(HealthStatus.UNKNOWN, Map.of("reason", "No Rockets found")) : new HealthResult(HealthStatus.UNKNOWN);
      int healthy = 0;
      for (var check : rockets.values()) if (check.getAsBoolean()) healthy++;
      return new HealthResult(healthy == rockets.size() ? HealthStatus.UP : healthy == 0 ? HealthStatus.DOWN : HealthStatus.PARTIALLY_UP);
    });
    rockets.forEach((name, check) -> {
      Objects.requireNonNull(check, "rocket check");
      if (name.indexOf('/') >= 0) throw new IllegalArgumentException("Rocket health names must be a single path component");
      builder.indicator(configuration("rockets/" + name, name), (config, request) -> new HealthResult(status(check.getAsBoolean())));
    });
    return builder;
  }

  private static HealthConfiguration configuration(String id, String name) { return new HealthConfiguration(id, name, false, true, true); }
  private static HealthStatus status(boolean available) { return available ? HealthStatus.UP : HealthStatus.DOWN; }
  private static boolean eventsUp(Booster booster) { booster.eventStore().lastPosition(); return true; }
  private static boolean modelsUp(Booster booster) {
    // Execute a real provider query even for an application with no read models.
    if (booster.readModels().isEmpty()) booster.stateStore().scan("booster.health", null, 1);
    else for (var model : booster.readModels()) booster.stateStore().scan("readModels:" + model.name(), null, 1);
    return true;
  }
}
