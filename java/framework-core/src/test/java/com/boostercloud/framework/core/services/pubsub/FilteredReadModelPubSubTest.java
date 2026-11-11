package com.boostercloud.framework.core.services.pubsub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.core.BoosterAuthorizer;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.concepts.ReadModelBeforeFunction;
import com.boostercloud.framework.types.concepts.ReadModelMetadata;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.ReadModelRequestEnvelope;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** {@code services/pub-sub/*.ts} (no TypeScript tests; behaviour from the source, spec 07 §3.6). */
class FilteredReadModelPubSubTest {

  public record Item(String sku, int quantity) {}

  public record CartReadModel(UUID id, String status, double total, List<String> tags, List<Item> items,
      Map<String, Object> shipping) {}

  public record OtherReadModel(UUID id, String status) {}

  public record OwnedCart(UUID id, String userId) {}

  private static final CartReadModel CART = new CartReadModel(UUID.of("cart-1"), "open", 10.5, List.of("a", "b"),
      List.of(new Item("s1", 1), new Item("s2", 2)), Map.of("city", "Madrid", "zip", "28001"));

  private static Map<String, Object> filter(Object... keysAndValues) {
    Map<String, Object> filter = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      filter.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return filter;
  }

  private static boolean matches(Object... keysAndValues) {
    return FilteredReadModelPubSub.filterReadModel(CART, filter(keysAndValues));
  }

  @Nested
  class TheFilterEvaluator {
    @Test
    void noFiltersMatchEverything() {
      assertThat(FilteredReadModelPubSub.filterReadModel(CART, null)).isTrue();
      assertThat(matches()).isTrue();
    }

    @Test
    void eqAndNeAreStrict() {
      assertThat(matches("id", filter("eq", "cart-1"))).isTrue();
      assertThat(matches("id", filter("eq", "cart-2"))).isFalse();
      assertThat(matches("total", filter("eq", 10.5))).isTrue();
      assertThat(matches("total", filter("eq", "10.5"))).isFalse();
      assertThat(matches("status", filter("ne", "closed"))).isTrue();
      assertThat(matches("status", filter("ne", "open"))).isFalse();
      // Objects and arrays compare by reference
      assertThat(matches("tags", filter("eq", List.of("a", "b")))).isFalse();
    }

    @Test
    void eqNullDoesNotMatchAMissingProperty() {
      assertThat(matches("missing", filter("eq", null))).isFalse();
      assertThat(matches("missing", filter("ne", null))).isTrue();
    }

    @Test
    void relationalOperatorsCompareLikeJavaScript() {
      assertThat(matches("total", filter("gt", 10))).isTrue();
      assertThat(matches("total", filter("gt", 11))).isFalse();
      assertThat(matches("total", filter("gte", 10.5))).isTrue();
      assertThat(matches("total", filter("lt", 11))).isTrue();
      assertThat(matches("total", filter("lte", 10))).isFalse();
      assertThat(matches("total", filter("gt", 10, "lt", 10.6))).isTrue();
      assertThat(matches("total", filter("gt", 10, "lt", 10.5))).isFalse();
      assertThat(matches("total", filter("gt", "9"))).isTrue();
      assertThat(matches("status", filter("gt", "a"))).isTrue();
      assertThat(matches("status", filter("lt", "a"))).isFalse();
    }

    @Test
    void aMissingPropertyPassesTheRelationalOperators() {
      assertThat(matches("missing", filter("lt", 1))).isTrue();
      assertThat(matches("missing", filter("gt", 1))).isTrue();
      assertThat(matches("missing", filter("gte", 1, "lte", 0))).isTrue();
      assertThat(matches("status", filter("gt", 1))).isTrue();
    }

    @Test
    void inChecksTheOperandIncludesTheValue() {
      assertThat(matches("status", filter("in", List.of("open", "closed")))).isTrue();
      assertThat(matches("status", filter("in", List.of("closed")))).isFalse();
      assertThat(matches("total", filter("in", List.of(1, 10.5)))).isTrue();
      assertThat(matches("status", filter("in", "is it open?"))).isTrue();
      assertThatThrownBy(() -> matches("status", filter("in", 3)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("value.includes is not a function");
    }

    @Test
    void containsWorksOnStringsAndArrays() {
      assertThat(matches("status", filter("contains", "pe"))).isTrue();
      assertThat(matches("tags", filter("contains", "b"))).isTrue();
      assertThat(matches("tags", filter("contains", "c"))).isFalse();
      assertThat(matches("total", filter("contains", "1"))).isFalse();
    }

    @Test
    void beginsWithWorksOnStrings() {
      assertThat(matches("status", filter("beginsWith", "op"))).isTrue();
      assertThat(matches("status", filter("beginsWith", "pe"))).isFalse();
      assertThat(matches("tags", filter("beginsWith", "a"))).isFalse();
    }

    @Test
    void regexAndIRegexSearchStrings() {
      assertThat(matches("status", filter("regex", "^o.e"))).isTrue();
      assertThat(matches("status", filter("regex", "^O"))).isFalse();
      assertThat(matches("status", filter("iRegex", "^O"))).isTrue();
      assertThat(matches("status", filter("regex", "pe"))).isTrue();
      assertThat(matches("total", filter("regex", "1"))).isFalse();
    }

    @Test
    void aPatternThatBacktracksCatastrophicallyFailsInsteadOfPinningTheThread() {
      CartReadModel cart = new CartReadModel(UUID.of("cart-1"), "o".repeat(40) + "!", 1, List.of(), List.of(),
          Map.of());
      org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(30), () ->
          assertThatThrownBy(() -> FilteredReadModelPubSub.filterReadModel(cart,
              filter("status", filter("regex", "^(.*o){20}$"))))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessage("The regular expression /^(.*o){20}$/ is too complex to evaluate against a value of 41 "
                  + "characters; simplify it"));
      assertThat(FilteredReadModelPubSub.filterReadModel(cart, filter("status", filter("iRegex", "^O+!$")))).isTrue();
    }

    @Test
    void includesMatchesItemsPartially() {
      assertThat(matches("items", filter("includes", filter("sku", "s2")))).isTrue();
      assertThat(matches("items", filter("includes", filter("sku", "s2", "quantity", 1)))).isFalse();
      assertThat(matches("tags", filter("includes", "a"))).isTrue();
      assertThat(matches("status", filter("includes", "o"))).isFalse();
      assertThatThrownBy(() -> matches("items", filter("includes", null)))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theOperatorsAfterIncludesAreEvaluatedToo() {
      // TypeScript returns the result of `includes` and ignores the operators after it
      assertThat(matches("tags", filter("includes", "a", "eq", "never"))).isFalse();
      assertThat(matches("tags", filter("includes", "a", "contains", "b"))).isTrue();
    }

    @Test
    void everyNestedPropertyMustMatch() {
      assertThat(matches("shipping", filter("city", filter("eq", "Madrid")))).isTrue();
      assertThat(matches("shipping", filter("city", filter("eq", "Paris")))).isFalse();
      // TypeScript only evaluates the first nested property
      assertThat(matches("shipping", filter("city", filter("eq", "Madrid"), "zip", filter("eq", "nope")))).isFalse();
      assertThat(matches("shipping", filter("city", filter("eq", "Madrid"), "zip", filter("eq", "28001")))).isTrue();
      assertThatThrownBy(() -> matches("missing", filter("city", filter("eq", "Madrid"))))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Cannot read properties of undefined (reading 'city')");
    }

    @Test
    void isDefinedChecksThatThePropertyHasAValue() {
      // TypeScript ignores isDefined; the providers store no nulls, so a null property is not defined either
      CartReadModel withoutStatus = new CartReadModel(UUID.of("cart-2"), null, 0, List.of(), List.of(), Map.of());
      assertThat(matches("status", filter("isDefined", true))).isTrue();
      assertThat(matches("status", filter("isDefined", false))).isFalse();
      assertThat(matches("missing", filter("isDefined", true))).isFalse();
      assertThat(matches("missing", filter("isDefined", false))).isTrue();
      assertThat(FilteredReadModelPubSub.filterReadModel(withoutStatus, filter("status", filter("isDefined", true))))
          .isFalse();
      assertThat(matches("status", filter("isDefined", true, "eq", "closed"))).isFalse();
    }

    @Test
    void everyTopLevelPropertyMustMatch() {
      // TypeScript only evaluates the first top-level property
      assertThat(matches("status", filter("eq", "open"), "total", filter("eq", 0))).isFalse();
      assertThat(matches("total", filter("eq", 0), "status", filter("eq", "open"))).isFalse();
      assertThat(matches("status", filter("eq", "open"), "total", filter("eq", 10.5))).isTrue();
    }

    @Test
    void andOrAndNotCombineFilters() {
      Map<String, Object> open = filter("status", filter("eq", "open"));
      Map<String, Object> closed = filter("status", filter("eq", "closed"));
      Map<String, Object> expensive = filter("total", filter("gt", 100));
      assertThat(matches("and", List.of(open, filter("total", filter("gt", 10))))).isTrue();
      assertThat(matches("and", List.of(open, expensive))).isFalse();
      assertThat(matches("or", List.of(closed, open))).isTrue();
      assertThat(matches("or", List.of(closed, expensive))).isFalse();
      assertThat(matches("not", closed)).isTrue();
      assertThat(matches("not", open)).isFalse();
      assertThat(matches("and", List.of(filter("or", List.of(closed, open)), filter("not", expensive)))).isTrue();
      // A combinator does not replace the other properties
      assertThat(matches("or", List.of(open), "id", filter("eq", "cart-2"))).isFalse();
      // Nested property filters accept them too
      assertThat(matches("shipping", filter("or", List.of(filter("city", filter("eq", "Paris")),
          filter("zip", filter("eq", "28001")))))).isTrue();
      assertThat(matches("shipping", filter("not", filter("city", filter("eq", "Madrid"))))).isFalse();
    }

    @Test
    void idsAndOtherValueTypesAreComparedInTheirJsonForm() {
      assertThat(matches("id", filter("eq", UUID.of("cart-1")))).isTrue();
      assertThat(FilteredReadModelPubSub.filterReadModel(Map.of("n", 5), filter("n", filter("eq", 5.0)))).isTrue();
    }
  }

  @Nested
  class AsyncIterator {
    private BoosterConfig config;

    @BeforeEach
    void setUp() {
      config = new BoosterConfig("test");
      register(List.of());
    }

    private void register(List<ReadModelBeforeFunction> before) {
      config.readModels.put("CartReadModel",
          new ReadModelMetadata(CartReadModel.class, List.of(), BoosterAuthorizer.ALLOW_ACCESS, before));
    }

    private static List<Object> drain(Iterator<Object> iterator) {
      List<Object> items = new ArrayList<>();
      iterator.forEachRemaining(items::add);
      return items;
    }

    private ReadModelRequestEnvelope envelope(Map<String, Object> filters) {
      return ReadModelRequestEnvelope.builder().type(CartReadModel.class).className("CartReadModel").filters(filters)
          .currentUser(UserEnvelope.builder().username("user@example.com").build()).version(1).build();
    }

    @Test
    void yieldsTheReadModelsOfTheClassThatPassTheFilter() {
      CartReadModel closed = new CartReadModel(UUID.of("cart-2"), "closed", 1, List.of(), List.of(), Map.of());
      OtherReadModel other = new OtherReadModel(UUID.of("cart-1"), "open");
      FilteredReadModelPubSub pubSub = new FilteredReadModelPubSub(List.of(CART, other, closed));

      assertThat(drain(pubSub.asyncIterator(envelope(filter("status", filter("eq", "open"))), config))).containsExactly(CART);
      assertThat(drain(pubSub.asyncIterator(envelope(filter()), config))).containsExactly(CART, closed);
    }

    @Test
    void appliesTheBeforeHooksWithTheEnvelopeUser() {
      List<List<Object>> calls = new ArrayList<>();
      ReadModelBeforeFunction onlyMine = (request, user) -> {
        calls.add(Arrays.asList(request, user));
        return request.toBuilder().filters(filter("id", filter("eq", "cart-2"))).build();
      };
      register(List.of(onlyMine));
      CartReadModel mine = new CartReadModel(UUID.of("cart-2"), "closed", 1, List.of(), List.of(), Map.of());
      ReadModelRequestEnvelope request = envelope(filter());

      List<Object> result = drain(new FilteredReadModelPubSub(List.of(CART, mine)).asyncIterator(request, config));

      assertThat(result).containsExactly(mine);
      assertThat(calls).containsExactly(Arrays.asList(request, request.currentUser()));
    }

    @Test
    void usesTheClassNameOfTheTransformedEnvelope() {
      config.readModels.put("OtherReadModel",
          new ReadModelMetadata(OtherReadModel.class, List.of(), BoosterAuthorizer.ALLOW_ACCESS, List.of()));
      register(List.of((request, user) -> request.toBuilder().type(OtherReadModel.class).className("OtherReadModel").build()));
      OtherReadModel other = new OtherReadModel(UUID.of("o"), "open");

      assertThat(drain(new FilteredReadModelPubSub(List.of(CART, other)).asyncIterator(envelope(filter()), config)))
          .containsExactly(other);
    }

    @Test
    void aValidatingBeforeHookCannotBeBypassedWithAnotherFilterProperty() {
      // website/docs/06_graphql.md: a hook that rejects requests whose userId filter is not the current user
      ReadModelBeforeFunction validateUser = (request, user) -> {
        Object userIdFilter = request.filters() == null ? null : request.filters().get("userId");
        if (!(userIdFilter instanceof Map<?, ?> userId) || !user.username().equals(userId.get("eq"))) {
          throw new IllegalStateException("not authorized");
        }
        return request;
      };
      config.readModels.put("OwnedCart",
          new ReadModelMetadata(OwnedCart.class, List.of(), BoosterAuthorizer.ALLOW_ACCESS, List.of(validateUser)));
      OwnedCart alices = new OwnedCart(UUID.of("cart-a"), "alice");
      OwnedCart bobs = new OwnedCart(UUID.of("cart-b"), "bob");
      FilteredReadModelPubSub pubSub = new FilteredReadModelPubSub(List.of(alices, bobs));

      // The key order of GraphQL input objects follows the declaration order of the filter type: id before userId
      ReadModelRequestEnvelope request = ReadModelRequestEnvelope.builder().type(OwnedCart.class).className("OwnedCart")
          .filters(filter("id", filter("ne", ""), "userId", filter("eq", "alice")))
          .currentUser(UserEnvelope.builder().username("alice").build()).version(1).build();

      assertThat(drain(pubSub.asyncIterator(request, config))).containsExactly(alices);
      assertThatThrownBy(() -> pubSub.asyncIterator(request.toBuilder().filters(filter("id", filter("ne", ""))).build(),
          config)).hasMessage("not authorized");
    }

    @Test
    void filtersAddedByABeforeHookAfterTheClientOnesApplyToo() {
      register(List.of((request, user) -> {
        Map<String, Object> filters = new LinkedHashMap<>(request.filters());
        filters.put("status", filter("eq", "closed"));
        return request.toBuilder().filters(filters).build();
      }));
      CartReadModel closed = new CartReadModel(UUID.of("cart-2"), "closed", 1, List.of(), List.of(), Map.of());

      List<Object> result = drain(new FilteredReadModelPubSub(List.of(CART, closed))
          .asyncIterator(envelope(filter("id", filter("ne", ""))), config));

      assertThat(result).containsExactly(closed);
    }

    @Test
    void failsForAnUnknownReadModel() {
      ReadModelRequestEnvelope request = ReadModelRequestEnvelope.builder().className("Nope").version(1).build();

      assertThatThrownBy(() -> new FilteredReadModelPubSub(List.of()).asyncIterator(request, config))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @Test
  void theNoopPubSubYieldsNothing() {
    assertThat(new NoopReadModelPubSub().asyncIterator(null, null).hasNext()).isFalse();
    assertThat(new NoopReadModelPubSub()).isEqualTo(new NoopReadModelPubSub());
  }

  /** Records what a publisher emits. */
  static class RecordingSubscriber implements Flow.Subscriber<Object> {
    final List<Object> items = new ArrayList<>();
    Flow.Subscription subscription;
    Throwable error;
    boolean completed;
    final long initialRequest;

    RecordingSubscriber(long initialRequest) {
      this.initialRequest = initialRequest;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      this.subscription = subscription;
      if (initialRequest > 0) {
        subscription.request(initialRequest);
      }
    }

    @Override
    public void onNext(Object item) {
      items.add(item);
    }

    @Override
    public void onError(Throwable throwable) {
      error = throwable;
    }

    @Override
    public void onComplete() {
      completed = true;
    }
  }

  @Nested
  class ThePublisher {
    @Test
    void emitsEveryElementAndCompletes() {
      RecordingSubscriber subscriber = new RecordingSubscriber(Long.MAX_VALUE);

      ReadModelPubSub.toPublisher(List.of(1, 2, 3).iterator()).subscribe(subscriber);

      assertThat(subscriber.items).containsExactly(1, 2, 3);
      assertThat(subscriber.completed).isTrue();
    }

    @Test
    void honoursTheDemand() {
      RecordingSubscriber subscriber = new RecordingSubscriber(1);

      ReadModelPubSub.toPublisher(List.of(1, 2, 3).iterator()).subscribe(subscriber);
      assertThat(subscriber.items).containsExactly(1);
      assertThat(subscriber.completed).isFalse();

      subscriber.subscription.request(5);
      assertThat(subscriber.items).containsExactly(1, 2, 3);
      assertThat(subscriber.completed).isTrue();
    }

    @Test
    void completesAnEmptyIteratorWithoutDemand() {
      RecordingSubscriber subscriber = new RecordingSubscriber(0);

      ReadModelPubSub.toPublisher(List.of().iterator()).subscribe(subscriber);

      assertThat(subscriber.completed).isTrue();
    }

    @Test
    void stopsWhenCancelled() {
      RecordingSubscriber subscriber = new RecordingSubscriber(1) {
        @Override
        public void onNext(Object item) {
          super.onNext(item);
          subscription.cancel();
          subscription.request(10);
        }
      };

      ReadModelPubSub.toPublisher(List.of(1, 2, 3).iterator()).subscribe(subscriber);

      assertThat(subscriber.items).containsExactly(1);
      assertThat(subscriber.completed).isFalse();
    }

    @Test
    void rejectsNonPositiveRequestsAndASecondSubscriber() {
      RecordingSubscriber invalid = new RecordingSubscriber(0);
      Flow.Publisher<Object> publisher = ReadModelPubSub.toPublisher(List.of(1).iterator());
      publisher.subscribe(invalid);
      invalid.subscription.request(0);
      RecordingSubscriber second = new RecordingSubscriber(1);
      publisher.subscribe(second);

      assertThat(invalid.error).isInstanceOf(IllegalArgumentException.class);
      assertThat(second.error).isInstanceOf(IllegalStateException.class);
    }
  }
}
