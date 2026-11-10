package com.boostercloud.integration.azurecontract;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the TypeScript synthesis produces for the TypeScript app ({@code packages/framework-integration-tests/src},
 * environment {@code azure}, {@code BOOSTER_APP_SUFFIX=contract}), written out by hand from the TypeScript sources so
 * that the Java synthesis of the Java port of the app is compared with TypeScript, not with itself:
 *
 * <ul>
 *   <li>the concepts that drive the synthesis: the 19 read models of {@code src/read-models}, the scheduled command
 *       {@code CheckCartCount} ({@code @ScheduledCommand({ minute: '0/1' })}), subscriptions and event streaming on
 *       ({@code configureSubscriptions}, {@code configureEventHub}: topic {@code test}, 3 partitions, 1 day), no
 *       rockets, assets {@code ['assets', 'assetFile.txt', 'host.json']};
 *   <li>the names {@code helper/utils.ts} derives from {@code my-store-contract} + {@code azure}
 *       ({@code toTerraformName}, {@code createResourceGroupName}, ...; worked out with the rules of spec 12 §3.2);
 *   <li>the functions {@code helper/functions-code-generator.ts} registers, in its order.
 * </ul>
 */
final class TypeScriptAzureApp {

  /** {@code BOOSTER_APP_SUFFIX} of the contract suites. */
  static final String APP_SUFFIX = "contract";
  /** {@code 'my-store-' + (process.env.BOOSTER_APP_SUFFIX ?? 'default')}. */
  static final String APP_NAME = "my-store-" + APP_SUFFIX;
  /** The environment. */
  static final String ENVIRONMENT = "azure";
  /** The Terraform stack: {@code config.appName + config.environmentName}. */
  static final String STACK = APP_NAME + ENVIRONMENT;
  /** {@code buildAppPrefix(config)}. */
  static final String APP_PREFIX = "mystorecontractazure";
  /** {@code createResourceGroupName(appName, environmentName)}. */
  static final String RESOURCE_GROUP = "mystorecontractazurerg";
  /** {@code createFunctionResourceGroupName(resourceGroupName)}. */
  static final String FUNCTION_APP = RESOURCE_GROUP + "fpr";
  /** {@code createStreamFunctionResourceGroupName(resourceGroupName)}. */
  static final String STREAM_FUNCTION_APP = RESOURCE_GROUP + "fcs";
  /** The Web PubSub service. */
  static final String WEB_PUB_SUB = RESOURCE_GROUP + "wps";
  /** {@code config.resourceNames.applicationStack}. */
  static final String DATABASE = APP_NAME + "-app";
  /** {@code config.resourceNames.eventsStore}. */
  static final String EVENTS_STORE = DATABASE + "-events-store";
  /** {@code config.resourceNames.dispatchedEventsStore}. */
  static final String DISPATCHED_EVENTS = DATABASE + "-dispatched-events";
  /** {@code config.resourceNames.eventsDedup}. */
  static final String EVENTS_DEDUP = DATABASE + "-events-dedup";
  /** {@code config.resourceNames.subscriptionsStore}. */
  static final String SUBSCRIPTIONS_STORE = DATABASE + "-subscriptions-store";
  /** {@code config.resourceNames.connectionsStore}. */
  static final String CONNECTIONS_STORE = DATABASE + "-connections-store";
  /** {@code config.resourceNames.streamTopic} ({@code eventStreamConfiguration.parameters.streamTopic}). */
  static final String STREAM_TOPIC = "test";
  /** The application class of the function apps ({@code BOOSTER_APP_CLASS}, Java only). */
  static final String APPLICATION_CLASS = "com.boostercloud.integration.Main";

  /** The read models of {@code src/read-models}. */
  static final List<String> READ_MODELS = List.of("AnotherCounterReadModel", "BookReadModel", "CarModelReadModel",
      "CarOfferReadModel", "CarOwnerReadModel", "CarPurchasesReadModel", "CartReadModel", "ConcurrencyReadModel",
      "CounterReadModel", "DataMigrationsReadModel", "LogEventReceivedTestReadModel", "MovieReadModel",
      "OtherConcurrencyReadModel", "ProductReadModel", "ProductUpdatesReadModel", "ProductsBySKU", "SchemaReadModel",
      "SpecialReportsReadModel", "StockReadModel");

  /** {@code scheduleFunction-<command>} with its NCRONTAB schedule ({@code 0 <minute> <hour> <day> <month> <weekDay>}). */
  static final Map<String, String> SCHEDULED_FUNCTIONS = Map.of("scheduleFunction-CheckCartCount", "0 0/1 * * * *");

  /** The assets of the {@code azure} environment. */
  static final List<String> ASSETS = List.of("assets", "assetFile.txt", "host.json");

  /** The asset files the packages carry (directories expanded). */
  static final List<String> ASSET_FILES = List.of("assets/certs/public.key", "assets/example/file-1.txt",
      "assets/file-2.txt", "assetFile.txt");

  /**
   * A Cosmos DB container of the stack.
   *
   * @param terraformId the construct id ({@code toTerraformName(...)})
   * @param name the container name
   * @param partitionKeyPath the partition key path
   * @param uniqueKeyPath the unique key path, or {@code null}
   * @param defaultTtl {@code default_ttl}, or {@code null}
   */
  record ContainerSpec(String terraformId, String name, String partitionKeyPath, String uniqueKeyPath,
      Integer defaultTtl) {}

  private TypeScriptAzureApp() {}

  /**
   * @return every container of the stack ({@code TerraformContainers.build} with subscriptions, then the dedup
   *     container of {@code buildEventHub})
   */
  static List<ContainerSpec> containers() {
    List<ContainerSpec> containers = new ArrayList<>();
    containers.add(new ContainerSpec("storecontractazureevents", EVENTS_STORE, "/entityTypeName_entityID_kind", null,
        null));
    containers.add(new ContainerSpec("actazuredispatchedevents", DISPATCHED_EVENTS, "/eventId", "/eventId", 300));
    containers.add(new ContainerSpec("tazuresubscriptionstable", SUBSCRIPTIONS_STORE, "/className", null, -1));
    containers.add(new ContainerSpec("actazureconnectionstable", CONNECTIONS_STORE, "/connectionID", null, -1));
    for (Map.Entry<String, String> readModel : readModelTerraformIds().entrySet()) {
      containers.add(new ContainerSpec(readModel.getValue(), DATABASE + "-" + readModel.getKey(), "/id", null, null));
    }
    containers.add(new ContainerSpec("econtractazurededuptable", EVENTS_DEDUP, "/primaryKey", "/primaryKey", -1));
    return containers;
  }

  /**
   * @return {@code toTerraformName(readModelName)} of every read model (lower case, without {@code -}/{@code _}, the
   *     last 24 characters)
   */
  static Map<String, String> readModelTerraformIds() {
    Map<String, String> ids = new LinkedHashMap<>();
    for (String readModel : READ_MODELS) {
      String clean = readModel.toLowerCase(java.util.Locale.ROOT);
      ids.put(readModel, clean.length() > 24 ? clean.substring(clean.length() - 24) : clean);
    }
    return ids;
  }

  /**
   * @return the functions of the main function app in the order {@code generateFunctionsCode} registers them
   */
  static List<String> mainFunctions() {
    List<String> functions = new ArrayList<>(List.of("graphql", "sensor-health", "eventProducer", "connect",
        "disconnect", "messages"));
    for (String readModel : READ_MODELS) {
      functions.add(readModel + "-subscriptions-notifier");
    }
    functions.addAll(SCHEDULED_FUNCTIONS.keySet());
    return functions;
  }

  /**
   * Creates every container of the stack in the fake, as {@code terraform apply} creates them in Azure.
   *
   * @param services the fake
   */
  static void provision(FakeAzureServices services) {
    for (ContainerSpec container : containers()) {
      services.createContainer(DATABASE, container.name(), container.partitionKeyPath(), container.uniqueKeyPath());
    }
  }
}
