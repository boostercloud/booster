package com.boostercloud.framework.provider.local.infrastructure.testhelper;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.provider.local.LocalProvider;
import com.boostercloud.framework.provider.local.LocalProviderRuntime;
import com.boostercloud.framework.provider.local.Provider;
import com.boostercloud.framework.provider.local.infrastructure.FakeUserApp;
import com.boostercloud.framework.provider.local.infrastructure.Infrastructure;
import com.boostercloud.framework.provider.local.infrastructure.LocalInfrastructure;
import com.boostercloud.framework.provider.local.infrastructure.LocalServer;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.framework.types.InternalInfo;
import com.boostercloud.framework.types.Logger;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

/** {@code LocalTestHelper.build} against a running server: the readiness check and the server URLs. */
class ReadinessTest {

  @TempDir
  Path directory;

  @Test
  void buildWaitsForAGraphQLAnswerAndUsesTheServerPorts() {
    BoosterConfig config = new BoosterConfig("test");
    config.logger = Mockito.mock(Logger.class);
    FakeUserApp app = new FakeUserApp(config);
    LocalProviderRuntime runtime = LocalProviderRuntime.create(LocalProviderRuntime.Options.defaults()
        .withBaseDirectory(directory).withWebSocketServer(true, "0").withUserApp(() -> app));
    app.provider(new LocalProvider(runtime, List.of(), Provider::loadInfrastructurePackage));
    LocalServer server = Infrastructure.infrastructure(null, LocalInfrastructure.Options.defaults().withRuntime(runtime)
        .withShutdownHook(false)).startServer(config, 0);
    try {
      LocalTestHelper helper = LocalTestHelper.build("my-store", LocalTestHelper.Options.defaults()
          .withBaseDirectory(directory).withPorts(server.port(), runtime.webSocketServerAdapter().port())
          .withReadinessTimeout(Duration.ofSeconds(5)));

      assertThat(helper.outputs()).isEqualTo(new ApplicationOutputs(server.graphqlURL(), server.websocketURL(),
          server.healthURL()));
      assertThat(app.graphQLRequests).hasSize(1);
      assertThat(helper.counters().events()).isZero();
    } finally {
      server.close();
      EnvironmentVariables.unset(InternalInfo.BOOSTER_LOCAL_PORT);
    }
  }
}
