package com.boostercloud.framework.provider.local.infrastructure;

import com.boostercloud.framework.provider.local.LocalProvider;
import com.boostercloud.framework.provider.local.LocalProviderRuntime;
import com.boostercloud.framework.provider.local.Provider;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.framework.types.InternalInfo;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.rockets.RocketDescriptor;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.mockito.Mockito;

/** Starts local servers over a temporary project directory with a fake application, and stops them afterwards. */
final class ServerFixture implements AutoCloseable {

  final BoosterConfig config;
  final Logger logger = Mockito.mock(Logger.class);
  final ManualClock clock = new ManualClock(ZonedDateTime.of(2024, 1, 31, 10, 3, 30, 0, ZoneId.of("UTC")));
  private final List<LocalServer> servers = new ArrayList<>();

  ServerFixture() {
    config = new BoosterConfig("test");
    config.appName = "my-store";
    config.logger = logger;
  }

  /** A runtime over {@code directory} for {@code app}, with its websocket server on a free port. */
  static LocalProviderRuntime runtime(Path directory, FakeUserApp app) {
    LocalProviderRuntime runtime = LocalProviderRuntime.create(LocalProviderRuntime.Options.defaults()
        .withBaseDirectory(directory).withWebSocketServer(true, "0").withUserApp(() -> app));
    app.provider(new LocalProvider(runtime, List.of(), Provider::loadInfrastructurePackage));
    return runtime;
  }

  LocalInfrastructure.Options options(LocalProviderRuntime runtime) {
    return LocalInfrastructure.Options.defaults().withRuntime(runtime).withShutdownHook(false)
        .withSchedulerClock(clock).withShutdownTimeout(Duration.ofSeconds(5));
  }

  LocalServer start(Path directory, FakeUserApp app) {
    return start(directory, app, List.of());
  }

  LocalServer start(Path directory, FakeUserApp app, List<RocketDescriptor> rockets) {
    LocalServer server = Infrastructure.infrastructure(rockets, options(runtime(directory, app))).startServer(config, 0);
    servers.add(server);
    return server;
  }

  @Override
  public void close() {
    servers.forEach(LocalServer::close);
    EnvironmentVariables.unset(InternalInfo.BOOSTER_LOCAL_PORT);
  }
}
