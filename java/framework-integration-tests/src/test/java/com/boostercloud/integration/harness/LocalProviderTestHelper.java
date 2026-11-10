package com.boostercloud.integration.harness;

import com.boostercloud.application.tester.ApplicationOutputs;
import com.boostercloud.application.tester.Counters;
import com.boostercloud.application.tester.ProviderTestHelper;
import com.boostercloud.application.tester.Queries;
import com.boostercloud.framework.provider.local.infrastructure.testhelper.LocalCounters;
import com.boostercloud.framework.provider.local.infrastructure.testhelper.LocalQueries;
import com.boostercloud.framework.provider.local.infrastructure.testhelper.LocalTestHelper;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The application tester's {@link ProviderTestHelper} over the local provider's {@link LocalTestHelper} (the
 * local-infrastructure module does not depend on the application tester, so the two types meet here). Counters and
 * queries read the {@code .booster} files of the application's project directory read-only, while it runs in its own
 * process.
 */
public final class LocalProviderTestHelper implements ProviderTestHelper {

  private final LocalTestHelper helper;
  private final ApplicationOutputs outputs;
  private final Counters counters;
  private final Queries queries;

  /**
   * @param helper the local test helper
   */
  public LocalProviderTestHelper(LocalTestHelper helper) {
    this.helper = Objects.requireNonNull(helper, "helper");
    var localOutputs = helper.outputs();
    this.outputs = new ApplicationOutputs(localOutputs.graphqlURL(), localOutputs.websocketURL(),
        localOutputs.healthURL());
    LocalCounters localCounters = helper.counters();
    this.counters = new Counters() {
      @Override
      public long subscriptions() {
        return localCounters.subscriptions();
      }

      @Override
      public long connections() {
        return localCounters.connections();
      }

      @Override
      public long readModels(String readModelName) {
        return localCounters.readModels(readModelName);
      }

      @Override
      public long events() {
        return localCounters.events();
      }
    };
    LocalQueries localQueries = helper.queries();
    this.queries = new Queries() {
      @Override
      public List<Map<String, Object>> events(String primaryKey) {
        return localQueries.events(primaryKey);
      }

      @Override
      public List<Map<String, Object>> readModels(String primaryKey, String readModelName) {
        return localQueries.readModels(primaryKey, readModelName);
      }
    };
  }

  /**
   * @param appName the application name ({@code AppHelper.applicationName()})
   * @param app the running application
   * @return the helper for it (the application is ready already, so the readiness check is a single request)
   */
  public static LocalProviderTestHelper of(String appName, AppProcess app) {
    return new LocalProviderTestHelper(LocalTestHelper.build(appName, options(app)));
  }

  /**
   * @param app an application process
   * @return the local test helper options for it: its project directory and ports
   */
  public static LocalTestHelper.Options options(AppProcess app) {
    return LocalTestHelper.Options.defaults()
        .withBaseDirectory(app.projectDirectory())
        .withPorts(app.httpPort(), app.webSocketPort())
        .withReadinessTimeout(java.time.Duration.ofSeconds(30));
  }

  /**
   * @return the wrapped local test helper (for its extra methods, e.g. {@code queries().events(key, latestFirst)})
   */
  public LocalTestHelper localTestHelper() {
    return helper;
  }

  @Override
  public ApplicationOutputs outputs() {
    return outputs;
  }

  @Override
  public Counters counters() {
    return counters;
  }

  @Override
  public Queries queries() {
    return queries;
  }
}
