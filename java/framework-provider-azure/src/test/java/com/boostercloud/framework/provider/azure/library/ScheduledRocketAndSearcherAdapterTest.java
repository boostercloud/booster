package com.boostercloud.framework.provider.azure.library;

import static com.boostercloud.framework.provider.azure.support.CosmosMocks.obj;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.boostercloud.framework.provider.azure.cosmos.FeedOptions;
import com.boostercloud.framework.provider.azure.cosmos.SqlQuerySpec;
import com.boostercloud.framework.provider.azure.support.CosmosMocks;
import com.boostercloud.framework.provider.azure.types.AzureInvocationContext;
import com.boostercloud.framework.provider.azure.types.TimerInfo;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.framework.types.envelope.ReadModelListResult;
import com.boostercloud.framework.types.envelope.ScheduledCommandEnvelope;
import com.boostercloud.framework.types.rockets.RocketEnvelope;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Tests of {@code scheduled-adapter.ts}, {@code rocket-adapter.ts} and {@code searcher-adapter.ts}. */
class ScheduledRocketAndSearcherAdapterTest {

  private final BoosterConfig config = new BoosterConfig("test");

  @AfterEach
  void clearEnvironment() {
    EnvironmentVariables.clearOverrides();
  }

  @Test
  void timerInputsBecomeScheduledCommandEnvelopesWithAFreshRequestId() {
    ScheduledAdapter.ScheduledCommandInput input = new ScheduledAdapter.ScheduledCommandInput(
        new TimerInfo(false, null, null), AzureInvocationContext.of("i"), "CheckCarts");

    ScheduledCommandEnvelope first = ScheduledAdapter.rawScheduledInputToEnvelope(config, input);
    ScheduledCommandEnvelope second = ScheduledAdapter.rawScheduledInputToEnvelope(config, input);

    assertThat(first.typeName()).isEqualTo("CheckCarts");
    assertThat(first.requestID()).isNotNull().isNotEqualTo(second.requestID());
    assertThat(first.currentUser()).isNull();
  }

  @Test
  void theTypeNameIsNotValidatedButTheTimerIs() {
    assertThat(ScheduledAdapter.rawScheduledInputToEnvelope(config,
        Map.of("timer", Map.of("isPastDue", true), "context", Map.of())).typeName()).isNull();
    assertThatThrownBy(() -> ScheduledAdapter.rawScheduledInputToEnvelope(config, Map.of("typeName", "X")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Invalid scheduled command input. Expected ScheduledCommandInput with typeName and timer, but "
            + "received: {\"typeName\":\"X\"}");
  }

  @Test
  void theRocketIdComesFromTheFunctionAppSetting() {
    assertThat(RocketAdapter.rawRocketInputToEnvelope(config, obj("BOOSTER_ROCKET_FUNCTION_ID", "ignored")))
        .isEqualTo(new RocketEnvelope(null));
    EnvironmentVariables.set("BOOSTER_ROCKET_FUNCTION_ID", "rocket-webhook");
    assertThat(RocketAdapter.rawRocketInputToEnvelope(config, null)).isEqualTo(new RocketEnvelope("rocket-webhook"));
  }

  @Test
  void readModelSearchesRunOnTheReadModelContainerWithReorderedArguments() {
    CosmosMocks cosmos = new CosmosMocks();

    Object result = SearcherAdapter.searchReadModel(cosmos.client, config, "CartReadModel",
        obj("id", obj("eq", "1")), obj("id", "ASC"), 5, null, true, List.of("id"));

    verify(cosmos.database).container("new-booster-app-app-CartReadModel");
    verify(cosmos.items).query(eq(new SqlQuerySpec("SELECT c[\"id\"] FROM c WHERE c[\"id\"] = @id_0 ORDER BY c.id ASC",
        List.of(com.boostercloud.framework.provider.azure.cosmos.SqlParameter.of("@id_0", "1")))),
        any(FeedOptions.class));
    assertThat(result).isInstanceOf(ReadModelListResult.class);
  }
}
