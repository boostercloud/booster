package com.boostercloud.framework.provider.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.provider.local.library.WebSocketServerAdapter;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import com.boostercloud.framework.types.provider.ProviderLoader;
import com.boostercloud.framework.types.rockets.RocketDescriptor;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The entry point core loads from {@code config.providerPackage}. */
class ProviderTest {

  @BeforeEach
  void useAFreeWebSocketPort() {
    EnvironmentVariables.set(WebSocketServerAdapter.LOCAL_ENVIRONMENT_WEBSOCKET_SERVER_PORT, "0");
  }

  @AfterEach
  void closeTheSharedRuntime() {
    LocalProviderRuntime.shared().close();
    EnvironmentVariables.unset(WebSocketServerAdapter.LOCAL_ENVIRONMENT_WEBSOCKET_SERVER_PORT);
  }

  @Test
  void coreLoadsTheProviderFromItsPackage() {
    ProviderLibrary library = ProviderLoader.loadProvider(Provider.PACKAGE_NAME,
        List.of(new RocketDescriptor("com.example.rocket", null)));
    assertThat(library).isInstanceOf(LocalProvider.class);
    LocalProvider provider = (LocalProvider) library;
    assertThat(provider.runtime()).isSameAs(LocalProviderRuntime.shared());
    assertThat(provider.runtime().baseDirectory()).isNull();
    // the websocket server starts with the provider, once per process
    assertThat(provider.runtime().webSocketServerAdapter().isRunning()).isTrue();
    LocalProvider second = (LocalProvider) Provider.provider(null);
    assertThat(second).isNotSameAs(provider);
    assertThat(second.runtime().webSocketServerAdapter()).isSameAs(provider.runtime().webSocketServerAdapter());
  }

  @Test
  void loadsTheInfrastructurePackageByName() {
    assertThat(Provider.INFRASTRUCTURE_PACKAGE_NAME).isEqualTo("com.boostercloud.framework.provider.local.infrastructure");
    assertThatThrownBy(() -> Provider.loadInfrastructurePackage(Provider.INFRASTRUCTURE_PACKAGE_NAME))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("Could not load the infrastructure class "
            + "com.boostercloud.framework.provider.local.infrastructure.Infrastructure");
    assertThatThrownBy(() -> Provider.loadInfrastructurePackage("com.example"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void pathsAreTheTypeScriptOnes() {
    assertThat(Paths.EVENTS_DATABASE).isEqualTo(".booster/events.json");
    assertThat(Paths.READ_MODELS_DATABASE).isEqualTo(".booster/read_models.json");
    assertThat(Paths.CONNECTIONS_DATABASE).isEqualTo(".booster/connections.json");
    assertThat(Paths.SUBSCRIPTION_DATABASE).isEqualTo(".booster/subscriptions.json");
    assertThat(Paths.REGISTERED_USERS_DATABASE).isEqualTo(".booster/registered_users.json");
    assertThat(Paths.AUTHENTICATED_USERS_DATABASE).isEqualTo(".booster/authenticated_users.json");
    assertThat(Paths.DISPATCH_JOURNAL_DATABASE).isEqualTo(".booster/dispatch_journal.json");
  }

  /** Spec 10, section 2.6: the constants of the TypeScript provider. */
  @Test
  void constantsAreTheTypeScriptOnes() {
    assertThat(WebSocketServerAdapter.LOCAL_ENVIRONMENT_WEBSOCKET_SERVER_PORT)
        .isEqualTo("LOCAL_ENVIRONMENT_WEBSOCKET_SERVER_PORT");
    assertThat(WebSocketServerAdapter.DEFAULT_PORT).isEqualTo("65529");
    assertThat(WebSocketServerAdapter.PATH).isEqualTo("/websocket");
    assertThat(com.boostercloud.framework.provider.local.services.ReadModelRegistry.UNIQUE_VIOLATED_ERROR_TYPE)
        .isEqualTo("uniqueViolated");
    assertThat(com.boostercloud.framework.provider.local.library.EventsSearchAdapter.DEFAULT_CREATED_AT_SORT_ORDER)
        .isEqualTo(-1);
    assertThat(com.boostercloud.framework.provider.local.library.EventsAdapter.ORIGIN_OF_TIME)
        .isEqualTo("1970-01-01T00:00:00.000Z");
    assertThat(com.boostercloud.framework.provider.local.services.WebSocketRegistry.EXPIRATION_TTL_SECONDS)
        .isEqualTo(172800);
    assertThat(Provider.INFRASTRUCTURE_PACKAGE_NAME)
        .isEqualTo("com.boostercloud.framework.provider.local.infrastructure");
  }
}
