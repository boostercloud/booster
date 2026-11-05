package io.boostercloud.booster.azure.hosting;

import io.boostercloud.booster.azure.AzureProvider;
import io.boostercloud.booster.core.Booster;
import io.boostercloud.booster.transport.AuthenticationProvider;
import io.boostercloud.booster.transport.BoosterHttpServer;
import io.boostercloud.booster.scheduling.ScheduledTask;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.function.BooleanSupplier;
import io.boostercloud.booster.rockets.uploads.FileUploadRocket;

/** Application entry point loaded by the Java 27 Azure container host. */
public interface AzureApplication {
  /** Use both provider stores so snapshots, projections and migration state are durable. */
  Booster create(AzureProvider provider);
  default AuthenticationProvider authentication() { return AuthenticationProvider.anonymous(); }
  /** Mount configured rockets and application HTTP extensions before the server starts. */
  default void configure(Booster runtime, BoosterHttpServer server) {}
  default List<ScheduledTask> schedules() { return List.of(); }
  /** Rocket installed into the builder during create(); host mounts its authenticated Blob callback. */
  default Optional<FileUploadRocket> fileUploads() { return Optional.empty(); }
  default Map<String, BooleanSupplier> rocketHealthChecks(Booster runtime, AzureProvider provider) { return Map.of(); }
}
