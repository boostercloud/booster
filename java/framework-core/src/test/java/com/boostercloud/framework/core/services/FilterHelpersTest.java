package com.boostercloud.framework.core.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.BoosterTestSupport;
import com.boostercloud.framework.core.decorators.Trace;
import com.boostercloud.framework.types.concepts.CommandBeforeFunction;
import com.boostercloud.framework.types.concepts.QueryBeforeFunction;
import com.boostercloud.framework.types.concepts.ReadModelBeforeFunction;
import com.boostercloud.framework.types.envelope.ReadModelRequestEnvelope;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@code services/filter-helpers.ts} (no TypeScript test). */
class FilterHelpersTest extends BoosterTestSupport {

  static final List<String> TRACED = new ArrayList<>();

  public static class TracedHook implements CommandBeforeFunction {
    @Override
    @Trace("BEFORE_HOOK")
    public Map<String, Object> apply(Map<String, Object> input, UserEnvelope currentUser) {
      Map<String, Object> result = new LinkedHashMap<>(input);
      result.put("traced", true);
      return result;
    }
  }

  @Test
  void chainsCommandHooksInOrderPassingTheUser() {
    UserEnvelope user = UserEnvelope.builder().username("u").build();
    CommandBeforeFunction first = (input, currentUser) -> Map.of("step", 1, "user", currentUser.username());
    CommandBeforeFunction second = (input, currentUser) -> Map.of("step", ((Integer) input.get("step")) + 1);

    assertThat(FilterHelpers.applyBeforeFunctions(Map.of(), List.of(first, second), user)).isEqualTo(Map.of("step", 2));
    Map<String, Object> input = Map.of("a", 1);
    assertThat(FilterHelpers.applyBeforeFunctions(input, List.of(), user)).isSameAs(input);
  }

  @Test
  void chainsQueryAndReadModelHooks() {
    QueryBeforeFunction query = (input, currentUser) -> Map.of("q", true);
    ReadModelRequestEnvelope envelope = ReadModelRequestEnvelope.builder().className("A").build();
    ReadModelBeforeFunction readModel = (request, currentUser) -> request.toBuilder().className("B").build();

    assertThat(FilterHelpers.applyQueryBeforeFunctions(Map.of(), List.of(query), null)).isEqualTo(Map.of("q", true));
    assertThat(FilterHelpers.applyReadModelRequestBeforeFunctions(envelope, List.of(readModel, readModel), null).className())
        .isEqualTo("B");
  }

  @Test
  void tracesHooksAnnotatedWithTrace() {
    Booster.config().traceConfiguration.enableAll();
    Booster.config().traceConfiguration.onStart = (config, actionType, info) -> TRACED.add(actionType + " " + info.className);

    assertThat(FilterHelpers.applyBeforeFunctions(Map.of(), List.of(new TracedHook()), null)).containsEntry("traced", true);
    assertThat(TRACED).containsExactly("BEFORE_HOOK TracedHook");
  }
}
