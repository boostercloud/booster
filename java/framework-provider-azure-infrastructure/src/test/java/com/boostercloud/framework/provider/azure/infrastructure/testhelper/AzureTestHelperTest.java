package com.boostercloud.framework.provider.azure.infrastructure.testhelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.provider.azure.cosmos.CosmosClient;
import com.boostercloud.framework.provider.azure.cosmos.CosmosContainer;
import com.boostercloud.framework.provider.azure.cosmos.CosmosDatabase;
import com.boostercloud.framework.provider.azure.cosmos.CosmosItem;
import com.boostercloud.framework.provider.azure.cosmos.CosmosItems;
import com.boostercloud.framework.provider.azure.cosmos.CosmosQueryIterator;
import com.boostercloud.framework.provider.azure.cosmos.FeedResponse;
import com.boostercloud.framework.provider.azure.cosmos.ItemResponse;
import com.boostercloud.framework.provider.azure.cosmos.SqlParameter;
import com.boostercloud.framework.provider.azure.cosmos.SqlQuerySpec;
import com.boostercloud.framework.provider.azure.infrastructure.helper.AzCliHelper;
import com.boostercloud.framework.provider.azure.infrastructure.helper.Params;
import com.boostercloud.framework.provider.azure.infrastructure.support.Fixtures;
import com.boostercloud.framework.provider.azure.infrastructure.support.RecordingCommandRunner;
import com.boostercloud.framework.provider.azure.infrastructure.support.TestEnvironment;
import com.boostercloud.framework.types.EnvironmentVariables;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(TestEnvironment.class)
class AzureTestHelperTest {

  private final RecordingCommandRunner commands = new RecordingCommandRunner().answering(command ->
      command.arguments().get(1).equals("group")
          ? "{\"id\":\"i\",\"location\":\"eastus\",\"name\":\"mystoreproductionrg\"}"
          : "{\"connectionStrings\":[{\"connectionString\":\"AccountEndpoint=https://x:443/;AccountKey=k;\"}]}");

  @Test
  void builds_the_outputs_counters_and_queries() {
    AtomicReference<String> connectionString = new AtomicReference<>();
    CosmosClient cosmos = mock(CosmosClient.class);
    AzureTestHelper helper = AzureTestHelper.build("my-store", "production", new AzCliHelper(commands),
        Fixtures.CONFIGURATION, value -> {
          connectionString.set(value);
          return cosmos;
        });
    assertThat(connectionString.get()).isEqualTo("AccountEndpoint=https://x:443/;AccountKey=k;");
    assertThat(helper.outputs()).isEqualTo(new ApplicationOutputs(
        "http://mystoreproductionrgapis.eastus.cloudapp.azure.com/production/graphql",
        "wss://mystoreproductionrgwps.webpubsub.azure.com:443/client/hubs/booster",
        "http://mystoreproductionrgapis.eastus.cloudapp.azure.com/production/sensor/health/"));
    assertThat(helper.counters()).isNotNull();
    assertThat(helper.queries()).isNotNull();
  }

  @Test
  void the_url_defaults_follow_typescript() {
    var group = new com.boostercloud.framework.provider.azure.infrastructure.types.ResourceGroup("i", "l", null, "rg");
    EnvironmentVariables.set("REGION", "North Europe");
    assertThat(AzureTestHelper.graphqlURL(group)).isEqualTo("http://rgapis.northeurope.cloudapp.azure.com/production/graphql");
    assertThat(AzureTestHelper.healthURL(group))
        .isEqualTo("http://rgapis.northeurope.cloudapp.azure.com/production/sensor/health/");
  }

  @Test
  void fails_without_cosmos_connection_strings() {
    RecordingCommandRunner empty = new RecordingCommandRunner().answering(command ->
        command.arguments().get(1).equals("group") ? "{\"name\":\"rg\"}" : "{\"connectionStrings\":[]}");
    assertThatThrownBy(() -> AzureTestHelper.build("my-store", "production", new AzCliHelper(empty),
        Fixtures.CONFIGURATION, value -> mock(CosmosClient.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Unable to get cosmos connection details from current resource group");
  }

  @Test
  void checks_the_azure_configuration() {
    assertThatThrownBy(() -> AzureTestHelper.ensureAzureConfiguration(
        Params.Configuration.of("app", "secret", "", "sub", "East US")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Azure credentials were not properly loaded and are required to run the integration tests"
            + "\nappId = app\ntenantId = \nsecret = secret\nsubscriptionId = sub");
    assertThatThrownBy(() -> AzureTestHelper.ensureAzureConfiguration(
        Params.Configuration.of("app", "secret", "tenant", "sub", "")))
        .hasMessage("Azure region was not properly loaded and is required to run the integration tests. ");
    AzureTestHelper.ensureAzureConfiguration(Fixtures.CONFIGURATION);
    AzureTestHelper.ensureAzureConfiguration();
  }

  /** A Cosmos DB client whose containers answer every query with {@code results} and record the queries. */
  private static final class FakeCosmos {
    final Map<String, List<SqlQuerySpec>> queries = new java.util.LinkedHashMap<>();
    final CosmosClient client = mock(CosmosClient.class);
    final Map<String, CosmosItem> items = new java.util.LinkedHashMap<>();

    FakeCosmos(List<Object> results) {
      CosmosDatabase database = mock(CosmosDatabase.class);
      when(client.database("my-store-app")).thenReturn(database);
      when(database.container(any())).thenAnswer(invocation -> {
        String name = invocation.getArgument(0);
        CosmosContainer container = mock(CosmosContainer.class);
        CosmosItems containerItems = mock(CosmosItems.class);
        when(container.items()).thenReturn(containerItems);
        when(containerItems.query(any(SqlQuerySpec.class))).thenAnswer(query -> {
          queries.computeIfAbsent(name, key -> new ArrayList<>()).add(query.getArgument(0));
          CosmosQueryIterator iterator = mock(CosmosQueryIterator.class);
          when(iterator.fetchAll()).thenReturn(FeedResponse.of(results));
          return iterator;
        });
        when(container.item(any(), any())).thenAnswer(item -> items.get(name + "/" + item.getArgument(0)));
        return container;
      });
    }
  }

  @Test
  void counts_the_documents_of_each_container() {
    // SELECT VALUE COUNT(1): the REST client's cross-partition pipeline runs VALUE aggregates only (TypeScript's
    // SELECT COUNT(1) as total is a non-VALUE aggregate); the counts are the same
    FakeCosmos cosmos = new FakeCosmos(List.of(7));
    AzureCounters counters = new AzureCounters("my-store", cosmos.client);
    assertThat(counters.subscriptions()).isEqualTo(7);
    assertThat(counters.connections()).isEqualTo(7);
    assertThat(counters.events()).isEqualTo(7);
    assertThat(counters.readModels("CartReadModel")).isEqualTo(7);
    assertThat(cosmos.queries).isEqualTo(Map.of(
        "my-store-app-subscriptions-store", List.of(SqlQuerySpec.of("SELECT VALUE COUNT(1) FROM c")),
        "my-store-app-connections-store", List.of(SqlQuerySpec.of("SELECT VALUE COUNT(1) FROM c")),
        "my-store-app-events-store", List.of(SqlQuerySpec.of("SELECT VALUE COUNT(1) FROM c WHERE c.kind=\"event\"")),
        "my-store-app-CartReadModel", List.of(SqlQuerySpec.of("SELECT VALUE COUNT(1) FROM c"))));
  }

  @Test
  void counts_minus_one_when_the_query_returns_nothing() {
    assertThat(new AzureCounters("my-store", new FakeCosmos(List.of()).client).events()).isEqualTo(-1);
  }

  @Test
  void queries_events_and_read_models() {
    FakeCosmos cosmos = new FakeCosmos(List.of(Map.of("id", "e1")));
    CosmosItem missing = mock(CosmosItem.class);
    when(missing.read()).thenReturn(ItemResponse.of(404, null));
    cosmos.items.put("my-store-app-CartReadModel/cart-1", missing);
    AzureQueries queries = new AzureQueries("my-store", cosmos.client);
    assertThat(queries.events("Cart-cart-1-event")).containsExactly(Map.of("id", "e1"));
    assertThat(cosmos.queries.get("my-store-app-events-store")).containsExactly(SqlQuerySpec.of(
        "SELECT * from c WHERE c.entityTypeName_entityID_kind = @eventId order by c.createdAt DESC",
        SqlParameter.of("@eventId", "Cart-cart-1-event")));
    List<Map<String, Object>> readModels = queries.readModels("cart-1", "CartReadModel");
    assertThat(readModels).hasSize(1);
    assertThat(readModels.get(0)).isNull();
  }
}
