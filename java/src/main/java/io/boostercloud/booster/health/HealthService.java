package io.boostercloud.booster.health;

import io.boostercloud.booster.core.BoosterException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Authorized component-tree checks with inherited enablement and bounded sensor execution. */
public final class HealthService implements AutoCloseable {
  private record Registration(HealthConfiguration configuration, HealthIndicator indicator) {}
  public record Report(List<HealthComponent> components, boolean healthy) {
    public Report { components = List.copyOf(components); }
  }
  private final Map<String, Registration> sensors;
  private final HealthAuthorizer authorizer;
  private final long timeoutNanos;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final Semaphore slots;

  private HealthService(Builder builder) {
    sensors = Collections.unmodifiableMap(new LinkedHashMap<>(builder.sensors));
    authorizer = builder.authorizer; timeoutNanos = builder.timeout.toNanos(); slots = new Semaphore(builder.concurrency);
  }
  public static Builder builder() { return new Builder(); }

  public Report check(HealthRequest request) {
    Objects.requireNonNull(request);
    if (!authorizer.permits(request)) throw new BoosterException(BoosterException.Code.FORBIDDEN, "Health access is not authorized");
    if (executor.isShutdown()) throw new IllegalStateException("Health service is closed");
    List<Registration> roots;
    if (request.componentPath().isEmpty()) roots = sensors.values().stream()
        .filter(value -> parent(value.configuration().id()).isEmpty()).toList();
    else {
      var selected = sensors.get(request.componentPath());
      if (selected == null) throw new BoosterException(BoosterException.Code.NOT_FOUND, "Unknown health component");
      roots = List.of(selected);
    }
    long deadline = System.nanoTime() + timeoutNanos;
    var results = resolve(roots, request, deadline);
    return new Report(results, healthy(results));
  }

  private List<HealthComponent> resolve(List<Registration> registrations, HealthRequest request, long deadline) {
    var results = new ArrayList<HealthComponent>();
    for (var registration : registrations) {
      var configuration = registration.configuration();
      if (!enabled(configuration.id())) continue;
      HealthResult check = execute(registration, request, deadline);
      List<HealthComponent> children = null;
      if (showChildren(configuration.id())) {
        var childRegistrations = sensors.values().stream()
            .filter(value -> parent(value.configuration().id()).equals(configuration.id())).toList();
        if (!childRegistrations.isEmpty()) children = resolve(childRegistrations, request, deadline);
      }
      results.add(new HealthComponent(configuration.name(), configuration.id(), check.status(),
          configuration.details() ? check.details() : null, children));
    }
    return List.copyOf(results);
  }

  private HealthResult execute(Registration registration, HealthRequest request, long deadline) {
    long remaining = deadline - System.nanoTime();
    if (remaining <= 0) return down("Health check timed out");
    if (!slots.tryAcquire()) return down("Health check capacity reached");
    var task = new java.util.concurrent.FutureTask<HealthResult>(() ->
        Objects.requireNonNull(registration.indicator().check(registration.configuration(), request)));
    // Release outside FutureTask: cancellation before execution must also return
    // the slot, while a sensor ignoring interruption must continue holding it.
    try { executor.execute(() -> { try { task.run(); } finally { slots.release(); } }); }
    catch (RuntimeException failure) { slots.release(); throw failure; }
    try { return task.get(remaining, TimeUnit.NANOSECONDS); }
    catch (TimeoutException failure) { task.cancel(true); return down("Health check timed out"); }
    catch (InterruptedException failure) { task.cancel(true); Thread.currentThread().interrupt(); return down("Health check interrupted"); }
    catch (ExecutionException failure) { return down("Health check failed"); }
  }

  private boolean enabled(String id) {
    for (String current = id; !current.isEmpty(); current = parent(current)) {
      var registration = sensors.get(current);
      if (registration != null && !registration.configuration().enabled()) return false;
    }
    return true;
  }
  private boolean showChildren(String id) {
    for (String current = id; !current.isEmpty(); current = parent(current)) {
      var registration = sensors.get(current);
      if (registration != null && !registration.configuration().showChildren()) return false;
    }
    return true;
  }
  private static String parent(String id) { int index = id.lastIndexOf('/'); return index < 0 ? "" : id.substring(0, index); }
  private static HealthResult down(String reason) { return HealthResult.of(HealthStatus.DOWN, Map.of("error", reason)); }
  private static boolean healthy(List<HealthComponent> components) {
    for (var component : components) {
      if (component.id().equals("rockets") && component.status() == HealthStatus.UNKNOWN) continue;
      if (component.status() != HealthStatus.UP || (component.components() != null && !healthy(component.components()))) return false;
    }
    return true;
  }
  @Override public void close() { executor.shutdownNow(); }

  public static final class Builder {
    private final Map<String, Registration> sensors = new LinkedHashMap<>();
    // Matches the baseline health policy. Provider defaults remain disabled until configured.
    private HealthAuthorizer authorizer = request -> true;
    private Duration timeout = Duration.ofSeconds(5);
    private int concurrency = 32;
    private Builder() {}
    public Builder authorize(HealthAuthorizer authorizer) { this.authorizer = Objects.requireNonNull(authorizer); return this; }
    public Builder timeout(Duration timeout) {
      if (timeout == null || timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofMinutes(1)) > 0)
        throw new IllegalArgumentException("Health timeout must be positive and at most one minute");
      this.timeout = timeout; return this;
    }
    public Builder concurrency(int capacity) {
      if (capacity < 1 || capacity > 256) throw new IllegalArgumentException("Health concurrency must be 1 to 256");
      concurrency = capacity; return this;
    }
    /** Custom sensors may override provider defaults at the same component ID. */
    public Builder indicator(HealthConfiguration configuration, HealthIndicator indicator) {
      Objects.requireNonNull(configuration); Objects.requireNonNull(indicator);
      if (!sensors.containsKey(configuration.id()) && sensors.size() >= 256) throw new IllegalArgumentException("Too many health sensors");
      sensors.put(configuration.id(), new Registration(configuration, indicator)); return this;
    }
    public Builder configure(String id, boolean enabled, boolean details, boolean showChildren) {
      var current = sensors.get(id);
      if (current == null) throw new IllegalArgumentException("Unknown health sensor: " + id);
      return indicator(new HealthConfiguration(id, current.configuration().name(), enabled, details, showChildren), current.indicator());
    }
    public Builder configureAll(boolean enabled, boolean details, boolean showChildren) {
      for (String id : List.copyOf(sensors.keySet())) configure(id, enabled, details, showChildren);
      return this;
    }
    public HealthService build() {
      for (String id : sensors.keySet()) {
        String parent = parent(id);
        if (!parent.isEmpty() && !sensors.containsKey(parent)) throw new IllegalArgumentException("Health parent is missing: " + parent);
      }
      return new HealthService(this);
    }
  }
}
