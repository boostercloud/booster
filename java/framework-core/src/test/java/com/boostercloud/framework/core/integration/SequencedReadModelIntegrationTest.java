package com.boostercloud.framework.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.integration.app.common.Rating;
import com.boostercloud.framework.core.integration.app.readmodels.ProductReviewReadModel;
import com.boostercloud.framework.types.Dates;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A sequenced read model ({@code @SequencedBy}): one row per sequence key, by-id queries returning every row or the
 * one of a sequence key, and the GraphQL mapping of enums, optional properties and dates (inputs, outputs, filters
 * and sorting).
 */
class SequencedReadModelIntegrationTest extends IntegrationTestSupport {

  private static final String REVIEW = """
      mutation ($rating: Rating!, $comment: String) {
        ReviewProduct(input: {productId: "product-1", rating: $rating, comment: $comment})
      }""";

  private final List<String> reviews = new ArrayList<>();

  private String review(String rating, String comment, String at) {
    Dates.useClock(Clock.fixed(Instant.parse(at), ZoneOffset.UTC));
    String timeKey = (String) data(graphQL(REVIEW, map("rating", rating, "comment", comment), TestTokens.customer()))
        .get("ReviewProduct");
    Dates.resetClock();
    return timeKey;
  }

  @BeforeEach
  void review() {
    reviews.add(review("FIVE", "Great", "2024-03-01T10:00:00.000Z"));
    reviews.add(review("ONE", null, "2024-03-02T10:00:00.000Z"));
    reviews.add(review("FIVE", "Again", "2024-03-03T10:00:00.000Z"));
  }

  @Test
  void everyReviewIsARowOfTheSameId() {
    assertThat(provider.readModels().documents("ProductReviewReadModel")).hasSize(3)
        .allSatisfy(document -> assertThat(document).containsEntry("id", "product-1"));

    Map<String, Object> all = graphQL("""
        { ProductReviewReadModel(id: "product-1") { reviewedAt rating comment reviewedOn reviewer } }""");

    assertThat(listAt(data(all), "ProductReviewReadModel")).extracting(row -> row.get("comment"))
        .containsExactlyInAnyOrder("Great", null, "Again");
    assertThat(listAt(data(all), "ProductReviewReadModel")).allSatisfy(row -> {
      assertThat(row.get("reviewer")).isEqualTo("customer@store.test");
      assertThat(reviews).contains((String) row.get("reviewedAt"));
    });
  }

  @Test
  void theSequenceKeySelectsOneRow() {
    Map<String, Object> one = graphQL("query ($at: ID) { ProductReviewReadModel(id: \"product-1\", reviewedAt: $at) "
        + "{ rating comment reviewedOn } }", map("at", reviews.get(1)), null);

    assertThat(data(one)).isEqualTo(Map.of("ProductReviewReadModel", List.of(
        map("rating", "ONE", "comment", null, "reviewedOn", "2024-03-02T10:00:00.000Z"))));
  }

  @Test
  void enumsAndDatesCanBeFilteredAndSorted() {
    Map<String, Object> result = graphQL("""
        { ListProductReviewReadModels(filter: {rating: {eq: FIVE}, reviewedOn: {gte: "2024-03-01T12:00:00.000Z"}},
            sortBy: {reviewedOn: DESC}) { items { comment reviewedOn } count } }""");
    Map<String, Object> sorted = graphQL("""
        { ListProductReviewReadModels(filter: {}, sortBy: {reviewedOn: DESC}) { items { rating } } }""");

    assertThat(mapAt(data(result), "ListProductReviewReadModels")).isEqualTo(map(
        "items", List.of(map("comment", "Again", "reviewedOn", "2024-03-03T10:00:00.000Z")), "count", 1));
    assertThat(listAt(data(sorted), "ListProductReviewReadModels", "items")).extracting(row -> row.get("rating"))
        .containsExactly("FIVE", "ONE", "FIVE");
  }

  @Test
  void theRowsAreInstancesWithTheirJavaTypes() {
    List<ProductReviewReadModel> rows = Booster.readModel(ProductReviewReadModel.class)
        .filter(Map.of("rating", Map.of("eq", Rating.ONE))).search();

    assertThat(rows).singleElement().satisfies(row -> {
      assertThat(row.comment()).isEqualTo(Optional.empty());
      assertThat(row.reviewedOn()).isEqualTo(Instant.parse("2024-03-02T10:00:00.000Z"));
      assertThat(row.reviewedAt().value()).isEqualTo(reviews.get(1));
    });
  }

  @Test
  void theEntityCountsTheReviews() {
    Map<String, Object> events = graphQL("{ eventsByEntity(entity: ProductReview, entityID: \"product-1\") { type value } }");

    assertThat(listAt(data(events), "eventsByEntity")).hasSize(3).first()
        .satisfies(event -> assertThat(mapAt(event, "value")).containsEntry("comment", "Again")
            .containsEntry("rating", "FIVE").containsEntry("reviewedOn", "2024-03-03T10:00:00.000Z"));
  }
}
