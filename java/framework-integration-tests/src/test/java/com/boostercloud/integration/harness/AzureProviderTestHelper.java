package com.boostercloud.integration.harness;

import com.boostercloud.application.tester.ApplicationOutputs;
import com.boostercloud.application.tester.Counters;
import com.boostercloud.application.tester.ProviderTestHelper;
import com.boostercloud.application.tester.Queries;
import com.boostercloud.framework.provider.azure.infrastructure.testhelper.AzureCounters;
import com.boostercloud.framework.provider.azure.infrastructure.testhelper.AzureQueries;
import com.boostercloud.framework.provider.azure.infrastructure.testhelper.AzureTestHelper;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The application tester's {@link ProviderTestHelper} over the Azure provider's {@link AzureTestHelper}, for runs with
 * {@code TESTED_PROVIDER=AZURE} against a deployed application (TypeScript's {@code integration/azure-end-to-end}).
 * Nothing in this repository deploys to Azure, so this path is not exercised by the build.
 */
public final class AzureProviderTestHelper implements ProviderTestHelper {

  private final ApplicationOutputs outputs;
  private final Counters counters;
  private final Queries queries;

  /**
   * @param helper the Azure test helper
   */
  public AzureProviderTestHelper(AzureTestHelper helper) {
    Objects.requireNonNull(helper, "helper");
    var azureOutputs = helper.outputs();
    this.outputs = new ApplicationOutputs(azureOutputs.graphqlURL(), azureOutputs.websocketURL(),
        azureOutputs.healthURL());
    AzureCounters azureCounters = helper.counters();
    this.counters = new Counters() {
      @Override
      public long subscriptions() {
        return azureCounters.subscriptions();
      }

      @Override
      public long connections() {
        return azureCounters.connections();
      }

      @Override
      public long readModels(String readModelName) {
        return azureCounters.readModels(readModelName);
      }

      @Override
      public long events() {
        return azureCounters.events();
      }
    };
    AzureQueries azureQueries = helper.queries();
    this.queries = new Queries() {
      @Override
      public List<Map<String, Object>> events(String primaryKey) {
        return azureQueries.events(primaryKey);
      }

      @Override
      public List<Map<String, Object>> readModels(String primaryKey, String readModelName) {
        return azureQueries.readModels(primaryKey, readModelName);
      }
    };
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
