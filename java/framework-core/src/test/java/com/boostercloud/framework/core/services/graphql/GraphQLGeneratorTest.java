package com.boostercloud.framework.core.services.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.BoosterCommandDispatcher;
import com.boostercloud.framework.core.BoosterEventsReader;
import com.boostercloud.framework.core.BoosterQueryDispatcher;
import com.boostercloud.framework.core.BoosterReadModelsReader;
import com.boostercloud.framework.core.BoosterTestSupport;
import com.boostercloud.framework.core.decorators.Decorators;
import com.boostercloud.framework.core.services.pubsub.ReadModelPubSub;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Level;
import com.boostercloud.framework.types.concepts.EntityMetadata;
import com.boostercloud.framework.types.concepts.ReducerMetadata;
import com.boostercloud.framework.types.concepts.SequenceKey;
import com.boostercloud.framework.types.concepts.TimeKey;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.CommandEnvelope;
import com.boostercloud.framework.types.envelope.ContextEnvelope;
import com.boostercloud.framework.types.envelope.EventParametersFilterByEntity;
import com.boostercloud.framework.types.envelope.EventSearchRequest;
import com.boostercloud.framework.types.envelope.EventSearchResponse;
import com.boostercloud.framework.types.envelope.GraphQLOperation;
import com.boostercloud.framework.types.envelope.QueryEnvelope;
import com.boostercloud.framework.types.envelope.ReadModelListResult;
import com.boostercloud.framework.types.envelope.ReadModelRequestEnvelope;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import graphql.ExecutionResult;
import graphql.Scalars;
import graphql.parser.Parser;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

/** Port of {@code test/services/graphql/graphql-generator.test.ts}. */
class GraphQLGeneratorTest extends BoosterTestSupport {

  private BoosterConfig mockConfig;

  @BeforeEach
  void setUp() {
    GraphQLGenerator.reset();
    mockConfig = new BoosterConfig("test");
    mockConfig.logLevel = Level.ERROR;
    mockConfig.entities.put("Cart", new EntityMetadata(Object.class, null));
    mockConfig.reducers.put("CartCreated", new ReducerMetadata(Object.class, "created"));
  }

  @AfterEach
  void tearDown() {
    GraphQLGenerator.reset();
  }

  private static GeneratedObjectType objectType(String name) {
    Map<String, GraphQLFieldConfig> fields = new LinkedHashMap<>();
    fields.put("field", GraphQLFieldConfig.of(Scalars.GraphQLString, null, null));
    return GeneratedObjectType.of(name, fields);
  }

  @Nested
  class GenerateSchema {

    private MockedConstruction<GraphQLQueryGenerator> queryGenerators;
    private MockedConstruction<GraphQLMutationGenerator> mutationGenerators;
    private MockedConstruction<GraphQLSubscriptionGenerator> subscriptionGenerators;
    private final GeneratedObjectType query = objectType("AQuery");
    private final GeneratedObjectType mutation = objectType("AMutation");
    private final GeneratedObjectType subscription = objectType("ASubscription");

    @BeforeEach
    void stubGenerators() {
      queryGenerators = mockConstruction(GraphQLQueryGenerator.class,
          (generator, context) -> when(generator.generate()).thenReturn(query));
      mutationGenerators = mockConstruction(GraphQLMutationGenerator.class,
          (generator, context) -> when(generator.generate()).thenReturn(java.util.Optional.of(mutation)));
      subscriptionGenerators = mockConstruction(GraphQLSubscriptionGenerator.class,
          (generator, context) -> when(generator.generate()).thenReturn(java.util.Optional.of(subscription)));
    }

    @AfterEach
    void closeGenerators() {
      queryGenerators.close();
      mutationGenerators.close();
      subscriptionGenerators.close();
    }

    @Test
    void callsTheQueryGenerator() {
      GraphQLGenerator.generateSchema(mockConfig);

      verify(queryGenerators.constructed().getFirst(), times(1)).generate();
    }

    @Test
    void callsTheMutationGenerator() {
      GraphQLGenerator.generateSchema(mockConfig);

      verify(mutationGenerators.constructed().getFirst(), times(1)).generate();
    }

    @Test
    void callsTheSubscriptionGenerator() {
      GraphQLGenerator.generateSchema(mockConfig);

      verify(subscriptionGenerators.constructed().getFirst(), times(1)).generate();
    }

    @Test
    void returnsAGraphQLSchemaWithTheGeneratedTypes() {
      GraphQLSchema result = GraphQLGenerator.generateSchema(mockConfig);

      assertThat(result.getQueryType().getName()).isEqualTo("AQuery");
      assertThat(result.getMutationType().getName()).isEqualTo("AMutation");
      assertThat(result.getSubscriptionType().getName()).isEqualTo("ASubscription");
    }

    @Test
    void generatesTheSchemaOnlyOnce() {
      GraphQLSchema first = GraphQLGenerator.generateSchema(mockConfig);

      assertThat(GraphQLGenerator.generateSchema(mockConfig)).isSameAs(first);
      assertThat(queryGenerators.constructed()).hasSize(1);
    }
  }

  @Nested
  class Resolvers {

    private MockedConstruction<BoosterReadModelsReader> readModelsReaders;
    private MockedConstruction<BoosterCommandDispatcher> commandDispatchers;
    private MockedConstruction<BoosterQueryDispatcher> queryDispatchers;
    private MockedConstruction<BoosterEventsReader> eventsReaders;
    private final UserEnvelope user = UserEnvelope.builder().username("user@example.com").roles(List.of("Admin"))
        .claims(Map.of()).build();
    private final UUID requestId = UUID.of("request-1");
    private final ReadModelPubSub pubSub = mock(ReadModelPubSub.class);
    private final ContextEnvelope requestContext = ContextEnvelope.of(Map.of("authorization", "Bearer 123"),
        Map.of("query", "Test query"), new Object());
    private GraphQLResolverContext mockResolverContext;

    @BeforeEach
    void setUpCollaborators() {
      readModelsReaders = mockConstruction(BoosterReadModelsReader.class);
      commandDispatchers = mockConstruction(BoosterCommandDispatcher.class);
      queryDispatchers = mockConstruction(BoosterQueryDispatcher.class);
      eventsReaders = mockConstruction(BoosterEventsReader.class);
      GraphQLGenerator.generateSchema(mockConfig);
      mockResolverContext = context(null, false);
    }

    @AfterEach
    void closeCollaborators() {
      readModelsReaders.close();
      commandDispatchers.close();
      queryDispatchers.close();
      eventsReaders.close();
    }

    private GraphQLResolverContext context(String connectionID, boolean storeSubscriptions) {
      return new GraphQLResolverContext(connectionID, new LinkedHashMap<>(),
          GraphQLOperation.builder().query("query { a }").build(), requestId, user, storeSubscriptions, pubSub,
          requestContext);
    }

    private BoosterReadModelsReader readModelsReader() {
      return readModelsReaders.constructed().getFirst();
    }

    private ReadModelRequestEnvelope expectedSearchEnvelope(Class<?> readModelClass) {
      return ReadModelRequestEnvelope.builder()
          .currentUser(user)
          .filters(Map.of())
          .sortBy(Map.of())
          .requestID(requestId)
          .type(readModelClass)
          .className(readModelClass.getSimpleName())
          .limit(null)
          .afterCursor(null)
          .paginatedVersion(false)
          .version(1)
          .select(null)
          .build();
    }

    @Nested
    class ReadModelResolverBuilder {

      @Test
      void callsSearchWithTheExpectedPayload() {
        GraphQLGenerator.readModelResolverBuilder(GraphQLTestApp.ProductReadModel.class)
            .resolve("", Map.of(), mockResolverContext, null);

        verify(readModelsReader()).search(expectedSearchEnvelope(GraphQLTestApp.ProductReadModel.class));
      }

      @Test
      void returnsTheSearchResult() {
        List<Object> fetchResult = List.of(Map.of("id", "1"), Map.of("id", "2"));
        when(readModelsReader().search(any())).thenReturn(fetchResult);

        Object result = GraphQLGenerator.readModelResolverBuilder(GraphQLTestApp.ProductReadModel.class)
            .resolve("", Map.of(), mockResolverContext, null);

        assertThat(result).isEqualTo(fetchResult);
      }

      @Test
      void passesTheFilterSortLimitAndCursor() {
        Map<String, Object> args = Map.of("filter", Map.of("sku", Map.of("eq", "a")), "sortBy",
            Map.of("price", "DESC"), "limit", 10, "afterCursor", Map.of("id", "10"));

        GraphQLGenerator.readModelResolverBuilder(GraphQLTestApp.ProductReadModel.class)
            .resolve("", args, mockResolverContext, null);

        verify(readModelsReader()).search(expectedSearchEnvelope(GraphQLTestApp.ProductReadModel.class).toBuilder()
            .filters(Map.of("sku", Map.of("eq", "a")))
            .sortBy(Map.of("price", "DESC"))
            .limit(10)
            .afterCursor(Map.of("id", "10"))
            .build());
      }
    }

    @Nested
    class ReadModelByIDResolverBuilder {

      @Test
      void buildsAFunctionThatPerformsRequestsById() {
        BoosterConfig config = new BoosterConfig("test");

        GraphQLGenerator.readModelByIDResolverBuilder(config, GraphQLTestApp.TimelineReadModel.class)
            .resolve(Map.of(), Map.of("id", "42"), mockResolverContext, null);

        ArgumentCaptor<ReadModelRequestEnvelope> envelope = ArgumentCaptor.forClass(ReadModelRequestEnvelope.class);
        verify(readModelsReader()).findById(envelope.capture());
        assertThat(envelope.getValue()).isEqualTo(ReadModelRequestEnvelope.builder()
            .currentUser(user)
            .requestID(requestId)
            .type(GraphQLTestApp.TimelineReadModel.class)
            .className("TimelineReadModel")
            .key(new ReadModelRequestEnvelope.Key(UUID.of("42"), null))
            .version(1)
            .filters(Map.of())
            .sortBy(Map.of())
            .build());
      }

      @Test
      void buildsAFunctionThatPerformsRequestsByIdAndSequenceKey() {
        BoosterConfig config = new BoosterConfig("test");
        config.readModelSequenceKeys.put("TimelineReadModel", "timestamp");

        GraphQLGenerator.readModelByIDResolverBuilder(config, GraphQLTestApp.TimelineReadModel.class)
            .resolve(Map.of(), Map.of("id", "42", "timestamp", "1000"), mockResolverContext, null);

        ArgumentCaptor<ReadModelRequestEnvelope> envelope = ArgumentCaptor.forClass(ReadModelRequestEnvelope.class);
        verify(readModelsReader()).findById(envelope.capture());
        assertThat(envelope.getValue().key()).isEqualTo(new ReadModelRequestEnvelope.Key(UUID.of("42"),
            new SequenceKey("timestamp", TimeKey.of("1000"))));
        assertThat(envelope.getValue().version()).isEqualTo(1);
        assertThat(envelope.getValue().className()).isEqualTo("TimelineReadModel");
      }

      @Test
      void keepsTheSequenceKeyWithoutValueWhenTheArgumentIsOmitted() {
        BoosterConfig config = new BoosterConfig("test");
        config.readModelSequenceKeys.put("TimelineReadModel", "timestamp");

        GraphQLGenerator.readModelByIDResolverBuilder(config, GraphQLTestApp.TimelineReadModel.class)
            .resolve(Map.of(), Map.of("id", "42"), mockResolverContext, null);

        ArgumentCaptor<ReadModelRequestEnvelope> envelope = ArgumentCaptor.forClass(ReadModelRequestEnvelope.class);
        verify(readModelsReader()).findById(envelope.capture());
        assertThat(envelope.getValue().key().sequenceKey()).isEqualTo(new SequenceKey("timestamp", null));
      }

      @Test
      void readsTheSequenceKeyWhenTheResolverIsBuilt() {
        BoosterConfig config = new BoosterConfig("test");
        GraphQLFieldResolver resolver = GraphQLGenerator.readModelByIDResolverBuilder(config,
            GraphQLTestApp.TimelineReadModel.class);
        config.readModelSequenceKeys.put("TimelineReadModel", "timestamp");

        resolver.resolve(Map.of(), Map.of("id", "42", "timestamp", "1000"), mockResolverContext, null);

        ArgumentCaptor<ReadModelRequestEnvelope> envelope = ArgumentCaptor.forClass(ReadModelRequestEnvelope.class);
        verify(readModelsReader()).findById(envelope.capture());
        assertThat(envelope.getValue().key().sequenceKey()).isNull();
      }
    }

    @Nested
    class CommandResolverBuilder {

      private final Map<String, Object> mockInput = Map.of("testObjectKey", "a value");

      @Test
      void callsDispatchCommandWithTheExpectedInput() {
        GraphQLGenerator.commandResolverBuilder(GraphQLTestApp.CreateProduct.class)
            .resolve("", Map.of("input", mockInput), mockResolverContext, null);

        ArgumentCaptor<CommandEnvelope> envelope = ArgumentCaptor.forClass(CommandEnvelope.class);
        verify(commandDispatchers.constructed().getFirst()).dispatchCommand(envelope.capture(),
            eq(mockResolverContext));
        assertThat(envelope.getValue().requestID()).isEqualTo(requestId);
        assertThat(envelope.getValue().currentUser()).isEqualTo(user);
        assertThat(envelope.getValue().typeName()).isEqualTo("CreateProduct");
        assertThat(envelope.getValue().value()).isEqualTo(mockInput);
        assertThat(envelope.getValue().version()).isEqualTo(1);
        assertThat(envelope.getValue().context().request()).isEqualTo(new ContextEnvelope.Request(
            Map.of("authorization", "Bearer 123"), Map.of("query", "Test query")));
        assertThat(envelope.getValue().context().rawContext()).isSameAs(mockResolverContext);
      }

      @Test
      void returnsTrueWhenTheCommandReturnsNothing() {
        Object result = GraphQLGenerator.commandResolverBuilder(GraphQLTestApp.CreateProduct.class)
            .resolve("", Map.of("input", mockInput), mockResolverContext, null);

        assertThat(result).isEqualTo(true);
      }

      @Test
      void returnsFalsyResultsAsTheyAre() {
        when(commandDispatchers.constructed().getFirst().dispatchCommand(any(), any())).thenReturn(false, 0, "");
        GraphQLFieldResolver resolver = GraphQLGenerator.commandResolverBuilder(GraphQLTestApp.CreateProduct.class);

        assertThat(resolver.resolve("", Map.of(), mockResolverContext, null)).isEqualTo(false);
        assertThat(resolver.resolve("", Map.of(), mockResolverContext, null)).isEqualTo(0);
        assertThat(resolver.resolve("", Map.of(), mockResolverContext, null)).isEqualTo("");
      }

      @Test
      void buildsAnEmptyRequestContextWhenTheRequestHasNone() {
        GraphQLResolverContext withoutContext = new GraphQLResolverContext(null, new LinkedHashMap<>(),
            GraphQLOperation.builder().query("q").build(), requestId, null, true, pubSub, null);

        GraphQLGenerator.commandResolverBuilder(GraphQLTestApp.DeleteProduct.class)
            .resolve("", Map.of(), withoutContext, null);

        ArgumentCaptor<CommandEnvelope> envelope = ArgumentCaptor.forClass(CommandEnvelope.class);
        verify(commandDispatchers.constructed().getFirst()).dispatchCommand(envelope.capture(), any());
        assertThat(envelope.getValue().context().request()).isEqualTo(new ContextEnvelope.Request(null, null));
        assertThat(envelope.getValue().value()).isNull();
      }
    }

    @Nested
    class QueriesResolverBuilder {

      @Test
      void dispatchesTheQueryAndReturnsItsResultAsItIs() {
        BoosterQueryDispatcher dispatcher = queryDispatchers.constructed().getFirst();
        when(dispatcher.dispatchQuery(any(), any())).thenReturn(null);

        Object result = GraphQLGenerator.queriesResolverBuilder(GraphQLTestApp.GetStock.class)
            .resolve("", Map.of("input", Map.of("productId", "1")), mockResolverContext, null);

        assertThat(result).isNull();
        ArgumentCaptor<QueryEnvelope> envelope = ArgumentCaptor.forClass(QueryEnvelope.class);
        verify(dispatcher).dispatchQuery(envelope.capture(), eq(mockResolverContext));
        assertThat(envelope.getValue().typeName()).isEqualTo("GetStock");
        assertThat(envelope.getValue().value()).isEqualTo(Map.of("productId", "1"));
        assertThat(envelope.getValue().version()).isEqualTo(1);
      }
    }

    @Nested
    class SubscriptionByIDResolverBuilder {

      @Test
      void callsSubscriptionResolverBuilderAndReturnsItsResult() {
        try (MockedStatic<GraphQLGenerator> generator = mockStatic(GraphQLGenerator.class, CALLS_REAL_METHODS)) {
          GraphQLFieldResolver inner = (parent, args, context, info) -> "the inner result";
          generator.when(() -> GraphQLGenerator.subscriptionResolverBuilder(mockConfig,
              GraphQLTestApp.ProductReadModel.class)).thenReturn(inner);

          Object result = GraphQLGenerator.subscriptionByIDResolverBuilder(mockConfig,
              GraphQLTestApp.ProductReadModel.class).resolve("", Map.of("id", "42"), mockResolverContext, null);

          generator.verify(() -> GraphQLGenerator.subscriptionResolverBuilder(mockConfig,
              GraphQLTestApp.ProductReadModel.class));
          assertThat(result).isEqualTo("the inner result");
        }
      }

      @Test
      void subscribesWithAnIdFilter() {
        when(pubSub.asyncIterator(any(), any())).thenReturn(List.<Object>of().iterator());

        GraphQLGenerator.subscriptionByIDResolverBuilder(mockConfig, GraphQLTestApp.ProductReadModel.class)
            .resolve("", Map.of("id", "42"), context("connection-1", true), null);

        ArgumentCaptor<ReadModelRequestEnvelope> envelope = ArgumentCaptor.forClass(ReadModelRequestEnvelope.class);
        verify(readModelsReader()).subscribe(eq("connection-1"), envelope.capture(), any());
        assertThat(envelope.getValue().filters()).isEqualTo(Map.of("id", Map.of("eq", "42")));
      }
    }

    @Nested
    class SubscriptionResolverBuilder {

      @Test
      void throwsWithoutConnectionID() {
        GraphQLFieldResolver resolver = GraphQLGenerator.subscriptionResolverBuilder(mockConfig,
            GraphQLTestApp.ProductReadModel.class);

        assertThatThrownBy(() -> resolver.resolve("", Map.of(), context(null, true), null))
            .hasMessage("Missing \"connectionID\". It is required for subscriptions");
        verify(readModelsReader(), never()).subscribe(any(), any(), any());
      }

      @Test
      void storesTheSubscriptionWhenStoreSubscriptionsIsSet() {
        GraphQLResolverContext context = context("connection-1", true);

        GraphQLGenerator.subscriptionResolverBuilder(mockConfig, GraphQLTestApp.ProductReadModel.class)
            .resolve("", Map.of(), context, null);

        verify(readModelsReader(), times(1)).subscribe("connection-1",
            expectedSearchEnvelope(GraphQLTestApp.ProductReadModel.class), context.operation());
      }

      @Test
      void doesNotStoreTheSubscriptionOtherwise() {
        GraphQLGenerator.subscriptionResolverBuilder(mockConfig, GraphQLTestApp.ProductReadModel.class)
            .resolve("", Map.of(), context("connection-1", false), null);

        verify(readModelsReader(), never()).subscribe(any(), any(), any());
      }

      @Test
      void callsThePubSubWithTheReadModelRequest() {
        GraphQLGenerator.subscriptionResolverBuilder(mockConfig, GraphQLTestApp.ProductReadModel.class)
            .resolve("", Map.of(), context("connection-1", false), null);

        verify(pubSub, times(1)).asyncIterator(expectedSearchEnvelope(GraphQLTestApp.ProductReadModel.class),
            mockConfig);
      }

      @Test
      void returnsThePubSubIteratorAsAPublisher() {
        Iterator<Object> iterator = List.<Object>of("rm1", "rm2").iterator();
        when(pubSub.asyncIterator(any(), any())).thenReturn(iterator);

        Object result = GraphQLGenerator.subscriptionResolverBuilder(mockConfig,
            GraphQLTestApp.ProductReadModel.class).resolve("", Map.of(), context("connection-1", false), null);

        assertThat(result).isInstanceOf(IteratorPublisher.class);
        assertThat(((IteratorPublisher) result).iterator()).isSameAs(iterator);
        assertThat(IteratorPublisherTest.collect((Flow.Publisher<Object>) result)).containsExactly("rm1", "rm2");
      }
    }

    @Nested
    class EventResolver {

      @Test
      void callsFetchWithTheExpectedPayload() {
        GraphQLGenerator.eventResolver("", Map.of("entity", "TestEntity"), mockResolverContext);

        verify(eventsReaders.constructed().getFirst()).fetch(EventSearchRequest.builder()
            .currentUser(user)
            .parameters(new EventParametersFilterByEntity("TestEntity", null, null, null, null))
            .requestID(requestId)
            .build());
      }

      @Test
      void returnsTheFetchedEvents() {
        List<EventSearchResponse> fetchEventsResult = new ArrayList<>();
        when(eventsReaders.constructed().getFirst().fetch(any())).thenReturn(fetchEventsResult);

        Object result = GraphQLGenerator.eventResolver().resolve("", Map.of("entity", "TestEntity"),
            mockResolverContext, null);

        assertThat(result).isSameAs(fetchEventsResult);
      }
    }
  }

  /** {@code getFields(info)}: the {@code select} of read model searches, extracted while executing real queries. */
  @Nested
  class SelectedFields {

    private MockedConstruction<BoosterReadModelsReader> readModelsReaders;
    private GraphQLSchema schema;

    @BeforeEach
    void setUpSchema() {
      readModelsReaders = mockConstruction(BoosterReadModelsReader.class, (reader, context) -> {
        when(reader.search(any())).thenReturn(List.of());
      });
      Booster.config().logLevel = Level.ERROR;
      GraphQLTestApp.CLASSES.forEach(Decorators::registerClass);
      schema = GraphQLGenerator.generateSchema(Booster.config());
    }

    @AfterEach
    void closeReaders() {
      readModelsReaders.close();
    }

    private ReadModelRequestEnvelope searchedWith(String query) {
      GraphQLResolverContext context = new GraphQLResolverContext(null, new LinkedHashMap<>(),
          GraphQLOperation.builder().query(query).build(), UUID.of("r"), null, true, null, null);
      ExecutionResult result = GraphQLExecution.execute(schema, Parser.parse(query), query, context, null, null);
      assertThat(result.getErrors()).isEmpty();
      ArgumentCaptor<ReadModelRequestEnvelope> envelope = ArgumentCaptor.forClass(ReadModelRequestEnvelope.class);
      verify(readModelsReaders.constructed().getFirst()).search(envelope.capture());
      return envelope.getValue();
    }

    @Test
    void marksListsOfObjects() {
      ReadModelRequestEnvelope envelope = searchedWith("{ ProductReadModels { id tags addresses { street } } }");

      assertThat(envelope.select()).containsExactly("id", "tags", "addresses[].street");
      assertThat(envelope.paginatedVersion()).isFalse();
    }

    @Test
    void followsNestedObjects() {
      ReadModelRequestEnvelope envelope = searchedWith("{ ProductReadModels { address { street zip } } }");

      assertThat(envelope.select()).containsExactly("address.street", "address.zip");
    }

    @Test
    void ignoresAliasesAndUnknownFieldsAndInlinesFragments() {
      ReadModelRequestEnvelope envelope = searchedWith("""
          { ProductReadModels { ...Ids theSku: sku __typename vehicle { __typename ... on Car { wheels } } } }
          fragment Ids on ProductReadModel { id }
          """);

      assertThat(envelope.select()).containsExactly("id", "sku", "vehicle.wheels");
    }

    @Test
    void removesTheItemsSegmentInPaginatedQueries() {
      when(readModelsReaders.constructed().getFirst().search(any()))
          .thenReturn(new ReadModelListResult<>(List.of(), 0, null));

      ReadModelRequestEnvelope envelope = searchedWith(
          "{ ListProductReadModels { items { id address { street } } count cursor } }");

      assertThat(envelope.select()).containsExactly("id", "address.street");
      assertThat(envelope.paginatedVersion()).isTrue();
    }

    @Test
    void sendsAnEmptySelectWhenOnlyTheCountIsSelected() {
      when(readModelsReaders.constructed().getFirst().search(any()))
          .thenReturn(new ReadModelListResult<>(List.of(), 0, null));

      ReadModelRequestEnvelope envelope = searchedWith("{ ListProductReadModels { count } }");

      assertThat(envelope.select()).isEmpty();
    }

    @Test
    void sendsNoSelectWithoutAnEnvironment() {
      assertThat(GraphQLGenerator.getFields(null)).isEmpty();
    }

    @Test
    void readsTheConnectionOfAPaginatedSearch() {
      when(readModelsReaders.constructed().getFirst().search(any())).thenReturn(new ReadModelListResult<>(
          List.of(Map.of("id", "1")), 1, Map.of("id", "1")));
      String query = "{ ListProductReadModels { items { id } count cursor } }";
      GraphQLResolverContext context = new GraphQLResolverContext(null, new LinkedHashMap<>(),
          GraphQLOperation.builder().query(query).build(), UUID.of("r"), null, true, null, null);

      ExecutionResult result = GraphQLExecution.execute(schema, Parser.parse(query), query, context, null, null);

      assertThat(GraphQLExecution.toResultMap(result)).isEqualTo(Map.of("data", Map.of("ListProductReadModels",
          Map.of("items", List.of(Map.of("id", "1")), "count", 1, "cursor", Map.of("id", "1")))));
    }
  }

  @Test
  void failsToResolveBeforeTheSchemaIsGenerated() {
    GraphQLFieldResolver resolver = GraphQLGenerator.readModelResolverBuilder(GraphQLTestApp.ProductReadModel.class);

    assertThatThrownBy(() -> resolver.resolve(null, Map.of(), null, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("generateSchema");
  }

  @Test
  void createsTheRootTypesWithTheirResolvers() {
    try (MockedConstruction<BoosterReadModelsReader> readers = mockConstruction(BoosterReadModelsReader.class)) {
      GraphQLTestApp.CLASSES.forEach(Decorators::registerClass);
      GraphQLSchema schema = GraphQLGenerator.generateSchema(Booster.config());

      GraphQLObjectType query = schema.getQueryType();
      assertThat(schema.getCodeRegistry().getDataFetcher(query, query.getFieldDefinition("ProductReadModel")))
          .isInstanceOf(GraphQLFieldResolver.class);
      assertThat(schema.getCodeRegistry().getDataFetcher(schema.getObjectType("Address"),
          schema.getObjectType("Address").getFieldDefinition("street"))).isInstanceOf(PropertyResolver.class);
      assertThat(readers.constructed()).hasSize(1);
    }
  }
}
