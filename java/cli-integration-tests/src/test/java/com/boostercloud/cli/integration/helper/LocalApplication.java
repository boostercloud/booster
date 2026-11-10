package com.boostercloud.cli.integration.helper;

import com.boostercloud.application.tester.ApplicationOutputs;
import com.boostercloud.application.tester.ApplicationTester;
import com.boostercloud.application.tester.Counters;
import com.boostercloud.application.tester.ProviderTestHelper;
import com.boostercloud.application.tester.Queries;
import com.boostercloud.framework.provider.local.infrastructure.testhelper.LocalCounters;
import com.boostercloud.framework.provider.local.infrastructure.testhelper.LocalQueries;
import com.boostercloud.framework.provider.local.infrastructure.testhelper.LocalTestHelper;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * The {@link ApplicationTester} of an application started with {@code boost start -e local}: the local provider's
 * {@link LocalTestHelper} (TypeScript: {@code LocalTestHelper.build(applicationName())} through
 * {@code getProviderTestHelper()} of {@code app-helper.ts}), on the ports the test chose and reading the
 * {@code .booster} files of the project, adapted to the application tester's {@link ProviderTestHelper}.
 */
public final class LocalApplication {
  private LocalApplication() {}

  /**
   * Waits until the application answers GraphQL requests and returns its tester.
   *
   * @param appName the application name ({@code config.appName})
   * @param projectDirectory the project directory (the working directory of the application)
   * @param httpPort the port given to {@code boost start --port}
   * @param webSocketPort {@code LOCAL_ENVIRONMENT_WEBSOCKET_SERVER_PORT}
   * @return the tester
   */
  public static ApplicationTester tester(String appName, Path projectDirectory, int httpPort, int webSocketPort) {
    LocalTestHelper local =
        LocalTestHelper.build(
            appName,
            LocalTestHelper.Options.defaults()
                .withBaseDirectory(projectDirectory)
                .withPorts(httpPort, webSocketPort)
                .withReadinessTimeout(Duration.ofMinutes(1)));
    return new ApplicationTester(providerTestHelper(local));
  }

  /**
   * @param local the local provider's helper
   * @return the same endpoints, counters and queries as an application-tester {@link ProviderTestHelper}
   */
  public static ProviderTestHelper providerTestHelper(LocalTestHelper local) {
    var outputs = local.outputs();
    LocalCounters counters = local.counters();
    LocalQueries queries = local.queries();
    return ProviderTestHelper.of(
        new ApplicationOutputs(outputs.graphqlURL(), outputs.websocketURL(), outputs.healthURL()),
        new Counters() {
          @Override
          public long subscriptions() {
            return counters.subscriptions();
          }

          @Override
          public long connections() {
            return counters.connections();
          }

          @Override
          public long readModels(String readModelName) {
            return counters.readModels(readModelName);
          }

          @Override
          public long events() {
            return counters.events();
          }
        },
        new Queries() {
          @Override
          public List<Map<String, Object>> events(String primaryKey) {
            return queries.events(primaryKey);
          }

          @Override
          public List<Map<String, Object>> readModels(String primaryKey, String readModelName) {
            return queries.readModels(primaryKey, readModelName);
          }
        });
  }
}
