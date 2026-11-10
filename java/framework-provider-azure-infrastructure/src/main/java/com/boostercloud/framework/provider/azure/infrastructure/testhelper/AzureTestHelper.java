package com.boostercloud.framework.provider.azure.infrastructure.testhelper;

import com.boostercloud.framework.provider.azure.cosmos.CosmosClient;
import com.boostercloud.framework.provider.azure.cosmos.rest.CosmosRestClient;
import com.boostercloud.framework.provider.azure.cosmos.rest.CosmosRetryOptions;
import com.boostercloud.framework.provider.azure.infrastructure.helper.AzCliHelper;
import com.boostercloud.framework.provider.azure.infrastructure.helper.Params;
import com.boostercloud.framework.provider.azure.infrastructure.types.ResourceGroup;
import com.boostercloud.framework.types.EnvironmentVariables;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Port of {@code test-helper/azure-test-helper.ts}: what the integration tests need to talk to an application deployed
 * on Azure (the shape of application-tester's {@code ProviderTestHelper}: {@link #outputs()}, {@link #counters()},
 * {@link #queries()}). It finds the resource group and the Cosmos DB connection string with the {@code az} CLI.
 */
public final class AzureTestHelper {

  private final ApplicationOutputs outputs;
  private final AzureCounters counters;
  private final AzureQueries queries;

  private AzureTestHelper(ApplicationOutputs outputs, AzureCounters counters, AzureQueries queries) {
    this.outputs = outputs;
    this.counters = counters;
    this.queries = queries;
  }

  /**
   * @return the endpoints
   */
  public ApplicationOutputs outputs() {
    return outputs;
  }

  /**
   * @return the document counters
   */
  public AzureCounters counters() {
    return counters;
  }

  /**
   * @return the raw queries
   */
  public AzureQueries queries() {
    return queries;
  }

  /**
   * {@code checkResourceGroup(applicationName, environmentName)}: {@code az group show}.
   *
   * @param applicationName the application name
   * @param environmentName the environment name
   * @return the resource group
   */
  public static ResourceGroup checkResourceGroup(String applicationName, String environmentName) {
    return new AzCliHelper().getResourceGroup(applicationName, environmentName);
  }

  /**
   * {@code build(appName, environmentName)} with the {@code az} CLI, the environment's Azure settings and the Cosmos DB
   * REST client.
   *
   * @param appName the application name
   * @param environmentName the environment name
   * @return the helper
   */
  public static AzureTestHelper build(String appName, String environmentName) {
    return build(appName, environmentName, new AzCliHelper(), Params.configuration(),
        connectionString -> CosmosRestClient.create(connectionString, CosmosRetryOptions.defaults()));
  }

  /**
   * {@code build(appName, environmentName)}: checks the resource group and the Azure settings, reads the Cosmos DB
   * connection string and builds the outputs, counters and queries.
   *
   * @param appName the application name
   * @param environmentName the environment name
   * @param azCli the {@code az} CLI helper
   * @param configuration the Azure settings
   * @param cosmosClientFactory creates the Cosmos DB client of a connection string
   * @return the helper
   */
  public static AzureTestHelper build(
      String appName,
      String environmentName,
      AzCliHelper azCli,
      Params.Configuration configuration,
      Function<String, CosmosClient> cosmosClientFactory) {
    ResourceGroup resourceGroup = azCli.getResourceGroup(appName, environmentName);
    ensureAzureConfiguration(configuration);
    String cosmosConnectionString = getCosmosConnection(azCli, appName, environmentName);
    CosmosClient db = cosmosClientFactory.apply(cosmosConnectionString);
    return new AzureTestHelper(
        new ApplicationOutputs(graphqlURL(resourceGroup), websocketURL(resourceGroup, "booster"),
            healthURL(resourceGroup)),
        new AzureCounters(appName, db),
        new AzureQueries(appName, db));
  }

  /**
   * {@code ensureAzureConfiguration()} with the environment's settings.
   */
  public static void ensureAzureConfiguration() {
    ensureAzureConfiguration(Params.configuration());
  }

  /**
   * {@code ensureAzureConfiguration()}: prints {@code Checking Azure configuration...} and checks the credentials and
   * the region.
   *
   * @param configuration the settings
   * @throws IllegalStateException {@code Azure credentials were not properly loaded and are required to run the
   *     integration tests} followed by the four values (secret included, as in TypeScript), or {@code Azure region was
   *     not properly loaded and is required to run the integration tests. }
   */
  public static void ensureAzureConfiguration(Params.Configuration configuration) {
    System.out.println("Checking Azure configuration...");
    if (isEmpty(configuration.appId()) || isEmpty(configuration.tenantId()) || isEmpty(configuration.secret())
        || isEmpty(configuration.subscriptionId())) {
      throw new IllegalStateException("Azure credentials were not properly loaded and are required to run the"
          + " integration tests"
          + "\nappId = " + configuration.appId()
          + "\ntenantId = " + configuration.tenantId()
          + "\nsecret = " + configuration.secret()
          + "\nsubscriptionId = " + configuration.subscriptionId());
    }
    if (isEmpty(configuration.region())) {
      throw new IllegalStateException("Azure region was not properly loaded and is required to run the integration"
          + " tests. ");
    }
    System.out.println("Azure Region set to " + configuration.region());
  }

  private static String getCosmosConnection(AzCliHelper azCli, String appName, String environmentName) {
    Map<String, Object> connectionDetails = azCli.getCosmosConnectionStrings(appName, environmentName);
    Object mainDbConnection = connectionDetails.get("connectionStrings") instanceof List<?> list && !list.isEmpty()
        ? list.get(0)
        : null;
    if (!(mainDbConnection instanceof Map<?, ?> connection)) {
      throw new IllegalStateException("Unable to get cosmos connection details from current resource group");
    }
    return Objects.toString(connection.get("connectionString"), null);
  }

  /**
   * {@code graphqlURL(resourceGroup)}:
   * {@code http://<rg>apis.<region>.cloudapp.azure.com/<BOOSTER_ENV or 'DEFAULT'>/graphql}, also printed as
   * {@code service Url: <url>}.
   *
   * @param resourceGroup the resource group
   * @return the URL
   */
  public static String graphqlURL(ResourceGroup resourceGroup) {
    String environment = EnvironmentVariables.get("BOOSTER_ENV").orElse("DEFAULT");
    String url = "http://" + resourceGroup.name() + "apis." + getRegion() + ".cloudapp.azure.com/" + environment
        + "/graphql";
    System.out.println("service Url: " + url);
    return url;
  }

  /**
   * {@code healthURL(resourceGroup)}:
   * {@code http://<rg>apis.<region>.cloudapp.azure.com/<BOOSTER_ENV or 'azure'>/sensor/health/}.
   *
   * @param resourceGroup the resource group
   * @return the URL
   */
  public static String healthURL(ResourceGroup resourceGroup) {
    String environment = EnvironmentVariables.get("BOOSTER_ENV").orElse("azure");
    return "http://" + resourceGroup.name() + "apis." + getRegion() + ".cloudapp.azure.com/" + environment
        + "/sensor/health/";
  }

  private static String websocketURL(ResourceGroup resourceGroup, String hubName) {
    return "wss://" + resourceGroup.name() + "wps.webpubsub.azure.com:443/client/hubs/" + hubName;
  }

  private static String getRegion() {
    return EnvironmentVariables.get(Params.REGION).orElse("").toLowerCase(Locale.ROOT).replace(" ", "");
  }

  private static boolean isEmpty(String value) {
    return value == null || value.isEmpty();
  }
}
