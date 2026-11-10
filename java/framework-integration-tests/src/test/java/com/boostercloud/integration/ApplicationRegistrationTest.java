package com.boostercloud.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.services.graphql.GraphQLGenerator;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.framework.types.concepts.CommandMetadata;
import com.boostercloud.framework.types.concepts.DataMigrationMetadata;
import com.boostercloud.framework.types.envelope.CommandEnvelope;
import com.boostercloud.framework.types.injectable.InjectableCommand;
import com.boostercloud.framework.types.sensor.HealthIndicatorConfigurationBase;
import com.boostercloud.integration.common.AppErrorHandler;
import com.boostercloud.integration.common.CustomLogger;
import com.boostercloud.integration.common.JsonStringify;
import com.boostercloud.integration.config.CustomPublicKeyTokenVerifier;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphQLUnionType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Boots the application in this JVM ({@code Booster.start(Main.class)} with {@code BOOSTER_ENV=local}; no provider is
 * started) and checks that every concept of the TypeScript app is registered and that the GraphQL schema has the type
 * and field names the TypeScript documents use. A plain {@code mvn verify} thus catches registration errors and schema
 * drift without running the end-to-end suites. The orders pinned for {@code SchemaReadModel} are those of the
 * TypeScript {@code functionality/schema.*.integration.ts} suites (spec 16 §5.3).
 */
class ApplicationRegistrationTest {

  /**
   * The element type of every list of strings in the output types. graphql-js and graphql-java need one type per name,
   * so Booster caches the list types by name ({@code StringList}) and the first property that creates it decides
   * whether its elements are nullable for every other property (faithful to TypeScript). TypeScript registers the
   * read models in import order, so its first {@code StringList} is {@code CartReadModel.cartItemsIds?} ({@code
   * [String]}); Java registers them in class-path order, so it is {@code CarOfferReadModel.purchasesIds}
   * ({@code [String!]}). See the README, "Differences from TypeScript".
   */
  private static final String STRING_LIST = "[String!]";

  /**
   * {@code JSON.stringify(commandMetadata)} of {@code ChangeCartItem}, part of the error messages of the global error
   * handler (pinned by the TypeScript {@code functionality/errors.integration.ts}). The shape and key order are
   * TypeScript's ({@code before} with one {@code null} per hook function, then {@code properties} including the
   * non-exposed {@code test}, then {@code methods}); the type metadata is the Java one: {@code importPath} is the Java
   * package of {@code UUID} and the hooks and the handler return {@code Record<string, unknown>} and {@code void}
   * (TypeScript: {@code @boostercloud/framework-types}, {@code Promise<CommandInput>} and {@code Promise<void>}).
   */
  static final String CHANGE_CART_ITEM_METADATA_JSON = """
      {"before":[null,null],"properties":[{"name":"cartId","typeInfo":{"name":"UUID","typeGroup":"Class","isNullable":false,\
      "isGetAccessor":false,"parameters":[],"importPath":"com.boostercloud.framework.types.concepts","typeName":"UUID"}},\
      {"name":"productId","typeInfo":{"name":"UUID","typeGroup":"Class","isNullable":false,"isGetAccessor":false,\
      "parameters":[],"importPath":"com.boostercloud.framework.types.concepts","typeName":"UUID"}},{"name":"quantity",\
      "typeInfo":{"name":"number","typeGroup":"Number","isNullable":false,"isGetAccessor":false,"parameters":[],\
      "typeName":"Number"}},{"name":"test","typeInfo":{"name":"number","typeGroup":"Number","isNullable":false,\
      "isGetAccessor":false,"parameters":[],"typeName":"Number"}}],"methods":[{"name":"beforeFn","typeInfo":{"name":"Record<string,\
       unknown>","typeGroup":"Type","isNullable":false,"isGetAccessor":false,"parameters":[{"name":"string",\
      "typeGroup":"String","isNullable":false,"isGetAccessor":false,"parameters":[],"typeName":"String"},{"name":"unknown",\
      "typeGroup":"Other","isNullable":false,"isGetAccessor":false,"parameters":[]}]}},{"name":"beforeFnV2",\
      "typeInfo":{"name":"Record<string, unknown>","typeGroup":"Type","isNullable":false,"isGetAccessor":false,\
      "parameters":[{"name":"string","typeGroup":"String","isNullable":false,"isGetAccessor":false,"parameters":[],\
      "typeName":"String"},{"name":"unknown","typeGroup":"Other","isNullable":false,"isGetAccessor":false,"parameters":[]}]}},\
      {"name":"handle","typeInfo":{"name":"void","typeGroup":"Other","isNullable":false,"isGetAccessor":false,\
      "parameters":[]}}]}""";

  private static BoosterConfig config;
  private static GraphQLSchema schema;

  @BeforeAll
  static void boot() {
    Booster.reset();
    EnvironmentVariables.clearOverrides();
    System.setProperty(EnvironmentVariables.BOOSTER_ENV_PROPERTY, "local");
    Booster.start(Main.class);
    config = Booster.config();
    schema = GraphQLGenerator.generateSchema(config);
  }

  @AfterAll
  static void reset() {
    Booster.reset();
    System.clearProperty(EnvironmentVariables.BOOSTER_ENV_PROPERTY);
  }

  @Test
  void registersEveryCommandQueryEventAndNotification() {
    assertThat(config.commandHandlers.keySet()).containsExactlyInAnyOrder("AbandonCart", "AddBook", "AddCarModel",
        "AddCarOwner", "AddConcurrency", "AddMovie", "AddPurchaseOffer", "BuyCar", "CartMyAddress",
        "CartShippingAddress", "ChangeCartItem", "ChangeMultipleCartItems", "ChangeMultipleCartItemsWithIds",
        "ConfirmPayment", "CreatePack", "CreateProduct", "DeleteProduct", "EmptyCommand", "EntitiesIdsFinder",
        "FlushEvents", "FlushNotifications", "GetConcurrency", "HardDelete", "IncrementAnotherCounter",
        "IncrementCounter", "LogEvent", "LogSomething", "MigrateAllReadModel", "MigrateCommand", "MoveStock",
        "TouchCommand", "UpdateCarModelName", "UpdateCarOwnerName", "UpdateProduct", "UpdateShippingAddress");
    assertThat(config.queryHandlers.keySet())
        .containsExactlyInAnyOrder("CartTotalQuantity", "CartWithRole", "CartsByCountry", "SearchMedia");
    assertThat(config.events.keySet()).contains("AnotherCounterAdded", "BookAdded", "CarModelAdded",
        "CarModelNameUpdated", "CarOfferAdded", "CarOwnerAdded", "CarOwnerNameUpdated", "CarPurchased", "CartChecked",
        "CartItemChanged", "CartPaid", "ConcurrencyPersisted", "CounterAdded", "ErrorEvent", "LogEventReceived",
        "LogEventReceivedTest", "MovieAdded", "OrderCreated", "PackCreated", "ProductAvailabilityChanged",
        "ProductCreated", "ProductDeleted", "ProductUpdated", "ShippingAddressUpdated", "StockMoved");
    assertThat(config.notifications.keySet()).containsExactly("CartAbandoned");
    assertThat(config.partitionKeys).containsEntry("CartAbandoned", "something");
  }

  @Test
  void registersEveryEntityReadModelAndProjection() {
    assertThat(config.entities.keySet()).containsExactlyInAnyOrder("AnotherCounter", "Book", "CarModel",
        "CarOffers", "CarOwner", "CarPurchase", "Cart", "Concurrency", "Counter", "LogEvent", "LogEventTest",
        "MigratedCart", "Movie", "Order", "Pack", "Payment", "Product", "Stock", "BoosterDataMigrationEntity");
    assertThat(config.readModels.keySet()).containsExactlyInAnyOrder("AnotherCounterReadModel", "BookReadModel",
        "CarModelReadModel", "CarOfferReadModel", "CarOwnerReadModel", "CarPurchasesReadModel", "CartReadModel",
        "ConcurrencyReadModel", "CounterReadModel", "DataMigrationsReadModel", "LogEventReceivedTestReadModel",
        "MovieReadModel", "OtherConcurrencyReadModel", "ProductReadModel", "ProductUpdatesReadModel",
        "ProductsBySKU", "SchemaReadModel", "SpecialReportsReadModel", "StockReadModel");
    assertThat(config.projections.get("Cart")).extracting(projection -> projection.type().getSimpleName())
        .containsExactlyInAnyOrder("CartReadModel", "SchemaReadModel");
    assertThat(config.projections.get("MigratedCart")).hasSize(1);
    assertThat(config.projections.get("Product")).extracting(projection -> projection.type().getSimpleName())
        .containsExactlyInAnyOrder("ProductReadModel", "ProductUpdatesReadModel", "ProductsBySKU",
            "SpecialReportsReadModel");
    assertThat(config.projections.get("Concurrency")).hasSize(3);
    assertThat(config.projections.get("CarOwner")).hasSize(2);
    assertThat(config.projections.get("CarModel")).hasSize(2);
    assertThat(config.unProjections.get("Product")).hasSize(4);
    assertThat(config.unProjections.get("Pack")).hasSize(1);
    // MigratedCart declares no reducer: the inherited static reducers are registered for Cart only, as in TypeScript
    assertThat(config.reducers.values()).extracting(reducer -> reducer.type().getSimpleName())
        .doesNotContain("MigratedCart");
  }

  @Test
  void registersTheHandlersRolesMigrationsAndSensors() {
    assertThat(config.eventHandlers.keySet()).contains("StockMoved", "CartAbandoned");
    assertThat(config.scheduledCommandHandlers.keySet()).containsExactly("CheckCartCount");
    assertThat(config.scheduledCommandHandlers.get("CheckCartCount").scheduledOn().minute()).isEqualTo("0/1");
    assertThat(config.roles.keySet()).containsExactlyInAnyOrder("Admin", "UserWithEmail", "UserWithPhone",
        "SuperUser", "SuperUserNoConfirmation");
    assertThat(config.dataMigrationHandlers.values().stream()
        .sorted(Comparator.comparingInt(migration -> migration.migrationOptions().order()))
        .map(DataMigrationMetadata::type)
        .map(Class::getSimpleName))
        .containsExactly("CartIdDataMigrateV1", "CartIdDataMigrateV2");
    Map<String, Integer> versions = new LinkedHashMap<>();
    for (String concept : List.of("UpdateProduct", "Product", "ProductUpdated", "CartReadModel", "ProductReadModel",
        "ChangeCartItem", "Cart", "CartItemChanged", "BookReadModel")) {
      versions.put(concept, config.currentVersionFor(concept));
    }
    assertThat(versions).containsExactly(Map.entry("UpdateProduct", 2), Map.entry("Product", 2),
        Map.entry("ProductUpdated", 4), Map.entry("CartReadModel", 2), Map.entry("ProductReadModel", 2),
        Map.entry("ChangeCartItem", 1), Map.entry("Cart", 1), Map.entry("CartItemChanged", 1),
        Map.entry("BookReadModel", 1));
    assertThat(config.globalErrorsHandler.type()).isEqualTo(AppErrorHandler.class);
    assertThat(config.userHealthIndicators.keySet()).contains("myApplication", "myApplication/child",
        "booster/database/myApplication", "booster/database/myApplication2");
    assertThat(config.userHealthIndicators.get("myApplication").healthIndicatorConfiguration().name)
        .isEqualTo("my-application");
    assertThat(config.userHealthIndicators.get("myApplication/child").healthIndicatorConfiguration().name)
        .isEqualTo("My Application child");
  }

  @Test
  void configuresTheLocalEnvironmentLikeTheTypeScriptConfig() {
    assertThat(config.appName).isEqualTo("my-store");
    assertThat(config.providerPackage).isEqualTo("com.boostercloud.framework.provider.local");
    assertThat(config.tokenVerifiers).hasSize(2);
    assertThat(config.tokenVerifiers.get(1)).isInstanceOf(CustomPublicKeyTokenVerifier.class);
    assertThat(config.enableSubscriptions).isTrue();
    assertThat(config.eventStreamConfiguration.enabled()).isTrue();
    assertThat(config.logger).isInstanceOf(CustomLogger.class);
    assertThat(config.traceConfiguration.enableTraceNotification)
        .isEqualTo(List.of("COMMAND_HANDLER", "CHANGE_CART_ITEM_HANDLER"));
    assertThat(config.traceConfiguration.includeInternal).isFalse();
    assertThat(config.sensorConfiguration.health.booster.values())
        .allMatch((HealthIndicatorConfigurationBase indicator) -> indicator.enabled);
    assertThat(config.injectable.commands()).extracting(InjectableCommand::name).containsExactly("test");
    assertThat(config.rockets == null || config.rockets.isEmpty()).as("no rockets").isTrue();
  }

  @Test
  void theErrorHandlerMessagesCarryTheCommandMetadataAsJson() {
    CommandMetadata metadata = config.commandHandlers.get("ChangeCartItem");
    CommandEnvelope envelope = CommandEnvelope.builder().typeName("ChangeCartItem").version(1)
        .value(Map.of("cartId", Constants.COMMAND_HANDLER_ERROR_CART_ID)).build();

    assertThat(JsonStringify.stringify(metadata)).isEqualTo(CHANGE_CART_ITEM_METADATA_JSON);
    Throwable replaced = AppErrorHandler.onCommandHandlerError(
        new IllegalStateException(Constants.COMMAND_HANDLER_ERROR_CART_MESSAGE), envelope, metadata);
    assertThat(AppErrorHandler.onError(replaced)).hasMessage(Constants.COMMAND_HANDLER_ERROR_CART_MESSAGE
        + "-onCommandHandlerError with metadata: " + CHANGE_CART_ITEM_METADATA_JSON + "-onError");
  }

  @Test
  void exposesTheCommandsWithTheTypeScriptInputTypes() {
    assertThat(inputFields("ChangeCartItemInput"))
        .containsExactly(Map.entry("cartId", "ID!"), Map.entry("productId", "ID!"), Map.entry("quantity", "Float!"));
    assertThat(inputFields("CreateProductInput")).containsExactly(Map.entry("sku", "String!"),
        Map.entry("displayName", "String!"), Map.entry("description", "String!"),
        Map.entry("priceInCents", "Float!"), Map.entry("currency", "String!"), Map.entry("productID", "ID"),
        Map.entry("productDetails", "JSON"), Map.entry("productType", "JSON"));
    assertThat(inputFields("CreatePackInput"))
        .containsExactly(Map.entry("name", "String!"), Map.entry("products", "[ID!]!"), Map.entry("packID", "ID"));
    assertThat(inputFields("MoveStockInput")).containsExactly(Map.entry("productID", "String!"),
        Map.entry("origin", "String!"), Map.entry("destination", "String!"), Map.entry("quantity", "Float!"));
    assertThat(inputFields("UpdateShippingAddressInput"))
        .containsExactly(Map.entry("cartId", "ID!"), Map.entry("address", "AddressInput!"));
    assertThat(inputFields("ChangeMultipleCartItemsInput"))
        .containsExactly(Map.entry("cartId", "ID!"), Map.entry("itemsCount", "Float!"));
    assertThat(inputFields("ChangeMultipleCartItemsWithIdsInput")).containsExactly(Map.entry("items", "[JSON!]!"));
    assertThat(inputFields("HardDeleteInput")).containsExactly(Map.entry("entityId", "String!"),
        Map.entry("entityTypeName", "String!"), Map.entry("createdAt", "String!"));
    assertThat(inputFields("CartShippingAddressInput"))
        .containsExactly(Map.entry("cartId", "ID!"), Map.entry("paginatedVersion", "Boolean!"));
    assertThat(inputFields("FlushEventsInput")).containsExactly(Map.entry("cartId", "ID!"),
        Map.entry("previousProducts", "Float!"), Map.entry("afterProducts", "Float!"));
    // @NonExposed multiply is not part of the input
    assertThat(inputFields("CartTotalQuantityInput")).containsExactly(Map.entry("cartId", "ID!"));

    GraphQLObjectType mutation = schema.getMutationType();
    assertThat(mutation.getFieldDefinition("EmptyCommand").getArguments()).isEmpty();
    assertThat(type(mutation.getFieldDefinition("EmptyCommand"))).isEqualTo("String!");
    assertThat(type(mutation.getFieldDefinition("MigrateAllReadModel"))).isEqualTo("String!");
    assertThat(type(mutation.getFieldDefinition("HardDelete"))).isEqualTo("Boolean!");
    assertThat(type(mutation.getFieldDefinition("FlushEvents"))).isEqualTo("[Cart!]!");
  }

  @Test
  void exposesTheReadModelsAndQueriesWithTheTypeScriptNames() {
    GraphQLObjectType query = schema.getQueryType();
    for (String readModel : List.of("CartReadModel", "ProductReadModel", "ProductsBySKU", "DataMigrationsReadModel",
        "SchemaReadModel", "SpecialReportsReadModel", "CarPurchasesReadModel")) {
      assertThat(query.getFieldDefinition(readModel)).as(readModel).isNotNull();
      assertThat(query.getFieldDefinition("List" + readModel + "s")).as("List" + readModel + "s").isNotNull();
    }
    assertThat(query.getFieldDefinition("CartReadModels")).isNotNull();
    assertThat(type(query.getFieldDefinition("CartTotalQuantity"))).isEqualTo("Float!");
    assertThat(type(query.getFieldDefinition("SearchMedia"))).isEqualTo("SearchResult!");
    assertThat(((GraphQLUnionType) schema.getType("MediaValue")).getTypes())
        .extracting(member -> ((GraphQLObjectType) member).getName())
        .containsExactlyInAnyOrder("BookReadModel", "MovieReadModel");
    assertThat(fields("CartReadModel")).containsExactly(Map.entry("id", "ID!"),
        Map.entry("cartItems", "[CartItem!]!"), Map.entry("checks", "Float!"),
        Map.entry("shippingAddress", "Address"), Map.entry("payment", "Payment"),
        // [String] in TypeScript, see STRING_LIST
        Map.entry("cartItemsIds", STRING_LIST), Map.entry("cartItemsSize", "Float"),
        Map.entry("myAddress", "Address!"), Map.entry("lastProduct", "ProductReadModel"));
    assertThat(fields("CartItem")).containsExactly(Map.entry("productId", "ID!"), Map.entry("quantity", "Float!"));
    assertThat(fields("Payment")).containsExactly(Map.entry("id", "ID!"), Map.entry("cartId", "ID!"),
        Map.entry("confirmationToken", "String!"));
    assertThat(fields("Money")).containsExactly(Map.entry("cents", "Float!"), Map.entry("currency", "String!"));
    assertThat(fields("Pack"))
        .containsExactly(Map.entry("id", "ID!"), Map.entry("name", "String!"), Map.entry("products", "[ID!]!"));
    assertThat(fields("ProductReadModel").keySet()).containsExactly("id", "sku", "displayName", "description",
        "availability", "deleted", "productDetails", "productType", "price", "packs");
  }

  @Test
  void mapsTheSchemaReadModelTypesInTheTypeScriptOrder() {
    assertThat(fields("SchemaReadModel")).containsExactly(
        Map.entry("readOnlyProperty", "String"), Map.entry("privateProperty", "String"),
        Map.entry("publicProperty", "String"), Map.entry("id", "ID!"), Map.entry("date", "Date!"),
        Map.entry("array0", "[String!]!"), Map.entry("array1", "[String!]!"), Map.entry("unionArrays", "JSON!"),
        Map.entry("unionWithNull", "String"), Map.entry("unionWithUndefined", "String"),
        Map.entry("unionWithAny", "JSON!"), Map.entry("unionWithObject", "JSON!"),
        Map.entry("unionWithUnknown", "JSON!"), Map.entry("func0", "JSON!"), Map.entry("any0", "JSON!"),
        Map.entry("unknown0", "JSON!"), Map.entry("record", "JSON!"), Map.entry("generic", "JSON!"),
        Map.entry("child", "BaseClass!"), Map.entry("optionalString", "String"), Map.entry("optionalNull", "String"),
        Map.entry("optionalUndefined", "JSON"), Map.entry("optionalUnknown", "JSON"),
        Map.entry("optionalAny", "JSON"), Map.entry("optionalRecord", "JSON"),
        Map.entry("optionalGeneric", "JSON"), Map.entry("optionalChild", "BaseClass"),
        Map.entry("readonlyArray", STRING_LIST));

    List<String> names = List.copyOf(fields("SchemaReadModel").keySet());
    List<String> filterNames = new ArrayList<>(names);
    filterNames.addAll(List.of("and", "or", "not"));
    Map<String, String> filter = inputFields("ListSchemaReadModelFilter");
    assertThat(filter.keySet()).containsExactlyElementsOf(filterNames);
    assertThat(filter).contains(Map.entry("readOnlyProperty", "StringPropertyFilter"),
        Map.entry("id", "UUIDPropertyFilter"), Map.entry("date", "DatePropertyFilter"),
        Map.entry("array0", "StringArrayPropertyFilter"), Map.entry("unionArrays", "JSON"),
        Map.entry("generic", "GenericClass_string_PropertyFilter"),
        Map.entry("optionalGeneric", "GenericClass_Cart_PropertyFilter"),
        Map.entry("child", "BaseClassPropertyFilter"), Map.entry("optionalChild", "BaseClassPropertyFilter"),
        // JSON in TypeScript: its filter builder only gives array filters to `Array`, not to `ReadonlyArray`, and Java
        // has no read-only list type (the metadata never produces ReadonlyArray); see the README
        Map.entry("readonlyArray", "StringArrayPropertyFilter"));
    assertThat(inputFields("BaseClassPropertyFilter")).containsEntry("base", "StringPropertyFilter");
    assertThat(inputFields("GenericClass_string_PropertyFilter")).containsEntry("genericValue", "JSON");

    Map<String, String> sortBy = inputFields("SchemaReadModelSortBy");
    assertThat(sortBy.keySet()).containsExactlyElementsOf(names);
    sortBy.forEach((name, sortType) -> assertThat(sortType).as(name).isEqualTo(
        name.equals("child") || name.equals("optionalChild") ? "BaseClassPropertySortBy" : "orderProperty"));
  }

  private static String type(GraphQLFieldDefinition field) {
    return GraphQLTypeUtil.simplePrint(field.getType());
  }

  private static Map<String, String> fields(String objectType) {
    Map<String, String> result = new LinkedHashMap<>();
    for (GraphQLFieldDefinition field : ((GraphQLObjectType) schema.getType(objectType)).getFieldDefinitions()) {
      result.put(field.getName(), type(field));
    }
    return result;
  }

  private static Map<String, String> inputFields(String inputType) {
    Map<String, String> result = new LinkedHashMap<>();
    for (GraphQLInputObjectField field : ((GraphQLInputObjectType) schema.getType(inputType)).getFieldDefinitions()) {
      result.put(field.getName(), GraphQLTypeUtil.simplePrint(field.getType()));
    }
    return result;
  }
}
