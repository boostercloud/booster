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
    void includesMatchesItemsPartiallyAndDecidesAlone() {
      assertThat(matches("items", filter("includes", filter("sku", "s2")))).isTrue();
      assertThat(matches("items", filter("includes", filter("sku", "s2", "quantity", 1)))).isFalse();
      assertThat(matches("tags", filter("includes", "a"))).isTrue();
      assertThat(matches("status", filter("includes", "o"))).isFalse();
      // The operators after includes are not evaluated
      assertThat(matches("tags", filter("includes", "a", "eq", "never"))).isTrue();
      assertThatThrownBy(() -> matches("items", filter("includes", null)))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nestedPropertiesAreFilteredAndOnlyTheFirstOneDecides() {
      assertThat(matches("shipping", filter("city", filter("eq", "Madrid")))).isTrue();
      assertThat(matches("shipping", filter("city", filter("eq", "Paris")))).isFalse();
      assertThat(matches("shipping", filter("city", filter("eq", "Madrid"), "zip", filter("eq", "nope")))).isTrue();
      assertThatThrownBy(() -> matches("missing", filter("city", filter("eq", "Madrid"))))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Cannot read properties of undefined (reading 'city')");
    }

    @Test
    void otherKeysWithScalarOperandsAreIgnored() {
      assertThat(matches("missing", filter("isDefined", true))).isTrue();
      assertThat(matches("status", filter("isDefined", false))).isTrue();
    }

    @Test
    void onlyTheFirstTopLevelPropertyIsEvaluated() {
      assertThat(matches("status", filter("eq", "open"), "total", filter("eq", 0))).isTrue();
      assertThat(matches("total", filter("eq", 0), "status", filter("eq", "open"))).isFalse();
    }

    @Test
    void combinatorsAreReadAsPropertiesAndFail() {
      assertThatThrownBy(() -> matches("and", List.of(filter("status", filter("eq", "open")))))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Cannot read properties of undefined (reading '0')");
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
