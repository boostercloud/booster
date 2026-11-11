package com.boostercloud.integration.azurecontract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.provider.azure.cosmos.CosmosContainer;
import com.boostercloud.framework.provider.azure.cosmos.CosmosException;
import com.boostercloud.framework.provider.azure.cosmos.FeedOptions;
import com.boostercloud.framework.provider.azure.cosmos.ItemResponse;
import com.boostercloud.framework.provider.azure.cosmos.RequestOptions;
import com.boostercloud.framework.provider.azure.cosmos.SqlParameter;
import com.boostercloud.framework.provider.azure.cosmos.SqlQuerySpec;
import com.boostercloud.framework.provider.azure.cosmos.rest.CosmosRestClient;
import com.boostercloud.framework.provider.azure.cosmos.rest.CosmosRetryOptions;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The fake Cosmos DB behaves like the service for the production REST client of {@code framework-provider-azure}: it
 * checks the master-key signature and the partition key, keeps ids and unique keys unique per logical partition,
 * honours {@code If-Match}, and serves single-partition and cross-partition queries (through the client's query plan
 * and pipeline).
 */
class FakeAzureServicesTest {

  private final FakeAzureServices services = FakeAzureServices.shared();
  private CosmosContainer container;

  @BeforeEach
  void provision() {
    services.reset();
    services.createContainer("db", "things", "/pk", "/unique");
    container = CosmosRestClient.create(services.cosmosConnectionString(), CosmosRetryOptions.defaults())
        .database("db").container("things");
  }

  @AfterEach
  void forget() {
    services.reset();
  }

  @Test
  void storesDocumentsWithSystemPropertiesAndAnswersPointReads() {
    ItemResponse created = container.items().create(Map.of("id", "1", "pk", "a", "unique", "u1", "value", 1));

    assertThat(created.statusCode()).isEqualTo(201);
    assertThat(created.resource()).containsKeys("_rid", "_self", "_etag", "_attachments", "_ts")
        .containsEntry("value", 1);
    assertThat(created.etag()).isEqualTo(created.resource().get("_etag"));
    assertThat(container.item("1", "a").read().resource()).isEqualTo(created.resource());
    assertThat(container.item("1", "b").read().statusCode()).isEqualTo(404);
    assertThat(services.changeFeed("db", "things")).singleElement()
        .satisfies(document -> assertThat(document).containsKey("_lsn"));
    assertThat(services.failures()).isEmpty();
  }

  @Test
  void conflictsAndPreconditionsAreTheServiceOnes() {
    ItemResponse created = container.items().create(Map.of("id", "1", "pk", "a", "unique", "u1"));

    assertThatThrownBy(() -> container.items().create(Map.of("id", "1", "pk", "a", "unique", "u2")))
        .isInstanceOfSatisfying(CosmosException.class, error -> assertThat(error.code()).isEqualTo(409));
    assertThatThrownBy(() -> container.items().create(Map.of("id", "2", "pk", "a", "unique", "u1")))
        .isInstanceOfSatisfying(CosmosException.class, error -> assertThat(error.code()).isEqualTo(409));
    // Ids and unique keys are unique per logical partition only
    assertThat(container.items().create(Map.of("id", "1", "pk", "b", "unique", "u1")).statusCode()).isEqualTo(201);

    String etag = (String) created.resource().get("_etag");
    ItemResponse updated = container.items().upsert(Map.of("id", "1", "pk", "a", "unique", "u1", "v", 2),
        RequestOptions.withAccessCondition(RequestOptions.AccessCondition.ifMatch(etag)));
    assertThat(updated.statusCode()).isEqualTo(200);
    assertThatThrownBy(() -> container.items().upsert(Map.of("id", "1", "pk", "a", "unique", "u1", "v", 3),
        RequestOptions.withAccessCondition(RequestOptions.AccessCondition.ifMatch(etag))))
        .isInstanceOfSatisfying(CosmosException.class, error -> assertThat(error.code()).isEqualTo(412));
    assertThat(container.item("1", "a").read().resource()).containsEntry("v", 2);
  }

  @Test
  void servesSingleAndCrossPartitionQueries() {
    container.items().create(Map.of("id", "1", "pk", "a", "unique", "1", "at", "2026-01-02"));
    container.items().create(Map.of("id", "2", "pk", "b", "unique", "2", "at", "2026-01-03"));
    container.items().create(Map.of("id", "3", "pk", "a", "unique", "3", "at", "2026-01-01"));

    List<Object> partition = container.items().query(SqlQuerySpec.of("SELECT * FROM c WHERE c.pk = @pk ORDER BY c.at",
        SqlParameter.of("@pk", "a")), FeedOptions.forPartition("a")).fetchAll().resources();
    List<Object> ordered = container.items().query(SqlQuerySpec.of("SELECT * FROM c ORDER BY c.at DESC"))
        .fetchAll().resources();
    List<Object> count = container.items().query(SqlQuerySpec.of("SELECT VALUE COUNT(1) FROM c WHERE c.pk = \"a\""))
        .fetchAll().resources();

    assertThat(partition).extracting(document -> (Object) ((Map<?, ?>) document).get("id")).containsExactly("3", "1");
    assertThat(ordered).extracting(document -> (Object) ((Map<?, ?>) document).get("id")).containsExactly("2", "1", "3");
    assertThat(count).containsExactly(2);
    assertThat(services.requests("cosmos")).extracting(FakeAzureServices.Request::summary)
        .contains("POST /dbs/db/colls/things/docs (query plan)", "GET /dbs/db/colls/things/pkranges");
    assertThat(services.failures()).isEmpty();
  }

  @Test
  void refusesWrongSignaturesUnknownContainersAndUnsupportedQueries() {
    CosmosContainer forged = CosmosRestClient.create(services.cosmosConnectionString()
        .replace(FakeAzureServices.COSMOS_KEY, "d3Jvbmcta2V5"), CosmosRetryOptions.defaults())
        .database("db").container("things");
    assertThatThrownBy(() -> forged.item("1", "a").read())
        .isInstanceOfSatisfying(CosmosException.class, error -> assertThat(error.code()).isEqualTo(401));
    CosmosContainer missing = CosmosRestClient.create(services.cosmosConnectionString(), CosmosRetryOptions.defaults())
        .database("db").container("missing");
    // A container Terraform does not create is a failure of the contract, even if the client copes with a 404
    assertThat(missing.item("1", "a").read().statusCode()).isEqualTo(404);
    assertThatThrownBy(() -> container.items().query(SqlQuerySpec.of("SELECT * FROM c WHERE STARTSWITH(c.id, 'a')"),
        FeedOptions.forPartition("a")).fetchAll())
        .isInstanceOfSatisfying(CosmosException.class, error -> assertThat(error.code()).isEqualTo(400));
    assertThat(services.failures()).hasSize(3)
        .anySatisfy(failure -> assertThat(failure).contains("401"))
        .anySatisfy(failure -> assertThat(failure).contains("404 Resource Not Found"))
        .anySatisfy(failure -> assertThat(failure).contains("STARTSWITH"));
  }
}
