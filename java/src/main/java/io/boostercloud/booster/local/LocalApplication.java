package io.boostercloud.booster.local;

import io.boostercloud.booster.core.Booster;
import io.boostercloud.booster.transport.AuthenticationProvider;
import io.boostercloud.booster.transport.BoosterHttpServer;
import io.boostercloud.booster.scheduling.ScheduledTask;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Pluggable application contract for the local infrastructure lifecycle. */
public interface LocalApplication {
  Booster create(LocalProvider provider);
  default AuthenticationProvider authentication() { return AuthenticationProvider.anonymous(); }
  /** Register webhook/upload routes and provider-owned schedulers before accepting requests. */
  default void configure(Booster runtime, BoosterHttpServer server, LocalProvider provider) throws Exception {}
  default List<ScheduledTask> schedules() { return List.of(); }
  default Map<String, BooleanSupplier> rocketHealthChecks(Booster runtime, LocalProvider provider) { return Map.of(); }
}
