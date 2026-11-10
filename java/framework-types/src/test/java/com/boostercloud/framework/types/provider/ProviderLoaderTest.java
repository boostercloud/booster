package com.boostercloud.framework.types.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.provider.fixture.Provider;
import com.boostercloud.framework.types.provider.fixture.infrastructure.Infrastructure;
import com.boostercloud.framework.types.rockets.RocketDescriptor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Provider and infrastructure loading by package name, and the optional infrastructure operations. */
class ProviderLoaderTest {

  private static final String FIXTURE = "com.boostercloud.framework.types.provider.fixture";

  @Test
  void loadsTheProviderClassOfAPackageWithTheRockets() {
    List<RocketDescriptor> rockets = List.of(new RocketDescriptor("com.example.rocket", Map.of("a", 1)));
    ProviderLibrary provider = ProviderLoader.loadProvider(FIXTURE, rockets);
    assertThat(provider.toString()).isEqualTo("FixtureProvider");
    assertThat(Provider.lastRockets).isEqualTo(rockets);
  }

  @Test
  void loadsTheConfiguredProviderOnceAndCachesIt() {
    BoosterConfig config = new BoosterConfig("test");
    config.providerPackage = FIXTURE;
    int before = Provider.calls;
    ProviderLibrary first = config.provider();
    config.providerPackage = "com.example.changed";
    assertThat(config.provider()).isSameAs(first);
    assertThat(Provider.calls).isEqualTo(before + 1);
    assertThat(Provider.lastRockets).isEmpty();
  }

  @Test
  void explainsMissingClassesAndMethods() {
    assertThatThrownBy(() -> ProviderLoader.loadProvider("com.example.nothing", List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Could not load the provider class com.example.nothing.Provider");
    assertThatThrownBy(() -> ProviderLoader.loadProvider("com.boostercloud.framework.types.provider.broken", List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not declare a public static method provider(List<RocketDescriptor>)");
    assertThatThrownBy(() -> ProviderLoader.loadInfrastructurePackage("com.example.nothing"))
        .hasMessageContaining("com.example.nothing.infrastructure.Infrastructure");
  }

  @Test
  void loadsTheInfrastructureModuleOfAProvider() {
    List<RocketDescriptor> rockets = List.of(new RocketDescriptor("r", null));
    ProviderInfrastructure infrastructure = ProviderLoader.loadInfrastructure(FIXTURE, rockets);
    assertThat(infrastructure).isInstanceOf(Infrastructure.DeployOnly.class);
    assertThat(((Infrastructure.DeployOnly) infrastructure).rockets).isEqualTo(rockets);
    HasInfrastructure module = ProviderLoader.loadInfrastructurePackage(FIXTURE);
    assertThat(module.infrastructure(List.of())).isInstanceOf(Infrastructure.DeployOnly.class);
  }

  @Test
  void reportsWhichOptionalOperationsAnInfrastructureSupports() {
    ProviderInfrastructure infrastructure = ProviderLoader.loadInfrastructure(FIXTURE, List.of());
    assertThat(infrastructure.supports(ProviderInfrastructure.Operation.DEPLOY)).isTrue();
    assertThat(infrastructure.supports(ProviderInfrastructure.Operation.NUKE)).isFalse();
    assertThat(infrastructure.supports(ProviderInfrastructure.Operation.START)).isFalse();
    assertThat(infrastructure.supports(ProviderInfrastructure.Operation.SYNTH)).isFalse();
    BoosterConfig config = new BoosterConfig("test");
    infrastructure.deploy(config);
    assertThat(((Infrastructure.DeployOnly) infrastructure).deploys).isEqualTo(1);
    assertThatThrownBy(() -> infrastructure.start(config, 3000))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("does not support the 'start' operation");
  }

  @Test
  void keepsTheAzureDefaults() {
    AzureConfiguration azure = new AzureConfiguration();
    assertThat(AzureConfiguration.DEFAULT_CHUNK_SIZE).isEqualTo(100);
    assertThat(azure.enableEventBatching).isTrue();
    assertThat(azure.cosmos.batchSize).isEqualTo(100);
  }
}
