package com.boostercloud.framework.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.core.coreconcepts.touchentity.events.BoosterEntityTouched;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.NonPersistedEventEnvelope;
import com.boostercloud.framework.types.envelope.SuperKindType;
import com.boostercloud.framework.types.provider.ProviderEventsLibrary;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Tests of {@link BoosterTouchEntityHandler} (no TypeScript unit tests exist; spec 06 §3.8 and §5.9). */
class BoosterTouchEntityHandlerTest extends BoosterTestSupport {

  private ProviderEventsLibrary events;
  private BoosterConfig config;

  @BeforeEach
  @SuppressWarnings("deprecation")
  void setUp() {
    config = Booster.config();
    ProviderLibrary provider = mock(ProviderLibrary.class);
    events = mock(ProviderEventsLibrary.class);
    when(provider.events()).thenReturn(events);
    when(events.store(anyList(), any())).thenReturn(List.of());
    config.setProvider(provider);
  }

  @SuppressWarnings("unchecked")
  private List<NonPersistedEventEnvelope> storedEnvelopes(int calls) {
    ArgumentCaptor<List<NonPersistedEventEnvelope>> stored = ArgumentCaptor.forClass(List.class);
    verify(events, times(calls)).store(stored.capture(), org.mockito.ArgumentMatchers.same(config));
    return stored.getAllValues().stream().flatMap(List::stream).toList();
  }

  @Test
  void storesABoosterEntityTouchedEventForTheEntity() {
    BoosterTouchEntityHandler.touchEntity("Cart", UUID.of("42"));

    assertThat(storedEnvelopes(1)).singleElement().satisfies(envelope -> {
      assertThat(envelope.version()).isEqualTo(1);
      assertThat(envelope.kind()).isEqualTo("event");
      assertThat(envelope.superKind()).isEqualTo(SuperKindType.BOOSTER);
      assertThat(envelope.entityID()).isEqualTo(UUID.of("42"));
      assertThat(envelope.entityTypeName()).isEqualTo("Cart");
      assertThat(envelope.typeName()).isEqualTo("BoosterEntityTouched");
      assertThat(envelope.currentUser()).isNull();
      assertThat(envelope.requestID().value()).matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
      assertThat(envelope.value()).isEqualTo(new BoosterEntityTouched("Cart", UUID.of("42")));
      assertThat(BoosterJson.toRawMap(envelope.value())).isEqualTo(Map.of("entityName", "Cart", "entityId", "42"));
    });
  }

  @Test
  void usesANewRequestIdForEveryTouchAndValidatesNothing() {
    BoosterTouchEntityHandler.touchEntity("NotAnEntity", UUID.of("1"));
    BoosterTouchEntityHandler.touchEntity("NotAnEntity", UUID.of("1"));

    List<NonPersistedEventEnvelope> stored = storedEnvelopes(2);
    assertThat(stored).hasSize(2);
    assertThat(stored.get(0).requestID()).isNotEqualTo(stored.get(1).requestID());
  }
}
