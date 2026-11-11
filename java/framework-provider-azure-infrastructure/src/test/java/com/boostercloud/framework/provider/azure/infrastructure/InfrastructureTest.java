package com.boostercloud.framework.provider.azure.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.provider.azure.Provider;
import com.boostercloud.framework.provider.azure.infrastructure.rockets.InfrastructureRocket;
import com.boostercloud.framework.provider.azure.infrastructure.support.TestEnvironment;
import com.boostercloud.framework.provider.azure.infrastructure.support.TestRockets;
import com.boostercloud.framework.types.provider.ProviderInfrastructure;
import com.boostercloud.framework.types.provider.ProviderLoader;
import com.boostercloud.framework.types.rockets.RocketDescriptor;
import com.example.rockets.webhook.Rocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(TestEnvironment.class)
class InfrastructureTest {

  @Test
  void loads_every_rocket_eagerly_through_the_rocket_loader() {
    Map<String, Object> parameters = Map.of("route", "test");
    ProviderInfrastructure infrastructure =
        Infrastructure.infrastructure(List.of(new RocketDescriptor("com.example.rockets.webhook", parameters)));
    assertThat(infrastructure).isInstanceOf(AzureInfrastructure.class);
    assertThat(((AzureInfrastructure) infrastructure).rockets()).singleElement()
        .isInstanceOf(TestRockets.WebhookRocket.class);
    assertThat(Rocket.lastParameters).isSameAs(parameters);
  }

  @Test
  void fails_with_the_typescript_message_for_a_missing_rocket_package() {
    assertThatThrownBy(() -> Infrastructure.infrastructure(List.of(new RocketDescriptor("com.example.missing", null))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Could not load the rocket infrastructure package com.example.missing. Make sure you installed it"
            + " in your project devDependencies.");
  }

  @Test
  void keeps_undefined_and_empty_rocket_lists_apart() {
    List<RocketDescriptor> loaded = new ArrayList<>();
    AzureInfrastructure none = Infrastructure.infrastructure(null, descriptor -> {
      loaded.add(descriptor);
      return (c, s, u) -> s;
    }, config -> null);
    assertThat(none.rockets()).isNull();
    AzureInfrastructure empty = Infrastructure.infrastructure(List.of(), descriptor -> null, config -> null);
    assertThat(empty.rockets()).isEmpty();
    RocketDescriptor descriptor = new RocketDescriptor("x", null);
    InfrastructureRocket rocket = (c, s, u) -> s;
    AzureInfrastructure one = Infrastructure.infrastructure(List.of(descriptor), d -> {
      loaded.add(d);
      return rocket;
    }, config -> null);
    assertThat(one.rockets()).containsExactly(rocket);
    assertThat(loaded).containsExactly(descriptor);
  }

  @Test
  void the_azure_provider_finds_this_infrastructure() {
    assertThat(ProviderLoader.loadInfrastructure(Provider.PACKAGE, List.of())).isInstanceOf(AzureInfrastructure.class);
    assertThat(Provider.provider(List.of()).infrastructure()).isInstanceOf(AzureInfrastructure.class);
  }
}
