package com.boostercloud.framework.core.services.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.BoosterQueryDispatcher;
import com.boostercloud.framework.core.BoosterReadModelsReader;
import com.boostercloud.framework.core.BoosterTestSupport;
import com.boostercloud.framework.core.decorators.Decorators;
import com.boostercloud.framework.types.Level;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.GraphQLOperation;
import com.boostercloud.framework.types.errors.BoosterError;
import com.boostercloud.framework.types.errors.NotFoundError;
import graphql.ExecutionResult;
import graphql.GraphqlErrorBuilder;
import graphql.language.SourceLocation;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

/** Executes operations on the generated schema: how values are read and written, and the shape of errors. */
class GraphQLExecutionTest extends BoosterTestSupport {

  private MockedConstruction<BoosterReadModelsReader> readModelsReaders;
  private MockedConstruction<BoosterQueryDispatcher> queryDispatchers;
  private GraphQLSchema schema;

  @BeforeEach
  void setUp() {
    GraphQLGenerator.reset();
    Booster.config().logLevel = Level.ERROR;
    Booster.config().logger = Logger.NOOP;
    readModelsReaders = mockConstruction(BoosterReadModelsReader.class);
    queryDispatchers = mockConstruction(BoosterQueryDispatcher.class);
    GraphQLTestApp.CLASSES.forEach(Decorators::registerClass);
    schema = GraphQLGenerator.generateSchema(Booster.config());
  }

  @AfterEach
  void tearDown() {
    readModelsReaders.close();
    queryDispatchers.close();
    GraphQLGenerator.reset();
  }

  private Map<String, Object> run(String query) {
    GraphQLResolverContext context = new GraphQLResolverContext(null, new LinkedHashMap<>(),
        GraphQLOperation.builder().query(query).build(), UUID.of("request-1"), null, true, null, null);
    ExecutionResult result = GraphQLExecution.execute(schema, Parser.parse(query), query, context, null, null);
    return GraphQLExecution.toResultMap(result);
  }

  private static GraphQLTestApp.ProductReadModel product() {
    return new GraphQLTestApp.ProductReadModel(UUID.of("product-1"), "SKU-1", 10, true,
        Instant.parse("2024-01-30T10:15:30Z"), List.of("a", "b"), Optional.of(new GraphQLTestApp.Address("Main", 1)),
        Optional.of("secret"), GraphQLTestApp.Size.LARGE, Map.of("k", List.of(1)), new GraphQLTestApp.Car(4),
        new GraphQLTestApp.Page<>(List.of("x"), "x"), List.of(), Optional.empty());
  }

  @Test
  @SuppressWarnings("unchecked")
  void executesTheSelectionOfASubscriptionForEveryReadModelOfThePubSub() {
    String query = "subscription { ProductReadModels(filter: { sku: { eq: \"SKU-1\" } }) { id sku } }";
    com.boostercloud.framework.core.services.pubsub.ReadModelPubSub pubSub =
        (request, config) -> List.<Object>of(product(), Map.of("id", "product-2", "sku", "SKU-1")).iterator();
    GraphQLResolverContext context = new GraphQLResolverContext("connection-1", new LinkedHashMap<>(),
        GraphQLOperation.builder().query(query).id("operation-1").build(), UUID.of("request-1"), null, false, pubSub,
        null);

    ExecutionResult result = GraphQLExecution.execute(schema, Parser.parse(query), query, context, null, null);

    assertThat(result.getErrors()).isEmpty();
    List<Map<String, Object>> events = new java.util.ArrayList<>();
    ((org.reactivestreams.Publisher<ExecutionResult>) result.getData()).subscribe(
        new org.reactivestreams.Subscriber<>() {
          @Override
          public void onSubscribe(org.reactivestreams.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
          }

          @Override
          public void onNext(ExecutionResult event) {
            events.add(GraphQLExecution.toResultMap(event));
          }

          @Override
          public void onError(Throwable throwable) {
            throw new AssertionError(throwable);
          }

          @Override
          public void onComplete() {}
        });
    org.awaitility.Awaitility.await().until(() -> events.size() == 2);
    assertThat(events).containsExactly(
        Map.of("data", Map.of("ProductReadModels", Map.of("id", "product-1", "sku", "SKU-1"))),
        Map.of("data", Map.of("ProductReadModels", Map.of("id", "product-2", "sku", "SKU-1"))));
    org.mockito.Mockito.verify(readModelsReaders.constructed().getFirst(), org.mockito.Mockito.never())
        .subscribe(any(), any(), any());
  }

  @Test
  void readsTheValuesOfReadModelInstances() {
    when(readModelsReaders.constructed().getFirst().findById(any())).thenReturn(product());

    Map<String, Object> result = run("""
        { ProductReadModel(id: "product-1") {
            id sku price available createdAt tags address { street zip } size metadata page addresses
            category { name } priceWithTaxes
            vehicle { __typename ... on Car { wheels } ... on Bike { brand } } } }
        """);

    Map<String, Object> expected = new LinkedHashMap<>();
    expected.put("id", "product-1");
    expected.put("sku", "SKU-1");
    expected.put("price", 10.0);
    expected.put("available", true);
    expected.put("createdAt", "2024-01-30T10:15:30.000Z");
    expected.put("tags", List.of("a", "b"));
    expected.put("address", Map.of("street", "Main", "zip", 1.0));
    expected.put("size", "LARGE");
    expected.put("metadata", Map.of("k", List.of(1)));
    expected.put("page", Map.of("items", List.of("x"), "first", "x"));
    expected.put("addresses", List.of());
    expected.put("category", null);
    expected.put("priceWithTaxes", 10 * 1.21);
    expected.put("vehicle", Map.of("__typename", "Car", "wheels", 4.0));
    assertThat(result).isEqualTo(Map.of("data", Map.of("ProductReadModel", expected)));
  }

  /** Read model with an asynchronous calculated field (TypeScript {@code get x(): Promise<number>}). */
  @com.boostercloud.framework.core.decorators.ReadModel(authorize = com.boostercloud.framework.core.decorators.All.class)
  public record AsyncPriceReadModel(UUID id, double price) {
    @com.boostercloud.framework.core.decorators.CalculatedField(dependsOn = {"price"})
    public java.util.concurrent.CompletableFuture<Double> doublePrice() {
      return java.util.concurrent.CompletableFuture.supplyAsync(() -> price * 2,
          java.util.concurrent.CompletableFuture.delayedExecutor(50, java.util.concurrent.TimeUnit.MILLISECONDS));
    }
  }

  @Test
  void waitsForAsynchronousCalculatedFieldsAndTypesThemWithTheirResult() {
    GraphQLGenerator.reset();
    Decorators.registerClass(AsyncPriceReadModel.class);
    schema = GraphQLGenerator.generateSchema(Booster.config());
    when(readModelsReaders.constructed().getLast().findById(any()))
        .thenReturn(new AsyncPriceReadModel(UUID.of("p-1"), 21));

    Map<String, Object> result = run("{ AsyncPriceReadModel(id: \"p-1\") { id doublePrice } }");

    assertThat(graphql.schema.GraphQLTypeUtil.simplePrint(
        schema.getObjectType("AsyncPriceReadModel").getFieldDefinition("doublePrice").getType())).isEqualTo("Float!");
    assertThat(result).isEqualTo(Map.of("data", Map.of("AsyncPriceReadModel", Map.of("id", "p-1", "doublePrice", 42.0))));
  }

  @Test
  void readsTheValuesOfRawDocuments() {
    when(readModelsReaders.constructed().getFirst().findById(any())).thenReturn(Map.of("id", "product-1",
        "size", "SMALL", "createdAt", "2024-01-30T10:15:30Z", "address", Map.of("street", "Main", "zip", 2)));

    Map<String, Object> result = run("{ ProductReadModel(id: \"product-1\") { id size createdAt address { zip } } }");

    assertThat(result).isEqualTo(Map.of("data", Map.of("ProductReadModel", Map.of("id", "product-1", "size", "SMALL",
        "createdAt", "2024-01-30T10:15:30.000Z", "address", Map.of("zip", 2.0)))));
  }

  @Test
  void readsTheFieldsOfClasses() {
    when(queryDispatchers.constructed().getFirst().dispatchQuery(any(), any()))
        .thenReturn(List.of(new GraphQLTestApp.Address("Main", 3)));

    Map<String, Object> result = run("{ FindProducts(input: {}) { street zip } }");

    assertThat(result).isEqualTo(Map.of("data", Map.of("FindProducts", List.of(Map.of("street", "Main", "zip", 3.0)))));
  }

  @Test
  void reportsResolverErrorsWithTheirMessageLocationPathAndCode() {
    when(readModelsReaders.constructed().getFirst().findById(any())).thenThrow(new NotFoundError("Not found"));

    Map<String, Object> result = run("{ ProductReadModel(id: \"x\") { id } }");

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("ProductReadModel", null);
    assertThat(result).isEqualTo(Map.of(
        "errors", List.of(Map.of("message", "Not found", "locations", List.of(Map.of("line", 1, "column", 3)),
            "path", List.of("ProductReadModel"), "extensions", Map.of("code", "NotFoundError"))),
        "data", data));
    assertThat(result.keySet()).containsExactly("errors", "data");
  }

  @Test
  void validatesWithTheRulesOfTheSpecificationOnly() {
    String nestedIntrospection = """
        { __type(name: "ProductReadModel") { name fields { name type { name fields { name } inputFields { name }
          ofType { fields { name type { fields { name } } } } } } } }""";

    assertThat(GraphQLExecution.validate(schema, Parser.parse(nestedIntrospection))).isEmpty();
    assertThat(GraphQLExecution.validate(schema, Parser.parse("{ nope }")))
        .extracting(graphql.validation.ValidationError::getMessage)
        .singleElement().asString().contains("'nope'");
  }

  @Test
  void formatsErrorsLikeGraphqlJs() {
    BoosterError withData = new BoosterError("Cart is full", "CART_FULL", Map.of("max", 10));

    assertThat(GraphQLExecution.formatError(withData, true)).isEqualTo(Map.of("message", "Cart is full",
        "extensions", Map.of("code", "CART_FULL", "data", Map.of("max", 10))));
    assertThat(GraphQLExecution.formatError(new IllegalStateException("plain"), true))
        .isEqualTo(Map.of("message", "plain", "extensions", Map.of()));
    assertThat(GraphQLExecution.formatError(new IllegalStateException("plain"), false))
        .isEqualTo(Map.of("message", "plain"));
    assertThat(GraphQLExecution.formatError(new IllegalStateException(), true))
        .isEqualTo(Map.of("message", "", "extensions", Map.of()));
    assertThat(GraphQLExecution.formatError(GraphqlErrorBuilder.newError().message("located")
        .location(new SourceLocation(2, 5)).path(List.of("a", 0)).build(), true))
        .isEqualTo(Map.of("message", "located", "locations", List.of(Map.of("line", 2, "column", 5)),
            "path", List.of("a", 0), "extensions", Map.of()));
    assertThat(GraphQLExecution.formatError(new GraphQLExecution.LocatedError("wrapped", List.of(), List.of(),
        new NotFoundError("wrapped")), true))
        .isEqualTo(Map.of("message", "wrapped", "extensions", Map.of("code", "NotFoundError")));
  }
}
