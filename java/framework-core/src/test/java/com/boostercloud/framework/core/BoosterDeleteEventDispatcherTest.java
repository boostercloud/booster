package com.boostercloud.framework.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.core.services.ReadModelStore;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EntitySnapshotEnvelope;
import com.boostercloud.framework.types.envelope.EventDeleteParameters;
import com.boostercloud.framework.types.envelope.EventEnvelope;
import com.boostercloud.framework.types.envelope.SnapshotDeleteParameters;
import com.boostercloud.framework.types.envelope.SuperKindType;
import com.boostercloud.framework.types.provider.ProviderEventsLibrary;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/** Tests of {@link BoosterDeleteEventDispatcher} (no TypeScript unit tests exist; spec 06 §3.7 and §5.9). */
class BoosterDeleteEventDispatcherTest {

  private BoosterConfig config;
  private ProviderEventsLibrary events;
  private ReadModelStore readModelStore;
  private final EventDeleteParameters parameters =
      new EventDeleteParameters("Cart", UUID.of("42"), "2026-11-10T10:00:00.000Z");

  @BeforeEach
  @SuppressWarnings("deprecation")
  void setUp() {
    config = new BoosterConfig("test");
    ProviderLibrary provider = mock(ProviderLibrary.class);
    events = mock(ProviderEventsLibrary.class);
    when(provider.events()).thenReturn(events);
    config.setProvider(provider);
    readModelStore = mock(ReadModelStore.class);
  }

  static EventEnvelope event(String id, String createdAt) {
    return EventEnvelope.builder()
        .id(id)
        .version(1)
        .superKind(SuperKindType.DOMAIN)
        .entityID(UUID.of("42"))
        .entityTypeName("Cart")
        .typeName("CartItemChanged")
        .value(Map.of())
        .requestID(UUID.generate())
        .createdAt(createdAt)
        .build();
  }

  static EntitySnapshotEnvelope snapshot(String id) {
    return EntitySnapshotEnvelope.builder()
        .id(id)
        .version(1)
        .superKind(SuperKindType.DOMAIN)
        .entityID(UUID.of("42"))
        .entityTypeName("Cart")
        .typeName("Cart")
        .value(Map.of("id", "42"))
        .requestID(UUID.generate())
        .snapshottedEventCreatedAt("2026-11-10T10:00:00.000Z")
        .createdAt("2026-11-10T10:00:00.000Z")
        .persistedAt("2026-11-10T10:00:00.100Z")
        .build();
  }

  @Test
  void returnsFalseWhenNoEventIsFound() {
    when(events.findDeletableEvent(config, parameters)).thenReturn(List.of());

    assertThat(BoosterDeleteEventDispatcher.deleteEvent(config, parameters, readModelStore)).isFalse();

    verify(events, never()).findDeletableSnapshot(any(), any());
    verify(events, never()).deleteSnapshot(any(), any());
    verify(events, never()).deleteEvent(any(), any());
    verifyNoInteractions(readModelStore);
  }

  @Test
  void returnsFalseWhenTheProviderReturnsNothing() {
    when(events.findDeletableEvent(config, parameters)).thenReturn(null);

    assertThat(BoosterDeleteEventDispatcher.deleteEvent(config, parameters)).isFalse();
    verify(events, never()).deleteEvent(any(), any());
  }

  @Test
  void unProjectsAndDeletesTheSnapshotsOfEachEventThenDeletesTheEvents() {
    EventEnvelope first = event("e1", "2026-11-10T10:00:00.000Z");
    EventEnvelope second = event("e2", "2026-11-10T10:00:01.000Z");
    EntitySnapshotEnvelope snapshot1 = snapshot("s1");
    EntitySnapshotEnvelope snapshot2 = snapshot("s2");
    when(events.findDeletableEvent(config, parameters)).thenReturn(List.of(first, second));
    SnapshotDeleteParameters firstSnapshots = new SnapshotDeleteParameters(UUID.of("42"), "Cart", "2026-11-10T10:00:00.000Z");
    SnapshotDeleteParameters secondSnapshots = new SnapshotDeleteParameters(UUID.of("42"), "Cart", "2026-11-10T10:00:01.000Z");
    when(events.findDeletableSnapshot(config, firstSnapshots)).thenReturn(List.of(snapshot1, snapshot2));
    when(events.findDeletableSnapshot(config, secondSnapshots)).thenReturn(List.of());

    assertThat(BoosterDeleteEventDispatcher.deleteEvent(config, parameters, readModelStore)).isTrue();

    InOrder order = inOrder(events, readModelStore);
    order.verify(events).findDeletableEvent(config, parameters);
    order.verify(events).findDeletableSnapshot(config, firstSnapshots);
    order.verify(readModelStore).project(snapshot1, true);
    order.verify(readModelStore).project(snapshot2, true);
    order.verify(events).deleteSnapshot(config, List.of(snapshot1, snapshot2));
    order.verify(events).findDeletableSnapshot(config, secondSnapshots);
    // called even when there is nothing to delete
    order.verify(events).deleteSnapshot(config, List.of());
    order.verify(events).deleteEvent(config, List.of(first, second));
    order.verifyNoMoreInteractions();
  }

  @Test
  void errorsPropagateAndLeaveTheWorkDoneSoFar() {
    EventEnvelope first = event("e1", "2026-11-10T10:00:00.000Z");
    EntitySnapshotEnvelope snapshot1 = snapshot("s1");
    when(events.findDeletableEvent(config, parameters)).thenReturn(List.of(first));
    when(events.findDeletableSnapshot(any(), any())).thenReturn(List.of(snapshot1));
    IllegalStateException error = new IllegalStateException("cannot delete");
    doThrow(error).when(events).deleteSnapshot(config, List.of(snapshot1));

    assertThatThrownBy(() -> BoosterDeleteEventDispatcher.deleteEvent(config, parameters, readModelStore)).isSameAs(error);

    verify(readModelStore).project(snapshot1, true);
    verify(events, never()).deleteEvent(any(), any());
  }

  @Test
  void thePublicMethodDeletesEventsWithoutSnapshots() {
    EventEnvelope first = event("e1", "2026-11-10T10:00:00.000Z");
    when(events.findDeletableEvent(config, parameters)).thenReturn(List.of(first));
    when(events.findDeletableSnapshot(any(), any())).thenReturn(List.of());

    assertThat(BoosterDeleteEventDispatcher.deleteEvent(config, parameters)).isTrue();

    verify(events).deleteEvent(config, List.of(first));
  }
}
