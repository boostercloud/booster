package com.boostercloud.framework.provider.azure;

import static com.boostercloud.framework.provider.azure.support.CosmosMocks.obj;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.boostercloud.framework.provider.azure.cosmos.rest.CosmosRestClient;
import com.boostercloud.framework.provider.azure.eventhubs.EventHubProducerClient;
import com.boostercloud.framework.provider.azure.eventhubs.rest.EventHubsRestProducerClient;
import com.boostercloud.framework.provider.azure.library.AzureHttpResponse;
import com.boostercloud.framework.provider.azure.library.HealthAdapter;
import com.boostercloud.framework.provider.azure.library.WebPubSubConnectResponse;
import com.boostercloud.framework.provider.azure.support.CosmosMocks;
import com.boostercloud.framework.provider.azure.webpubsub.WebPubSubServiceClient;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.framework.types.concepts.SequenceKey;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.ReadModelListResult;
import com.boostercloud.framework.types.errors.NotFoundError;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import com.boostercloud.framework.types.rockets.RocketDescriptor;
import com.boostercloud.framework.types.rockets.RocketEnvelope;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Tests of {@code index.ts}: the {@code Provider} factory, the library wiring, the clients and the infrastructure. */
class ProviderTest {

  private final BoosterConfig config = new BoosterConfig("test");

  @AfterEach
  void clearEnvironment() {
    EnvironmentVariables.clearOverrides();
  }

  @Test
  void booterLoadsTheProviderFromTheProviderPackage() {
    config.providerPackage = Provider.PACKAGE;

    ProviderLibrary provider = config.provider();

    assertThat(provider).isInstanceOf(AzureProviderLibrary.class);
    assertThat(Provider.provider(null)).isNotSameAs(Provider.provider(null));
    assertThat(((AzureProviderLibrary) Provider.provider(List.of(new RocketDescriptor("r", null)))).rocketDescriptors())
        .containsExactly(new RocketDescriptor("r", null));
  }

  @Test
  void theInfrastructureLoadFailureIsReportedWithTheTypeScriptMessage() {
    assertThatThrownBy(() -> Provider.provider(List.of()).infrastructure())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("The Azure infrastructure package could not be loaded. The following error was thrown: "
            + "Could not load the infrastructure class com.boostercloud.framework.provider.azure.infrastructure."
            + "Infrastructure")
        .hasMessageContaining("Please ensure that one of the following actions has been done:\n"
            + "  - It has been specified in the \"dependencies\" section of your \"pom.xml\" file. You can do so by "
            + "adding the dependency 'com.boostercloud:booster-framework-provider-azure-infrastructure'\n");
  }

  @Test
  void clientsAreCreatedLazilyFromTheAppSettings() {
    AzureClients unconfigured = AzureClients.fromEnvironment();
    assertThatThrownBy(() -> unconfigured.cosmos().get())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("The Cosmos DB client is not available: the COSMOSDB_CONNECTION_STRING app setting is not set");
    EnvironmentVariables.set("EVENTHUB_CONNECTION_STRING", "");
    EnvironmentVariables.set("EVENTHUB_NAME", "booster_events");
    assertThatThrownBy(() -> unconfigured.producer().get())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("The Event Hubs producer is not available");

    EnvironmentVariables.set("COSMOSDB_CONNECTION_STRING",
        "AccountEndpoint=https://acc.documents.azure.com:443/;AccountKey=a2V5;");
    EnvironmentVariables.set("EVENTHUB_CONNECTION_STRING",
        "Endpoint=sb://ns.servicebus.windows.net/;SharedAccessKeyName=n;SharedAccessKey=k;EntityPath=booster_events");
    AzureClients configured = AzureClients.fromEnvironment();
    assertThat(configured.cosmos().get()).isInstanceOf(CosmosRestClient.class).isSameAs(configured.cosmos().get());
    assertThat(configured.producer().get()).isInstanceOf(EventHubsRestProducerClient.class);
    assertThat(((EventHubsRestProducerClient) configured.producer().get()).eventHubName()).isEqualTo("booster_events");
    EnvironmentVariables.set("COSMOSDB_CONNECTION_STRING", "");
    assertThatThrownBy(() -> AzureClients.fromEnvironment().cosmos().get())
        .hasMessage("Could not parse the provided connection string");
  }

  @Test
  void theLibraryRoutesEveryCallToItsAdapterAndClients() {
    CosmosMocks cosmos = new CosmosMocks();
    WebPubSubServiceClient webPubSub = mock(WebPubSubServiceClient.class);
    AzureProviderLibrary provider = new AzureProviderLibrary(List.of(), AzureClients.of(cosmos.client,
        mock(EventHubProducerClient.class), (connectionString, hub) -> webPubSub,
        (url, method, data) -> new com.boostercloud.framework.common.HttpService.PostResult(200, "")));

    ReadModelListResult<Map<String, Object>> page = provider.readModels().search(config, "CartReadModel",
        obj(), null, null, null, false, null);
    provider.readModels().fetch(config, "CartReadModel", UUID.of("1"), new SequenceKey("time", null));
    provider.connections().sendMessage(config, "c1", obj("type", "complete"));

    assertThat(page).isEqualTo(ReadModelListResult.of(List.of()));
    verify(cosmos.container).item("1", "1");
    verify(webPubSub).sendToConnection("c1", "{\"type\":\"complete\"}", "text/plain");
    assertThat(provider.graphQL().handleResult(null, Map.of("Sec-WebSocket-Protocol", "graphql-ws")))
        .isEqualTo(new WebPubSubConnectResponse("graphql-ws", null, null, null));
    assertThat(provider.graphQL().handleResult(null)).isNull();
    assertThat(((AzureHttpResponse) provider.api().requestFailed(new NotFoundError("x"))).status()).isEqualTo(404);
    assertThat(((AzureHttpResponse) provider.api().requestSucceeded(obj("a", 1))).status()).isEqualTo(200);
    assertThat(((AzureHttpResponse) provider.api().healthRequestResult(List.of(), false)).status()).isEqualTo(503);
    assertThat(provider.rockets().rawToEnvelopes(config, null)).isEqualTo(new RocketEnvelope(null));
    assertThat(provider.sensor().isGraphQLFunctionUp(config)).isTrue();
    assertThat(provider.sensor().graphQLFunctionUrl(config)).isEqualTo("undefined/graphql");
    assertThat(provider.sensor().areRocketFunctionsUp(config)).isEmpty();
    assertThat(provider.sensor().databaseUrls(config)).containsExactly("dbs/new-booster-app-app");
    assertThat(provider.events().rawToEnvelopes(List.of())).isEmpty();
    verify(cosmos.client, org.mockito.Mockito.atLeastOnce()).database(anyString());
    assertThat(HealthAdapter.HttpRequester.DEFAULT).isNotNull();
    verify(cosmos.item).read();
  }
}
