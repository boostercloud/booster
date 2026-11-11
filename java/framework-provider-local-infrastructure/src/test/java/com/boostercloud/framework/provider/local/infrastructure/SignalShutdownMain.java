package com.boostercloud.framework.provider.local.infrastructure;

import com.boostercloud.framework.provider.local.LocalProvider;
import com.boostercloud.framework.provider.local.LocalProviderRuntime;
import com.boostercloud.framework.provider.local.Provider;
import com.boostercloud.framework.provider.local.library.ApiAdapter;
import com.boostercloud.framework.types.BoosterConfig;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Runs {@link LocalInfrastructure#start} in a separate JVM for {@link SignalShutdownTest}: the GraphQL handler prints
 * {@code REQUEST RECEIVED} and takes 1.5 s to answer.
 */
public final class SignalShutdownMain {

  private SignalShutdownMain() {}

  public static void main(String[] args) {
    BoosterConfig config = new BoosterConfig("local");
    config.appName = "signal-test";
    FakeUserApp app = new FakeUserApp(config);
    app.onGraphQL(request -> {
      System.out.println("REQUEST RECEIVED");
      System.out.flush();
      try {
        Thread.sleep(1500);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return ApiAdapter.requestSucceeded(Map.of("data", Map.of("finished", true)), Map.of());
    });
    LocalProviderRuntime runtime = LocalProviderRuntime.create(LocalProviderRuntime.Options.defaults()
        .withBaseDirectory(Path.of(args[0])).withWebSocketServer(true, "0").withUserApp(() -> app));
    app.provider(new LocalProvider(runtime, List.of(), Provider::loadInfrastructurePackage));
    Infrastructure.infrastructure(List.of(), LocalInfrastructure.Options.defaults().withRuntime(runtime))
        .start(config, 0);
    System.out.println("START RETURNED");
  }
}
