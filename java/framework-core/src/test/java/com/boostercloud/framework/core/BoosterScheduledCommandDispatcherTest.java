package com.boostercloud.framework.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.core.decorators.Decorators;
import com.boostercloud.framework.core.decorators.GlobalErrorHandler;
import com.boostercloud.framework.core.decorators.ScheduledCommand;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.concepts.ReducerMetadata;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.ContextEnvelope;
import com.boostercloud.framework.types.envelope.ScheduledCommandEnvelope;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.framework.types.errors.NotFoundError;
import com.boostercloud.framework.types.provider.ProviderEventsLibrary;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import com.boostercloud.framework.types.provider.ScheduledCommandsLibrary;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@code BoosterScheduledCommandDispatcher} (behaviour from the source, spec 05 §3.7) and the index delegation. */
class BoosterScheduledCommandDispatcherTest extends BoosterTestSupport {

  static final AtomicReference<Register> REGISTER = new AtomicReference<>();
  static final AtomicReference<RuntimeException> FAILURE = new AtomicReference<>();

  public record Tick(String id) {
    public UUID entityID() {
      return UUID.of(id);
    }
  }

  public record Clock(UUID id) {}

  @ScheduledCommand(minute = "0/5")
  public static class CheckCarts {
    public static void handle(Register register) {
      REGISTER.set(register);
      register.events(new Tick("t1"));
      if (FAILURE.get() != null) {
        throw FAILURE.get();
      }
    }
  }

  @GlobalErrorHandler
  public static class Swallow {
    public static Throwable onScheduledCommandHandlerError(Throwable error) {
      return null;
    }
  }

  private BoosterConfig config;
  private ProviderEventsLibrary events;
  private ScheduledCommandsLibrary scheduled;

  @BeforeEach
  @SuppressWarnings("deprecation")
  void setUp() {
    REGISTER.set(null);
    FAILURE.set(null);
    config = Booster.config();
    ProviderLibrary provider = mock(ProviderLibrary.class);
    events = mock(ProviderEventsLibrary.class);
    scheduled = mock(ScheduledCommandsLibrary.class);
    when(provider.events()).thenReturn(events);
    when(provider.scheduled()).thenReturn(scheduled);
    when(events.store(anyList(), any())).thenReturn(List.of());
    config.setProvider(provider);
    config.reducers.put("Tick", new ReducerMetadata(Clock.class, "tick"));
    Decorators.registerClass(CheckCarts.class);
  }

  private static ScheduledCommandEnvelope envelope(String typeName) {
    return ScheduledCommandEnvelope.builder().requestID(UUID.of("req")).typeName(typeName)
        .currentUser(UserEnvelope.builder().username("ignored").build())
        .context(ContextEnvelope.of(Map.of(), Map.of(), null)).build();
  }

  @Test
  void failsForUnknownScheduledCommands() {
    assertThatThrownBy(() -> new BoosterScheduledCommandDispatcher(config).dispatchCommand(envelope("Nope")))
        .isInstanceOf(NotFoundError.class).hasMessage("Could not find a proper handler for Nope");
  }

  @Test
  void runsTheHandlerWithoutUserAndStoresTheEvents() {
    ScheduledCommandEnvelope envelope = envelope("CheckCarts");

    new BoosterScheduledCommandDispatcher(config).dispatchCommand(envelope);

    Register register = REGISTER.get();
    assertThat(register.requestID()).isEqualTo(UUID.of("req"));
    assertThat(register.currentUser()).isNull();
    assertThat(register.responseHeaders()).isEmpty();
    assertThat(register.context()).isEqualTo(envelope.context());
    verify(events).store(anyList(), eq(config));
  }

  @Test
  void rethrowsOrSwallowsHandlerErrors() {
    FAILURE.set(new IllegalStateException("tick failed"));

    assertThatThrownBy(() -> new BoosterScheduledCommandDispatcher(config).dispatchCommand(envelope("CheckCarts")))
        .isSameAs(FAILURE.get());
    verify(events, never()).store(anyList(), any());

    Decorators.registerClass(Swallow.class);
    new BoosterScheduledCommandDispatcher(config).dispatchCommand(envelope("CheckCarts"));
    verify(events).store(anyList(), any());
  }

  @Test
  void dispatchConvertsTheRawRequest() {
    Object raw = Map.of("typeName", "CheckCarts");
    when(scheduled.rawToEnvelope(config, raw)).thenReturn(envelope("CheckCarts"));

    new BoosterScheduledCommandDispatcher(config).dispatch(raw);

    assertThat(REGISTER.get()).isNotNull();
  }
}
