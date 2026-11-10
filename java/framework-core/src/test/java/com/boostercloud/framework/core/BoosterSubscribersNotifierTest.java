package com.boostercloud.framework.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.common.PromisesError;
import com.boostercloud.framework.core.services.graphql.Common;
import com.boostercloud.framework.core.services.graphql.GraphQLResolverContext;
import com.boostercloud.framework.core.services.pubsub.FilteredReadModelPubSub;
import com.boostercloud.framework.core.services.pubsub.ReadModelPubSub;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.ReadModelMetadata;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.GraphQLOperation;
import com.boostercloud.framework.types.envelope.ReadModelEnvelope;
import com.boostercloud.framework.types.envelope.ReadModelRequestEnvelope;
import com.boostercloud.framework.types.envelope.SubscriptionEnvelope;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.framework.types.graphql.GraphQLData;
import com.boostercloud.framework.types.provider.ProviderConnectionsLibrary;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import com.boostercloud.framework.types.provider.ProviderReadModelsLibrary;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * {@code booster-subscribers-notifier.ts} (no TypeScript test of its own; spec 07 §3.5). The schema is built here with
 * subscription resolvers that follow the contract of the GraphQL stage: they read the {@link GraphQLResolverContext}
 * and return the pub-sub iterator as a publisher.
 */
class BoosterSubscribersNotifierTest extends BoosterTestSupport {

  public record TestReadModel(String id, int count) {}

  public record OtherReadModel(String id) {}

  static final String SDL = """
      type Query { ping: String }
      type Subscription {
        TestReadModel(id: ID!): TestReadModel
        TestReadModels(minCount: Int): TestReadModel
        OtherReadModel(id: ID!): OtherReadModel
      }
      type TestReadModel { id: ID! count: Int! }
      type OtherReadModel { id: ID! }
      """;

  private BoosterConfig config;
  private ProviderReadModelsLibrary readModels;
  private ProviderConnectionsLibrary connections;
  private final List<GraphQLResolverContext> contexts = Collections.synchronizedList(new ArrayList<>());

  @BeforeEach
  void setUp() {
    config = new BoosterConfig("test");
    config.logger = mock(Logger.class);
    ProviderLibrary provider = mock(ProviderLibrary.class);
    readModels = mock(ProviderReadModelsLibrary.class);
    connections = mock(ProviderConnectionsLibrary.class);
    when(provider.readModels()).thenReturn(readModels);
    when(provider.connections()).thenReturn(connections);
    config.setProvider(provider);
    for (Class<?> readModel : List.of(TestReadModel.class, OtherReadModel.class)) {
      config.readModels.put(readModel.getSimpleName(),
          new ReadModelMetadata(readModel, List.of(), BoosterAuthorizer.ALLOW_ACCESS, List.of()));
    }
  }

  private DataFetcher<Object> subscriptionResolver(Class<?> readModelClass, String argument, String property,
      String operator) {
    return environment -> {
      GraphQLResolverContext context = environment.getGraphQlContext().get(GraphQLResolverContext.class);
      contexts.add(context);
      assertThat(Common.resolverContext(environment)).isSameAs(context);
      Map<String, Object> filters = new LinkedHashMap<>();
      if (environment.getArgument(argument) != null) {
        filters.put(property, Map.of(operator, environment.getArgument(argument)));
      }
      ReadModelRequestEnvelope envelope = ReadModelRequestEnvelope.builder().currentUser(context.user())
          .requestID(context.requestID()).type(readModelClass).className(readModelClass.getSimpleName()).filters(filters)
          .version(1).build();
      return ReadModelPubSub.toPublisher(context.pubSub().asyncIterator(envelope, config));
    };
  }

  private GraphQLSchema schema() {
    RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
        .type("Subscription", type -> type
            .dataFetcher("TestReadModel", subscriptionResolver(TestReadModel.class, "id", "id", "eq"))
            .dataFetcher("TestReadModels", subscriptionResolver(TestReadModel.class, "minCount", "count", "gte"))
            .dataFetcher("OtherReadModel", subscriptionResolver(OtherReadModel.class, "id", "id", "eq")))
        .build();
    return new SchemaGenerator().makeExecutableSchema(new SchemaParser().parse(SDL), wiring);
  }

  private BoosterSubscribersNotifier notifier() {
    return new BoosterSubscribersNotifier(config, schema());
  }

  private static SubscriptionEnvelope subscription(String className, String connectionID, String operationID, String query,
      Map<String, Object> variables) {
    return SubscriptionEnvelope.builder().className(className).connectionID(connectionID).requestID(UUID.of("request-" + operationID))
        .currentUser(UserEnvelope.builder().username("user@example.com").build()).version(1).expirationTime(1)
        .operation(GraphQLOperation.builder().id(operationID).query(query).variables(variables).build()).build();
  }

  private void changed(ReadModelEnvelope... envelopes) {
    when(readModels.rawToEnvelopes(eq(config), any())).thenReturn(List.of(envelopes));
  }

  private static Map<String, Object> data(String field, Map<String, Object> value) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put(field, value);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("data", data);
    return payload;
  }

  @Test
  void sendsTheResultOfEveryMatchingSubscriptionToItsConnection() {
    changed(new ReadModelEnvelope("TestReadModel", Map.of("id", "1", "count", 5)));
    when(readModels.fetchSubscriptions(config, "TestReadModel")).thenReturn(List.of(
        subscription("TestReadModel", "connection-1", "op-1", "subscription { TestReadModel(id: \"1\") { id count } }", null),
        subscription("TestReadModel", "connection-2", "op-2", "subscription { TestReadModel(id: \"2\") { id } }", null)));

    notifier().dispatch("raw request");

    verify(readModels).rawToEnvelopes(config, "raw request");
    verify(connections, times(1)).sendMessage(config, "connection-1",
        new GraphQLData("op-1", data("TestReadModel", Map.of("id", "1", "count", 5))));
    verify(connections, never()).sendMessage(eq(config), eq("connection-2"), any());
    verify(config.logger, never()).error(any(), any());
  }

  @Test
  void usesTheStoredVariablesAndBuildsTheResolverContext() {
    changed(new ReadModelEnvelope("TestReadModel", Map.of("id", "1", "count", 5)));
    when(readModels.fetchSubscriptions(config, "TestReadModel")).thenReturn(List.of(subscription("TestReadModel",
        "connection-1", "op-1", "subscription Sub($id: ID!) { TestReadModel(id: $id) { id } }", Map.of("id", "1"))));

    notifier().dispatch(null);

    verify(connections).sendMessage(config, "connection-1", new GraphQLData("op-1", data("TestReadModel", Map.of("id", "1"))));
    assertThat(contexts).hasSize(1);
    GraphQLResolverContext context = contexts.get(0);
    assertThat(context.connectionID()).isEqualTo("connection-1");
    assertThat(context.requestID()).isEqualTo(UUID.of("request-op-1"));
    assertThat(context.user().username()).isEqualTo("user@example.com");
    assertThat(context.operation().id()).isEqualTo("op-1");
    assertThat(context.storeSubscriptions()).isFalse();
    assertThat(context.responseHeaders()).isEmpty();
    assertThat(context.pubSub()).isInstanceOf(FilteredReadModelPubSub.class);
    assertThat(((FilteredReadModelPubSub) context.pubSub()).readModels()).containsExactly(new TestReadModel("1", 5));
  }

  @Test
  void sendsOneMessagePerMatchingChangedReadModel() {
    changed(new ReadModelEnvelope("TestReadModel", Map.of("id", "1", "count", 5)),
        new ReadModelEnvelope("TestReadModel", Map.of("id", "2", "count", 1)),
        new ReadModelEnvelope("TestReadModel", Map.of("id", "3", "count", 9)));
    when(readModels.fetchSubscriptions(config, "TestReadModel")).thenReturn(List.of(subscription("TestReadModel",
        "connection-1", "op-1", "subscription { TestReadModels(minCount: 5) { id } }", null)));

    notifier().dispatch(null);

    InOrder order = inOrder(connections);
    order.verify(connections).sendMessage(config, "connection-1", new GraphQLData("op-1", data("TestReadModels", Map.of("id", "1"))));
    order.verify(connections).sendMessage(config, "connection-1", new GraphQLData("op-1", data("TestReadModels", Map.of("id", "3"))));
    verify(connections, times(2)).sendMessage(any(), any(), any());
  }

  @Test
  void fetchesTheSubscriptionsOncePerChangedTypeAndOnlyNotifiesTheRightType() {
    changed(new ReadModelEnvelope("TestReadModel", Map.of("id", "1", "count", 5)),
        new ReadModelEnvelope("OtherReadModel", Map.of("id", "1")),
        new ReadModelEnvelope("TestReadModel", Map.of("id", "2", "count", 5)));
    when(readModels.fetchSubscriptions(config, "TestReadModel")).thenReturn(List.of());
    when(readModels.fetchSubscriptions(config, "OtherReadModel")).thenReturn(List.of(subscription("OtherReadModel",
        "connection-9", "op-9", "subscription { OtherReadModel(id: \"1\") { id } }", null)));

    notifier().dispatch(null);

    verify(readModels, times(1)).fetchSubscriptions(config, "TestReadModel");
    verify(readModels, times(1)).fetchSubscriptions(config, "OtherReadModel");
    verify(connections, times(1)).sendMessage(config, "connection-9", new GraphQLData("op-9", data("OtherReadModel", Map.of("id", "1"))));
  }

  @Test
  void anUnknownReadModelIsLoggedAfterFetchingTheSubscriptions() {
    changed(new ReadModelEnvelope("Unknown", Map.of("id", "1")));

    notifier().dispatch(null);

    verify(readModels).fetchSubscriptions(config, "Unknown");
    verify(config.logger).error(eq("[Booster]|BoosterSubscribersNotifier#dispatch: "), argThat((Object error) ->
        error instanceof IllegalStateException e
            && e.getMessage().equals("Could not get information about read model with name: Unknown")));
    verify(connections, never()).sendMessage(any(), any(), any());
  }

  @Test
  void anInvalidStoredDocumentDoesNotStopTheOtherSubscriptions() {
    changed(new ReadModelEnvelope("TestReadModel", Map.of("id", "1", "count", 5)));
    when(readModels.fetchSubscriptions(config, "TestReadModel")).thenReturn(List.of(
        subscription("TestReadModel", "connection-1", "op-1", "subscription { TestReadModel(id: \"1\") { nope } }", null),
        subscription("TestReadModel", "connection-2", "op-2", "subscription { TestReadModel(id: \"1\") { id } }", null),
        subscription("TestReadModel", "connection-3", "op-3", "subscription {", null)));

    notifier().dispatch(null);

    verify(connections, times(1)).sendMessage(config, "connection-2", new GraphQLData("op-2", data("TestReadModel", Map.of("id", "1"))));
    verify(config.logger).error(eq("[Booster]|BoosterSubscribersNotifier#dispatch: "), argThat((Object error) -> {
      if (!(error instanceof PromisesError aggregate) || aggregate.failedReasons().size() != 2) {
        return false;
      }
      return aggregate.failedReasons().get(0) instanceof BoosterSubscribersNotifier.GraphQLErrors errors
          && !errors.errors().isEmpty();
    }));
  }

  @Test
  void aSendFailureIsLoggedAndNeverThrown() {
    changed(new ReadModelEnvelope("TestReadModel", Map.of("id", "1", "count", 5)));
    when(readModels.fetchSubscriptions(config, "TestReadModel")).thenReturn(List.of(subscription("TestReadModel",
        "connection-1", "op-1", "subscription { TestReadModel(id: \"1\") { id } }", null)));
    doThrow(new IllegalStateException("gone")).when(connections).sendMessage(any(), any(), any());

    notifier().dispatch(null);

    verify(config.logger).error(eq("[Booster]|BoosterSubscribersNotifier#dispatch: "), any(PromisesError.class));
  }

  @Test
  void aProviderFailureIsLogged() {
    when(readModels.rawToEnvelopes(any(), any())).thenThrow(new IllegalArgumentException("bad request"));

    notifier().dispatch(null);

    verify(config.logger).error(eq("[Booster]|BoosterSubscribersNotifier#dispatch: "), any(IllegalArgumentException.class));
  }

  @Test
  void aQueryDocumentIsNotASubscriptionAndFails() {
    changed(new ReadModelEnvelope("TestReadModel", Map.of("id", "1", "count", 5)));
    when(readModels.fetchSubscriptions(config, "TestReadModel")).thenReturn(List.of(subscription("TestReadModel",
        "connection-1", "op-1", "query { ping }", null)));

    notifier().dispatch(null);

    verify(connections, never()).sendMessage(any(), any(), any());
    verify(config.logger).error(eq("[Booster]|BoosterSubscribersNotifier#dispatch: "), any(PromisesError.class));
  }

  @Test
  void noChangedReadModelsDoNothing() {
    changed();

    notifier().dispatch(null);

    verify(readModels, never()).fetchSubscriptions(any(), any());
    verify(config.logger, never()).error(any(), any());
  }
}
