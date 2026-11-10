package com.boostercloud.framework.provider.azure.library;

import static com.boostercloud.framework.provider.azure.support.CosmosMocks.obj;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.provider.azure.cosmos.CosmosException;
import com.boostercloud.framework.provider.azure.cosmos.ItemResponse;
import com.boostercloud.framework.provider.azure.cosmos.RequestOptions;
import com.boostercloud.framework.provider.azure.support.CosmosMocks;
import com.boostercloud.framework.provider.azure.types.AzureInvocationContext;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.ReadModelEnvelope;
import com.boostercloud.framework.types.errors.OptimisticConcurrencyUnexpectedVersionError;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Port of {@code test/library/read-model-adapter.test.ts}, plus updates, error mapping, delete and change feed. */
class ReadModelAdapterTest {

  private BoosterConfig config;
  private CosmosMocks cosmos;
  private Map<String, Object> mockReadModel;
  private String mockReadModelName;
  private UUID mockReadModelId;
  private Logger logger;

  @BeforeEach
  void setUp() {
    cosmos = new CosmosMocks();
    config = new BoosterConfig("test");
    logger = mock(Logger.class);
    config.logger = logger;
    mockReadModelName = "ReadModel" + System.nanoTime();
    mockReadModelId = UUID.generate();
    mockReadModel = obj("id", UUID.generate().value(), "some", "object");
  }

  @Nested
  class TheFetchReadModelMethod {

    @Test
    void respondsWithAReadModelWhenItExists() {
      when(cosmos.item.read()).thenReturn(ItemResponse.of(200, obj("id", mockReadModelId.value(), "_etag", "\"e2\"",
          "boosterMetadata", obj("version", 2, "optimisticConcurrencyValue", "\"e1\""))));

      List<Map<String, Object>> result =
          ReadModelAdapter.fetchReadModel(cosmos.client, config, mockReadModelName, mockReadModelId);

      verify(cosmos.client).database(config.resourceNames().applicationStack());
      verify(cosmos.database).container(config.resourceNames().applicationStack() + "-" + mockReadModelName);
      verify(cosmos.container).item(mockReadModelId.value(), mockReadModelId.value());
      assertThat(result).containsExactly(obj("id", mockReadModelId.value(), "_etag", "\"e2\"",
          "boosterMetadata", obj("version", 2, "optimisticConcurrencyValue", "\"e2\"")));
    }

    @Test
    void aMissingReadModelIsAnEmptyList() {
      assertThat(ReadModelAdapter.fetchReadModel(cosmos.client, config, mockReadModelName, mockReadModelId)).isEmpty();
    }
  }

  @Nested
  class TheStoreReadModelMethod {

    @Test
    void savesAReadModel() {
      ReadModelAdapter.storeReadModel(cosmos.client, config, mockReadModelName, mockReadModel);

      verify(cosmos.database).container(config.resourceNames().applicationStack() + "-" + mockReadModelName);
      verify(cosmos.items).create(mockReadModel);
      verify(cosmos.items, never()).upsert(any(), any());
    }

    @Test
    void version1AlsoInsertsAndTheIdIsAString() {
      ReadModelAdapter.storeReadModel(cosmos.client, config, mockReadModelName,
          obj("id", 42, "boosterMetadata", obj("version", 1)));

      verify(cosmos.items).create(obj("id", "42", "boosterMetadata", obj("version", 1)));
    }

    @Test
    void laterVersionsUpsertWithIfMatchOnTheEtag() {
      Map<String, Object> readModel = obj("id", "1",
          "boosterMetadata", obj("version", 2, "optimisticConcurrencyValue", "\"etag-1\""));

      ReadModelAdapter.storeReadModel(cosmos.client, config, mockReadModelName, readModel);

      verify(cosmos.items).upsert(readModel,
          RequestOptions.withAccessCondition(RequestOptions.AccessCondition.ifMatch("\"etag-1\"")));
      verify(cosmos.items, never()).create(any());
    }

    @Test
    void withoutEtagTheConditionIsAStar() {
      Map<String, Object> readModel = obj("id", "1", "boosterMetadata", obj("version", 2, "optimisticConcurrencyValue", ""));

      ReadModelAdapter.storeReadModel(cosmos.client, config, mockReadModelName, readModel);

      verify(cosmos.items).upsert(eq(readModel),
          eq(RequestOptions.withAccessCondition(RequestOptions.AccessCondition.ifMatch("*"))));
    }

    @Test
    void anInsertConflictIsAnOptimisticConcurrencyError() {
      when(cosmos.items.create(any())).thenThrow(CosmosException.of("Entity with the specified id already exists", 409));

      assertThatThrownBy(() -> ReadModelAdapter.storeReadModel(cosmos.client, config, "Cart", obj("id", "1")))
          .isInstanceOf(OptimisticConcurrencyUnexpectedVersionError.class)
          .hasMessage("Entity with the specified id already exists");
      verify(logger).warn("[Booster]|read-model-adapter#insertReadModel: ",
          "Read model Cart insert failed with a conflict failure with id 1 and metadata undefined");
    }

    @Test
    void anUpdatePreconditionFailureIsAnOptimisticConcurrencyError() {
      when(cosmos.items.upsert(any(), any())).thenThrow(CosmosException.of("precondition is not met", 412));
      Map<String, Object> readModel = obj("id", "1", "boosterMetadata", obj("version", 3));

      assertThatThrownBy(() -> ReadModelAdapter.storeReadModel(cosmos.client, config, "Cart", readModel))
          .isInstanceOf(OptimisticConcurrencyUnexpectedVersionError.class)
          .hasMessage("precondition is not met");
      verify(logger).warn("[Booster]|read-model-adapter#updateReadModel: ",
          "Read model Cart update failed with a pre-condition failure with id 1 and metadata {\"version\":3}");
    }

    @Test
    void otherErrorsAreRethrown() {
      CosmosException insertError = CosmosException.of("bad", 400);
      CosmosException updateError = CosmosException.of("conflict on update", 409);
      when(cosmos.items.create(any())).thenThrow(insertError);
      when(cosmos.items.upsert(any(), any())).thenThrow(updateError);

      assertThatThrownBy(() -> ReadModelAdapter.storeReadModel(cosmos.client, config, "Cart", obj("id", "1")))
          .isSameAs(insertError);
      assertThatThrownBy(() -> ReadModelAdapter.storeReadModel(cosmos.client, config, "Cart",
          obj("id", "1", "boosterMetadata", obj("version", 2)))).isSameAs(updateError);
      verify(logger).error("[Booster]|read-model-adapter#insertReadModel: ",
          "[ReadModelAdapter#insertReadModel] Read model insert failed without a conflict failure", insertError);
      verify(logger).error("[Booster]|read-model-adapter#updateReadModel: ",
          "[ReadModelAdapter#updateReadModel] Read model update failed without a pre-condition failure");
    }
  }

  @Nested
  class TheDeleteReadModelMethod {

    @Test
    void deletesByIdAndPartitionKey() {
      ReadModelAdapter.deleteReadModel(cosmos.client, config, "Cart", obj("id", "7"));

      verify(cosmos.container).item("7", "7");
      verify(cosmos.item).delete();
    }

    @Test
    void swallowsEveryErrorAsNotFound() {
      when(cosmos.item.delete()).thenThrow(CosmosException.of("Not found", 404));

      ReadModelAdapter.deleteReadModel(cosmos.client, config, "Cart", obj("id", "7"));

      verify(logger).warn("[Booster]|read-model-adapter#deleteReadModel: ", "Read model to delete Cart ID = 7 not found");
    }

    @Test
    void aNullReadModelIsANoOp() {
      ReadModelAdapter.deleteReadModel(cosmos.client, config, "Cart", null);

      verify(cosmos.client, never()).database(any());
    }
  }

  @Nested
  class TheRawReadModelEventsToEnvelopesMethod {

    @Test
    void stripsTheSystemPropertiesAndKeepsEverythingElse() {
      SubscriptionModel.SubscriptionNotifierInput input = new SubscriptionModel.SubscriptionNotifierInput(
          List.of(obj("id", "42", "cartItems", List.of(), "boosterMetadata", obj("version", 3), "_rid", "r", "_self", "s",
              "_st", 1, "_etag", "e", "_lsn", 2, "_ts", 3, "_attachments", "attachments/")),
          AzureInvocationContext.of("i"), "CartReadModel");

      List<ReadModelEnvelope> envelopes = ReadModelAdapter.rawReadModelEventsToEnvelopes(config, input);

      assertThat(envelopes).containsExactly(new ReadModelEnvelope("CartReadModel", obj("id", "42", "cartItems", List.of(),
          "boosterMetadata", obj("version", 3), "_attachments", "attachments/")));
    }

    @Test
    void unexpectedInputsGiveNothing() {
      assertThat(ReadModelAdapter.rawReadModelEventsToEnvelopes(config, Map.of("documents", List.of()))).isEmpty();
      verify(logger).warn("[Booster]|read-model-adapter#rawReadModelEventsToEnvelopes: ",
          "Unexpected events to be parsed {\"documents\":[]}");
    }
  }
}
