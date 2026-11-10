package com.boostercloud.integration.azurecontract;

import static com.boostercloud.integration.azurecontract.TypeScriptAzureApp.APP_NAME;
import static com.boostercloud.integration.azurecontract.TypeScriptAzureApp.CONNECTIONS_STORE;
import static com.boostercloud.integration.azurecontract.TypeScriptAzureApp.DATABASE;
import static com.boostercloud.integration.azurecontract.TypeScriptAzureApp.DISPATCHED_EVENTS;
import static com.boostercloud.integration.azurecontract.TypeScriptAzureApp.EVENTS_DEDUP;
import static com.boostercloud.integration.azurecontract.TypeScriptAzureApp.EVENTS_STORE;
import static com.boostercloud.integration.azurecontract.TypeScriptAzureApp.FUNCTION_APP;
import static com.boostercloud.integration.azurecontract.TypeScriptAzureApp.RESOURCE_GROUP;
import static com.boostercloud.integration.azurecontract.TypeScriptAzureApp.STREAM_TOPIC;
import static com.boostercloud.integration.azurecontract.TypeScriptAzureApp.SUBSCRIPTIONS_STORE;
import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.application.tester.ApplicationTester;
import com.boostercloud.framework.common.RunCommandError;
import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.provider.azure.cosmos.rest.CosmosRestClient;
import com.boostercloud.framework.provider.azure.cosmos.rest.CosmosRetryOptions;
import com.boostercloud.framework.provider.azure.functions.BoosterAzureFunctions;
import com.boostercloud.framework.provider.azure.infrastructure.helper.AzCliHelper;
import com.boostercloud.framework.provider.azure.infrastructure.helper.Params;
import com.boostercloud.framework.provider.azure.infrastructure.process.Command;
import com.boostercloud.framework.provider.azure.infrastructure.testhelper.AzureTestHelper;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.UserAppRegistry;
import com.boostercloud.integration.harness.AzureProviderTestHelper;
import com.boostercloud.integration.harness.Faker;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * The Azure contract of the application, part 3: the <b>runtime</b>. The Azure Functions entry points of
 * {@link BoosterAzureFunctions} are invoked in-process with the trigger payloads the Java worker would pass for the
 * synthesized {@code function.json} bindings, with the app settings Terraform gives the function apps pointing at an
 * in-JVM stateful fake of Cosmos DB, Event Hubs and Web PubSub ({@link FakeAzureServices}, provisioned with the
 * containers of the stack). The application boots from {@code BOOSTER_APP_CLASS} on the first invocation, as in a
 * function app, and the whole path of an event is followed with the production REST clients: a GraphQL mutation through
 * the HTTP function stores the event (transactional batch), the events store change feed goes through the producer to
 * the event hub of the stream topic, the consumer of the stream de-duplicates it, reduces the entity (snapshot) and
 * projects the read models, and a GraphQL query reads the read model back; a websocket subscription made through the
 * Web PubSub functions is notified by the read model change feed. Finally the Azure test helper of the TypeScript
 * {@code TESTED_PROVIDER=AZURE} runs reads what the functions stored.
 */
@DisplayName("Azure runtime contract")
class AzureRuntimeContractIT {

  static final String GRAPHQL_URL = "https://" + FUNCTION_APP + ".azurewebsites.net/api/graphql";

  static final String CHANGE_CART_ITEM = """
      mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
        ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
      }
      """;

  static final String CART_READ_MODEL = """
      query CartReadModel($cartId: ID!) {
        CartReadModel(id: $cartId) {
          id
          cartItems {
            productId
            quantity
          }
        }
      }
      """;

  /** {@code cartSubscription} of {@code functionality/subscriptions.integration.ts}, as Apollo sends it. */
  static final String CART_SUBSCRIPTION = """
      subscription CartReadModel($cartId: ID!) {
        CartReadModel(id: $cartId) {
          id
          cartItems {
            productId
            quantity
            __typename
          }
          __typename
        }
      }
      """;

  /** The deployment check of {@code azure/deployment/deployment.integration.ts}. */
  static final String CREATE_PRODUCT = """
      mutation CreateProduct(
        $sku: String!
        $displayName: String!
        $description: String!
        $priceInCents: Float!
        $currency: String!
      ) {
        CreateProduct(
          input: {
            sku: $sku
            displayName: $displayName
            description: $description
            priceInCents: $priceInCents
            currency: $currency
          }
        )
      }
      """;

  private final FakeAzureServices services = FakeAzureServices.shared();
  private final BoosterAzureFunctions functions = new BoosterAzureFunctions();
  private final String cartId = UUID.randomUUID().toString();
  private final String productId = UUID.randomUUID().toString();
  private final String connectionId = "connection-" + UUID.randomUUID();
  private InProcessAzureApplication application;
  private List<Object> streamMessages = List.of();

  /** The app settings of the function apps ({@code TerraformFunctionAppSettings}), with the fake's endpoints. */
  private Map<String, String> appSettings() {
    Map<String, String> settings = new LinkedHashMap<>();
    settings.put("BOOSTER_APP_SUFFIX", TypeScriptAzureApp.APP_SUFFIX);
    settings.put("WebPubSubConnectionString", services.webPubSubConnectionString());
    settings.put("BOOSTER_REST_API_URL", "http://" + RESOURCE_GROUP + "apis.eastus.cloudapp.azure.com/azure");
    settings.put("EVENTHUB_CONNECTION_STRING", services.eventHubConnectionString(STREAM_TOPIC));
    settings.put("EVENTHUB_NAME", STREAM_TOPIC);
    settings.put("EVENTHUB_MAX_RETRIES", "5");
    settings.put("EVENTHUB_MODE", "exponential");
    settings.put("COSMOSDB_CONNECTION_STRING", services.cosmosConnectionString());
    settings.put("BOOSTER_APP_NAME", "");
    settings.put("BOOSTER_APP_CLASS", TypeScriptAzureApp.APPLICATION_CLASS);
    // Not an app setting: the region of the machine running the TypeScript Azure suites (read by AzureTestHelper)
    settings.put("REGION", "East US");
    return settings;
  }

  @BeforeAll
  void provision() {
    services.reset();
    TypeScriptAzureApp.provision(services);
    application = InProcessAzureApplication.prepare(appSettings());
  }

  @AfterAll
  void tearDown() {
    if (application != null) {
      application.close();
    }
    services.reset();
  }

  @BeforeEach
  void forgetRequests() {
    services.clearRequests();
  }

  // ---------------------------------------------------------------------------------------------------------------

  @Test
  @Order(1)
  @DisplayName("the graphql function boots the application and stores the event of a mutation in one transactional batch")
  void mutationStoresTheEvent() {
    assertThat(UserAppRegistry.find()).isEmpty();

    FunctionsWorker.Response response = graphql(CHANGE_CART_ITEM,
        Map.of("cartId", cartId, "productId", productId, "quantity", 2), null);

    assertThat(response.getStatusCode()).isEqualTo(200);
    assertThat(BoosterJson.parse((String) response.getBody())).isEqualTo(Map.of("data", Map.of("ChangeCartItem", true)));
    assertThat(Booster.config().environmentName).isEqualTo("azure");
    assertThat(Booster.config().appName).isEqualTo(APP_NAME);

    List<FakeAzureServices.Request> cosmos = services.requests("cosmos");
    assertThat(cosmos).extracting(FakeAzureServices.Request::summary)
        .containsExactly("POST /dbs/" + DATABASE + "/colls/" + EVENTS_STORE + "/docs (batch)");
    FakeAzureServices.Request batch = cosmos.getFirst();
    assertThat(batch.header("x-ms-documentdb-partitionkey")).isEqualTo("[\"Cart-" + cartId + "-event\"]");
    assertThat(batch.header("x-ms-cosmos-batch-atomic")).isEqualTo("True");
    List<?> operations = (List<?>) batch.json();
    assertThat(operations).hasSize(1);
    Map<String, Object> operation = map(operations.getFirst());
    assertThat(operation.get("operationType")).isEqualTo("Create");
    Map<String, Object> event = map(operation.get("resourceBody"));
    assertThat(event).containsOnlyKeys("version", "kind", "superKind", "entityID", "requestID", "entityTypeName",
        "typeName", "value", "createdAt", "entityTypeName_entityID_kind", "id");
    assertThat(event).containsEntry("version", 1).containsEntry("kind", "event").containsEntry("superKind", "domain")
        .containsEntry("entityID", cartId).containsEntry("entityTypeName", "Cart")
        .containsEntry("typeName", "CartItemChanged")
        .containsEntry("value", Map.of("cartId", cartId, "productId", productId, "quantity", 2))
        .containsEntry("entityTypeName_entityID_kind", "Cart-" + cartId + "-event");
    assertThat((String) event.get("createdAt")).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z");
    assertThat(services.documents(DATABASE, EVENTS_STORE)).hasSize(1);
    assertThat(services.failures()).isEmpty();
  }

  @Test
  @Order(2)
  @DisplayName("the eventProducer function forwards the change feed documents to the event hub of the stream topic")
  void changeFeedProducesTheStream() {
    List<Map<String, Object>> feed = services.changeFeed(DATABASE, EVENTS_STORE);
    assertThat(feed).hasSize(1);

    functions.eventProducer(BoosterJson.toJson(feed), FunctionsWorker.context("eventProducer"));

    List<Map<String, Object>> messages = services.eventHubMessages(STREAM_TOPIC);
    assertThat(messages).hasSize(1);
    assertThat(messages.getFirst().get("BrokerProperties")).isEqualTo(Map.of("PartitionKey", "Cart-" + cartId + "-event"));
    // The message is the change feed document itself (system properties included: _etag is the consumer's dedup key)
    assertThat(BoosterJson.parse((String) messages.getFirst().get("Body"))).isEqualTo(feed.getFirst());
    assertThat(services.requests("eventhubs")).singleElement().satisfies(request -> {
      assertThat(request.path()).isEqualTo("/" + STREAM_TOPIC + "/messages");
      assertThat(request.query()).isEqualTo("timeout=60&api-version=2014-01");
    });
    assertThat(services.requests("cosmos")).isEmpty();
    assertThat(services.failures()).isEmpty();
    streamMessages = messages.stream().map(message -> message.get("Body")).toList();
  }

  @Test
  @Order(3)
  @DisplayName("the eventConsumer function de-duplicates the message, reduces the cart and projects its read models")
  void streamConsumerReducesAndProjects() {
    assertThat(streamMessages).hasSize(1);
    Map<String, Object> storedEvent = services.documents(DATABASE, EVENTS_STORE).getFirst();

    consume(streamMessages);

    assertThat(services.failures()).isEmpty();
    assertThat(services.documents(DATABASE, EVENTS_DEDUP)).singleElement()
        .satisfies(dedup -> assertThat(dedup).containsEntry("primaryKey", storedEvent.get("_etag")));
    assertThat(services.documents(DATABASE, DISPATCHED_EVENTS)).singleElement()
        .satisfies(marker -> assertThat(marker).containsEntry("eventId", storedEvent.get("id")));

    List<Map<String, Object>> snapshots = services.documents(DATABASE, EVENTS_STORE).stream()
        .filter(document -> "snapshot".equals(document.get("kind"))).toList();
    assertThat(snapshots).singleElement().satisfies(snapshot -> {
      assertThat(snapshot).containsEntry("entityTypeName", "Cart").containsEntry("entityID", cartId)
          .containsEntry("entityTypeName_entityID_kind", "Cart-" + cartId + "-snapshot")
          .containsEntry("snapshottedEventCreatedAt", storedEvent.get("createdAt"))
          .containsEntry("createdAt", storedEvent.get("createdAt"));
      assertThat(map(snapshot.get("value"))).containsEntry("id", cartId)
          .containsEntry("cartItems", List.of(Map.of("productId", productId, "quantity", 2)));
    });

    List<Map<String, Object>> carts = services.documents(DATABASE, DATABASE + "-CartReadModel");
    assertThat(carts).singleElement().satisfies(cart -> {
      assertThat(cart).containsEntry("id", cartId)
          .containsEntry("cartItems", List.of(Map.of("productId", productId, "quantity", 2)));
      // CartReadModel is at schema version 2 (the @SchemaMigration of migrations.readmodels.cartreadmodel)
      assertThat(map(cart.get("boosterMetadata"))).containsEntry("version", 1).containsEntry("schemaVersion", 2)
          .containsEntry("lastProjectionInfo", Map.of("entityId", cartId, "entityName", "Cart",
              "entityUpdatedAt", storedEvent.get("createdAt"), "projectionMethod", "CartReadModel.updateWithCart"));
    });
    // SchemaReadModel also projects every cart (src/read-models/schema-read-model.ts)
    assertThat(services.documents(DATABASE, DATABASE + "-SchemaReadModel")).hasSize(1);

    // The container definitions (partition key paths) are read once per container and cached by the client
    String collections = "/dbs/" + DATABASE + "/colls/";
    List<FakeAzureServices.Request> requests = services.requests("cosmos").stream()
        .filter(request -> request.path().contains("/docs")).toList();
    assertThat(services.requests("cosmos")).filteredOn(request -> !request.path().contains("/docs"))
        .allSatisfy(request -> assertThat(request.method()).isEqualTo("GET"));
    // De-duplication (EventsStreamConsumerAdapter), dispatched marker (BoosterEventProcessor), then the snapshot of the
    // cart (EventStore: latest snapshot, events since, the TypeScript duplicate-snapshot guard, the new snapshot)
    assertThat(requests.subList(0, 7)).extracting(FakeAzureServices.Request::summary).containsExactly(
        "POST " + collections + EVENTS_DEDUP + "/docs",
        "POST " + collections + DISPATCHED_EVENTS + "/docs",
        "POST " + collections + EVENTS_STORE + "/docs (query)",
        "POST " + collections + EVENTS_STORE + "/docs (query)",
        "POST " + collections + EVENTS_STORE + "/docs (query plan)",
        "POST " + collections + EVENTS_STORE + "/docs (query)",
        "POST " + collections + EVENTS_STORE + "/docs");
    assertThat(requests.get(2).header("x-ms-documentdb-partitionkey")).isEqualTo("[\"Cart-" + cartId + "-snapshot\"]");
    assertThat(map(requests.get(2).json()).get("query")).isEqualTo("SELECT * FROM c WHERE"
        + " c[\"entityTypeName_entityID_kind\"] = @partitionKey ORDER BY c[\"createdAt\"] DESC OFFSET 0 LIMIT 1");
    assertThat(requests.get(3).header("x-ms-documentdb-partitionkey")).isEqualTo("[\"Cart-" + cartId + "-event\"]");
    assertThat(map(requests.get(3).json()).get("query")).isEqualTo("SELECT * FROM c WHERE"
        + " c[\"entityTypeName_entityID_kind\"] = @partitionKey AND c[\"createdAt\"] > @fromTime"
        + " AND NOT IS_DEFINED(c[\"deletedAt\"]) ORDER BY c[\"createdAt\"] ASC");
    assertThat(map(requests.get(5).json()).get("query"))
        .isEqualTo("SELECT * FROM c WHERE c.partitionKey = @partitionKey AND c.sortKey = @sortKey");
    assertThat(requests.get(6).header("x-ms-documentdb-partitionkey")).isEqualTo("[\"Cart-" + cartId + "-snapshot\"]");
    // Then both projections of Cart, concurrently: each looks its read model up by join key and inserts version 1
    for (String readModel : List.of("CartReadModel", "SchemaReadModel")) {
      String docs = "POST " + collections + DATABASE + "-" + readModel + "/docs";
      List<FakeAzureServices.Request> projection = requests.subList(7, requests.size()).stream()
          .filter(request -> request.summary().startsWith(docs)).toList();
      assertThat(projection).extracting(FakeAzureServices.Request::summary)
          .containsExactly(docs + " (query plan)", docs + " (query)", docs);
      assertThat(projection.get(1).json()).isEqualTo(Map.of("query", "SELECT * FROM c WHERE c[\"id\"] IN (@id_0_0)",
          "parameters", List.of(Map.of("name", "@id_0_0", "value", cartId))));
    }
    assertThat(requests).hasSize(7 + 6);
    // Nothing goes back to the stream and no websocket message is sent without subscriptions
    assertThat(services.eventHubMessages(STREAM_TOPIC)).hasSize(1);
    assertThat(services.webPubSubMessages()).isEmpty();
  }

  @Test
  @Order(4)
  @DisplayName("a redelivered stream message is dropped by the events-dedup unique key")
  void redeliveriesAreDeduplicated() {
    consume(streamMessages);

    assertThat(services.requests("cosmos")).extracting(FakeAzureServices.Request::summary)
        .containsExactly("POST /dbs/" + DATABASE + "/colls/" + EVENTS_DEDUP + "/docs");
    assertThat(services.documents(DATABASE, EVENTS_DEDUP)).hasSize(1);
    assertThat(services.documents(DATABASE, DATABASE + "-CartReadModel")).singleElement()
        .satisfies(cart -> assertThat(map(cart.get("boosterMetadata"))).containsEntry("version", 1));
    // The 409 is the expected answer of the service, not a failure of the fake
    assertThat(services.failures()).isEmpty();
  }

  @Test
  @Order(5)
  @DisplayName("the graphql function reads the projected read model back with a point read")
  void queryReadsTheReadModel() {
    FunctionsWorker.Response response = graphql(CART_READ_MODEL, Map.of("cartId", cartId), null);

    assertThat(response.getStatusCode()).isEqualTo(200);
    assertThat(BoosterJson.parse((String) response.getBody())).isEqualTo(Map.of("data", Map.of("CartReadModel", Map.of(
        "id", cartId, "cartItems", List.of(Map.of("productId", productId, "quantity", 2))))));
    assertThat(services.requests("cosmos")).singleElement().satisfies(read -> {
      assertThat(read.summary()).isEqualTo("GET /dbs/" + DATABASE + "/colls/" + DATABASE + "-CartReadModel/docs/" + cartId);
      assertThat(read.header("x-ms-documentdb-partitionkey")).isEqualTo("[\"" + cartId + "\"]");
    });
    assertThat(services.failures()).isEmpty();
  }

  @Nested
  @Order(6)
  @DisplayName("GraphQL subscriptions through Web PubSub")
  class Subscriptions {

    private String connectionContext(String eventType, String eventName) {
      return BoosterJson.toJson(Map.of("connectionId", connectionId, "hub", "booster", "eventType", eventType,
          "eventName", eventName));
    }

    @Test
    @Order(1)
    @DisplayName("the connect function accepts the graphql-ws subprotocol")
    void connect() {
      String request = BoosterJson.toJson(Map.of("connectionContext", map(BoosterJson.parse(connectionContext("system",
          "connect"))), "subprotocols", List.of("graphql-ws")));

      String response = functions.connect(request, null, FunctionsWorker.context("connect"));

      assertThat(response).isNotNull();
      assertThat(BoosterJson.parse(response)).isInstanceOf(Map.class);
      assertThat(map(BoosterJson.parse(response))).doesNotContainKey("code");
      assertThat(services.failures()).isEmpty();
    }

    @Test
    @Order(2)
    @DisplayName("the messages function stores the connection of connection_init and acknowledges it through Web PubSub")
    void connectionInit() {
      functions.messages(BoosterJson.toJson(Map.of("type", "connection_init", "payload", Map.of())),
          connectionContext("user", "message"), FunctionsWorker.context("messages"));

      assertThat(services.documents(DATABASE, CONNECTIONS_STORE)).singleElement()
          .satisfies(connection -> assertThat(connection).containsEntry("connectionID", connectionId));
      assertThat(services.webPubSubMessages()).singleElement().satisfies(message -> {
        assertThat(message.hub()).isEqualTo("booster");
        assertThat(message.connectionId()).isEqualTo(connectionId);
        assertThat(BoosterJson.parse(message.body())).isEqualTo(Map.of("type", "connection_ack"));
      });
      assertThat(services.failures()).isEmpty();
    }

    @Test
    @Order(3)
    @DisplayName("the messages function stores the subscription of a start message in the read model's partition")
    void start() {
      functions.messages(BoosterJson.toJson(Map.of("id", "1", "type", "start", "payload", Map.of(
          "variables", Map.of("cartId", cartId), "extensions", Map.of(), "operationName", "CartReadModel",
          "query", CART_SUBSCRIPTION))), connectionContext("user", "message"), FunctionsWorker.context("messages"));

      assertThat(services.documents(DATABASE, SUBSCRIPTIONS_STORE)).singleElement().satisfies(subscription ->
          assertThat(subscription).containsEntry("className", "CartReadModel")
              .containsEntry("connectionID", connectionId).containsEntry("subscriptionID", "1")
              .containsEntry("connectionID_subscriptionID", connectionId + "-1"));
      assertThat(services.failures()).isEmpty();
    }

    @Test
    @Order(4)
    @DisplayName("the CartReadModel-subscriptions-notifier function pushes the changed read model to the subscriber")
    void notifier() {
      List<Map<String, Object>> changed = services.changeFeed(DATABASE, DATABASE + "-CartReadModel");
      int sent = services.webPubSubMessages().size();

      functions.subscriptionsNotifier(BoosterJson.toJson(changed),
          FunctionsWorker.context("CartReadModel-subscriptions-notifier"));

      List<FakeAzureServices.WebPubSubMessage> messages = services.webPubSubMessages();
      assertThat(messages).hasSize(sent + 1);
      assertThat(BoosterJson.parse(messages.getLast().body())).isEqualTo(Map.of("id", "1", "type", "data",
          "payload", Map.of("data", Map.of("CartReadModel", Map.of("id", cartId, "__typename", "CartReadModel",
              "cartItems", List.of(Map.of("productId", productId, "quantity", 2, "__typename", "CartItem")))))));
      assertThat(services.requests("cosmos")).extracting(FakeAzureServices.Request::summary)
          .contains("POST /dbs/" + DATABASE + "/colls/" + SUBSCRIPTIONS_STORE + "/docs (query)");
      assertThat(services.failures()).isEmpty();
    }

    @Test
    @Order(5)
    @DisplayName("the disconnect function deletes the connection and its subscriptions")
    void disconnect() {
      functions.disconnect(BoosterJson.toJson(Map.of("connectionContext",
          map(BoosterJson.parse(connectionContext("system", "disconnect"))))), null,
          FunctionsWorker.context("disconnect"));

      assertThat(services.documents(DATABASE, CONNECTIONS_STORE)).isEmpty();
      assertThat(services.documents(DATABASE, SUBSCRIPTIONS_STORE)).isEmpty();
      assertThat(services.failures()).isEmpty();
    }
  }

  @Nested
  @Order(7)
  @DisplayName("the Azure test helper (TESTED_PROVIDER=AZURE)")
  class TheAzureTestHelper {

    private AzureProviderTestHelper providerTestHelper;
    private ApplicationTester applicationUnderTest;

    /** {@code getProviderTestHelper()} for AZURE, with {@code az} answering from the fake subscription. */
    @BeforeAll
    void buildHelper() {
      AzCliHelper az = new AzCliHelper(command -> azAnswer(command));
      AzureTestHelper helper = AzureTestHelper.build(APP_NAME, "azure", az,
          Params.Configuration.of("app-id", "app-secret", "tenant-id", "subscription-id", "East US"),
          connectionString -> CosmosRestClient.create(connectionString, CosmosRetryOptions.defaults()));
      providerTestHelper = new AzureProviderTestHelper(helper);
      applicationUnderTest = new ApplicationTester(providerTestHelper);
    }

    private String azAnswer(Command command) {
      if (command.commandLine().equals("az group show --name " + RESOURCE_GROUP)) {
        return "{\"id\":\"/subscriptions/subscription-id/resourceGroups/" + RESOURCE_GROUP + "\",\"location\":\"eastus\","
            + "\"managedBy\":null,\"name\":\"" + RESOURCE_GROUP + "\"}";
      }
      if (command.commandLine().equals("az cosmosdb list-connection-strings --resource-group " + RESOURCE_GROUP
          + " --name " + RESOURCE_GROUP + "cdba")) {
        return BoosterJson.toJson(Map.of("connectionStrings", List.of(Map.of(
            "connectionString", services.cosmosConnectionString(), "description", "Primary SQL Connection String"))));
      }
      throw new RunCommandError(2, "", "unexpected az command " + command.commandLine());
    }

    @Test
    @Order(1)
    @DisplayName("exposes the URLs of the deployed application behind the gateway and Web PubSub")
    void outputs() {
      String base = "http://" + RESOURCE_GROUP + "apis.eastus.cloudapp.azure.com/azure";
      assertThat(providerTestHelper.outputs().graphqlURL()).isEqualTo(base + "/graphql");
      assertThat(providerTestHelper.outputs().healthURL()).isEqualTo(base + "/sensor/health/");
      assertThat(providerTestHelper.outputs().websocketURL())
          .isEqualTo("wss://" + RESOURCE_GROUP + "wps.webpubsub.azure.com:443/client/hubs/booster");
      assertThat(applicationUnderTest.count().readModels("CartReadModel")).isEqualTo(1);
    }

    @Test
    @Order(2)
    @DisplayName("counts and queries what the functions stored, across partitions")
    void countsAndQueries() {
      assertThat(applicationUnderTest.count().events()).isEqualTo(1);
      assertThat(applicationUnderTest.count().subscriptions()).isZero();
      assertThat(applicationUnderTest.count().connections()).isZero();
      List<Map<String, Object>> events = applicationUnderTest.query().events("Cart-" + cartId + "-event");
      assertThat(events).singleElement().satisfies(event -> assertThat(event)
          .containsEntry("typeName", "CartItemChanged").containsEntry("entityID", cartId));
      assertThat(applicationUnderTest.query().readModels(cartId, "CartReadModel")).singleElement()
          .satisfies(cart -> assertThat(cart).containsEntry("id", cartId));
      assertThat(services.failures()).isEmpty();
    }

    @Test
    @Order(3)
    @DisplayName("signs Admin tokens the application accepts: CreateProduct succeeds as after a real deployment")
    void createProductAsAdmin() {
      String token = applicationUnderTest.token().forUser(Faker.internet.email(), "Admin");
      Map<String, Object> variables = new LinkedHashMap<>();
      variables.put("sku", Faker.random.uuid());
      variables.put("displayName", Faker.commerce.product());
      variables.put("description", Faker.commerce.productDescription());
      variables.put("priceInCents", Math.floor(Math.random() * 100 + 1));
      variables.put("currency", Faker.finance.currencyName());

      FunctionsWorker.Response response = graphql(CREATE_PRODUCT, variables, token);

      assertThat(response.getStatusCode()).isEqualTo(200);
      Map<String, Object> body = map(BoosterJson.parse((String) response.getBody()));
      assertThat(body).containsKey("data").doesNotContainKey("errors");
      List<Map<String, Object>> products = new ArrayList<>(services.documents(DATABASE, EVENTS_STORE).stream()
          .filter(document -> "Product".equals(document.get("entityTypeName"))).toList());
      assertThat(products).singleElement().satisfies(event -> {
        assertThat(event).containsEntry("typeName", "ProductCreated");
        assertThat(map(event.get("currentUser"))).containsEntry("roles", List.of("Admin"));
      });
      assertThat(services.failures()).isEmpty();
    }
  }

  // ---------------------------------------------------------------------------------------------------------------

  private FunctionsWorker.Response graphql(String query, Map<String, Object> variables, String token) {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Content-Type", "application/json");
    if (token != null) {
      headers.put("Authorization", "Bearer " + token);
    }
    String body = BoosterJson.toJson(Map.of("query", query, "variables", variables));
    return (FunctionsWorker.Response) functions.graphql(FunctionsWorker.post(GRAPHQL_URL, headers, body),
        FunctionsWorker.context("graphql"));
  }

  private void consume(List<Object> messages) {
    List<String> bodies = new ArrayList<>();
    for (Object message : messages) {
      bodies.add((String) message);
    }
    functions.eventConsumer(bodies, FunctionsWorker.context("eventConsumer"));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    assertThat(value).isInstanceOf(Map.class);
    return (Map<String, Object>) value;
  }
}
