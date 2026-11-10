package com.boostercloud.integration.endtoend;

import static com.boostercloud.application.tester.Sleep.waitForIt;
import static com.boostercloud.integration.harness.Faker.random;
import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.application.tester.graphql.GraphQLClient;
import com.boostercloud.application.tester.graphql.GraphQLResponse;
import com.boostercloud.framework.common.Promises;
import com.boostercloud.integration.harness.ApplicationUnderTest;
import com.boostercloud.integration.harness.EndToEnd;
import com.boostercloud.integration.helper.AppHelper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Port of {@code integration/provider-unaware/end-to-end/read-model-concurrency.integration.ts}: every projection that
 * writes a read model increments its {@code boosterMetadata.version} by one, also when two projections of the same
 * event write the same read model ({@code OtherConcurrencyReadModel}, projected by {@code id} and by {@code otherId},
 * which are equal here), without losing a write to the optimistic concurrency check.
 *
 * <p>The stored documents are read with the {@code GetConcurrency} command, which returns the raw provider documents
 * ({@code boosterMetadata} included) through the {@code JSON} scalar.
 */
@EndToEnd
@DisplayName("Concurrency end-to-end tests")
class ReadModelConcurrencyIT {

  private static final String ADD_CONCURRENCY = """
      mutation AddConcurrency($id: ID!, $otherId: ID!) {
        AddConcurrency(input: { id: $id, otherId: $otherId })
      }
      """;

  private static final String GET_CONCURRENCY = """
      mutation GetConcurrency($id: ID!, $readModelName: String!) {
        GetConcurrency(input: { id: $id, readModelName: $readModelName })
      }
      """;

  private final ApplicationUnderTest applicationUnderTest;
  private GraphQLClient client;

  ReadModelConcurrencyIT(ApplicationUnderTest applicationUnderTest) {
    this.applicationUnderTest = applicationUnderTest;
  }

  @BeforeAll
  void before() {
    client = applicationUnderTest.graphql().client();
  }

  @Nested
  @Order(1)
  @DisplayName("ReadModels")
  class ReadModels {

    @Nested
    @Order(1)
    @DisplayName("With one projection")
    class WithOneProjection {

      @Test
      @DisplayName("insert and update generate one ReadModel with version 2")
      void insertAndUpdateGenerateOneReadModelWithVersion2() {
        String entityId = random.uuid();

        Map<?, ?> insertedReadModel = addConcurrency(client, entityId, 1, "ConcurrencyReadModel");
        assertThat(insertedReadModel.get("id")).isEqualTo(entityId);
        assertThat(version(insertedReadModel)).isEqualTo(1);

        Map<?, ?> updatedReadModel = addConcurrency(client, entityId, 2, "ConcurrencyReadModel");
        assertThat(updatedReadModel.get("id")).isEqualTo(entityId);
        assertThat(version(updatedReadModel)).isEqualTo(2);
      }
    }

    @Nested
    @Order(2)
    @DisplayName("With two projections for the same ReadModel")
    class WithTwoProjectionsForTheSameReadModel {

      @Test
      @DisplayName("insert and update generate one ReadModel with version 4")
      void insertAndUpdateGenerateOneReadModelWithVersion4() {
        // TypeScript skips the whole describe block on AWS, which is not ported: the gate only documents it
        Assumptions.assumeFalse("AWS".equals(AppHelper.testedProvider()),
            "AWS Provider is not working properly when inserting a ReadModel with two projections");
        String entityId = random.uuid();

        Map<?, ?> insertedReadModel = addConcurrency(client, entityId, 2, "OtherConcurrencyReadModel");
        assertThat(insertedReadModel.get("id")).isEqualTo(entityId);
        assertThat(insertedReadModel.get("otherId")).isEqualTo(entityId);
        assertThat(version(insertedReadModel)).isEqualTo(2);

        Map<?, ?> updatedReadModel = addConcurrency(client, entityId, 4, "OtherConcurrencyReadModel");
        assertThat(updatedReadModel.get("id")).isEqualTo(entityId);
        assertThat(updatedReadModel.get("otherId")).isEqualTo(entityId);
        assertThat(version(updatedReadModel)).isEqualTo(4);
      }
    }

    @Nested
    @Order(3)
    @DisplayName("With concurrent events of the same entity (not in TypeScript)")
    class WithConcurrentEventsOfTheSameEntity {

      @Test
      @DisplayName("every event bumps the version of the ReadModel once, none is lost to the optimistic concurrency check")
      void everyEventBumpsTheVersionOfTheReadModelOnce() {
        String entityId = random.uuid();
        int events = 5;

        Promises.runAll(IntStream.range(0, events)
            .<Callable<GraphQLResponse>>mapToObj(i -> () -> client.mutate(ADD_CONCURRENCY,
                Map.of("id", entityId, "otherId", entityId)))
            .toList());

        GraphQLResponse result = waitForIt(
            () -> client.mutate(GET_CONCURRENCY, Map.of("id", entityId, "readModelName", "ConcurrencyReadModel")),
            response -> response != null && response.get("GetConcurrency") instanceof List<?> readModels
                && withVersion(readModels, events).isPresent()
                ? true
                : "ConcurrencyReadModel " + entityId + " is not at version " + events + " yet: "
                    + (response == null ? null : response.get("GetConcurrency")),
            1000, 120_000);

        List<?> readModels = (List<?>) result.get("GetConcurrency");
        assertThat(readModels).as("one stored read model").hasSize(1);
        assertThat(version((Map<?, ?>) readModels.getFirst())).isEqualTo(events);
        // two projections per event (by id and by otherId, which are equal): two version bumps per event
        Map<?, ?> other = addConcurrency(client, entityId, 2 * (events + 1), "OtherConcurrencyReadModel");
        assertThat(other.get("otherId")).isEqualTo(entityId);
      }
    }
  }

  /**
   * {@code addConcurrency(client, entityId, expectedVersion, readModelName)}: stores the entity and waits until the
   * provider holds the read model with the expected version.
   */
  private static Map<?, ?> addConcurrency(
      GraphQLClient client, String entityId, int expectedVersion, String readModelName) {
    client.mutate(ADD_CONCURRENCY, Map.of("id", entityId, "otherId", entityId));

    GraphQLResponse mutateResult = waitForIt(
        () -> client.mutate(GET_CONCURRENCY, Map.of("id", entityId, "readModelName", readModelName)),
        result -> result != null && result.get("GetConcurrency") instanceof List<?> readModels
            && !readModels.isEmpty() && readModels.getFirst() != null
            && withVersion(readModels, expectedVersion).isPresent());

    Map<?, ?> concurrency = withVersion((List<?>) mutateResult.get("GetConcurrency"), expectedVersion).orElseThrow();
    assertThat(concurrency.get("id")).isEqualTo(entityId);
    return concurrency;
  }

  /** {@code GetConcurrency.find((value) => value.boosterMetadata?.version === expectedVersion)}. */
  private static Optional<Map<?, ?>> withVersion(List<?> readModels, int expectedVersion) {
    return readModels.stream()
        .filter(Map.class::isInstance)
        .<Map<?, ?>>map(Map.class::cast)
        .filter(readModel -> Objects.equals(version(readModel), expectedVersion))
        .findFirst();
  }

  /** {@code readModel.boosterMetadata?.version}, as an integer when it is an integral number. */
  private static Object version(Map<?, ?> readModel) {
    if (!(readModel.get("boosterMetadata") instanceof Map<?, ?> metadata)) {
      return null;
    }
    Object version = metadata.get("version");
    return version instanceof Number number && number.doubleValue() == number.intValue() ? number.intValue() : version;
  }
}
