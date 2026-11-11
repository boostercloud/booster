package com.boostercloud.framework.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.types.instrumentation.TraceInfo;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

/**
 * Tracing a request end to end: the framework's trace points of the GraphQL, command, event and read model layers
 * and a {@code @Trace} on a user handler reach the configured {@code onStart}/{@code onEnd} handlers.
 */
class TracingIntegrationTest extends IntegrationTestSupport {

  private final List<String> started = new CopyOnWriteArrayList<>();
  private final List<TraceInfo> ended = new CopyOnWriteArrayList<>();

  private void traceEverything() {
    config.traceConfiguration.enableAll();
    config.traceConfiguration.onStart = (traceConfig, actionType, info) -> started.add(actionType);
    config.traceConfiguration.onEnd = (traceConfig, actionType, info) -> ended.add(info);
  }

  @Test
  void aMutationIsTracedThroughEveryLayer() {
    traceEverything();

    data(graphQL("mutation { CreateProduct(input: {sku: \"T\", displayName: \"T\", price: 1, category: \"t\"}) }",
        TestTokens.admin()));

    assertThat(started).contains("GRAPHQL_DISPATCH", "GRAPHQL_RUN_OPERATION", "COMMAND_HANDLER", "CREATE_PRODUCT",
        "DISPATCH_EVENTS", "FETCH_ENTITY_SNAPSHOT", "ENTITY_REDUCER", "STORE_SNAPSHOT", "DISPATCH_SUBSCRIBER_NOTIFIER");
    assertThat(ended).filteredOn(info -> "handle".equals(info.methodName) && "CreateProduct".equals(info.className))
        .singleElement().satisfies(info -> {
          assertThat(info.description).isEqualTo("Creates a product");
          assertThat(info.elapsedInvocationMillis).isNotNull().isNotNegative();
        });
    assertThat(ended).hasSameSizeAs(started);
  }

  @Test
  void onlyTheEnabledActionsAreTraced() {
    traceEverything();
    config.traceConfiguration.enableFor("CREATE_PRODUCT", "READ_MODEL_FIND_BY_ID");

    data(graphQL("mutation { CreateProduct(input: {sku: \"T\", displayName: \"T\", price: 1, category: \"t\"}) }",
        TestTokens.admin()));
    data(graphQL("{ CartReadModel(id: \"x\") { id } }"));

    assertThat(started).containsExactly("CREATE_PRODUCT", "READ_MODEL_FIND_BY_ID");
  }
}
