package com.boostercloud.framework.provider.local.library;

import static com.boostercloud.framework.provider.local.Raw.map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.provider.local.Mocks;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.EventParametersFilterByEntity;
import com.boostercloud.framework.types.envelope.EventParametersFilterByType;
import com.boostercloud.framework.types.envelope.EventSearchResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests of {@code events-searcher-builder.ts} (not covered by the TypeScript tests). */
class EventsSearcherBuilderTest {

  @Test
  void timeFiltersAreInclusiveAndOptional() {
    assertThat(EventsSearcherBuilder.buildFiltersForByTime("a", "b")).isEqualTo(map("createdAt", map("$gte", "a",
        "$lte", "b")));
    assertThat(EventsSearcherBuilder.buildFiltersForByTime("a", null)).isEqualTo(map("createdAt", map("$gte", "a")));
    assertThat(EventsSearcherBuilder.buildFiltersForByTime("", "b")).isEqualTo(map("createdAt", map("$lte", "b")));
    assertThat(EventsSearcherBuilder.buildFiltersForByTime(null, null)).isEqualTo(map());
  }

  @Test
  void filtersByEntityOrType() {
    assertThat(EventsSearcherBuilder.buildFiltersForByFilters(new EventParametersFilterByEntity("Cart", "c1", null, null,
        null))).isEqualTo(map("entityTypeName", "Cart", "entityID", "c1"));
    assertThat(EventsSearcherBuilder.buildFiltersForByFilters(new EventParametersFilterByEntity("Cart", "", null, null,
        null))).isEqualTo(map("entityTypeName", "Cart"));
    assertThat(EventsSearcherBuilder.buildFiltersForByFilters(new EventParametersFilterByType("Changed", null, null,
        null))).isEqualTo(map("typeName", "Changed"));
    assertThatThrownBy(() -> EventsSearcherBuilder.buildFiltersForByFilters(null))
        .hasMessage("Invalid search event query. It is neither an search by \"entity\" nor a search by \"type\"");
  }

  @Test
  void mapsResultsToSearchResponses() {
    assertThat(EventsSearcherBuilder.resultToEventSearchResponse(null)).isEmpty();
    assertThat(EventsSearcherBuilder.resultToEventSearchResponse(List.of())).isEmpty();
    EventEnvelope event = Mocks.event();
    assertThat(EventsSearcherBuilder.resultToEventSearchResponse(List.of(event)))
        .containsExactly(EventSearchResponse.fromEventEnvelope(event));
    assertThat(EventsSearcherBuilder.resultToEventSearchResponse(List.of(map("typeName", "T", "entityTypeName", "E",
        "entityID", "id", "requestID", "r", "createdAt", "c", "value", map(), "deletedAt", "d"))))
        .singleElement().satisfies(response -> {
          assertThat(response.type()).isEqualTo("T");
          assertThat(response.entity()).isEqualTo("E");
          assertThat(response.user()).isNull();
          assertThat(response.deletedAt()).isEqualTo("d");
        });
  }
}
