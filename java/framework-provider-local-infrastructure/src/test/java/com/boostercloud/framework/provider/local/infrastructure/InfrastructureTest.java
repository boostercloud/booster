package com.boostercloud.framework.provider.local.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.provider.ProviderInfrastructure;
import com.boostercloud.framework.types.provider.ProviderInfrastructure.Operation;
import com.boostercloud.framework.types.provider.ProviderLoader;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@code index.ts}: the {@code Infrastructure} entry point and the operations it does not support. */
class InfrastructureTest {

  private final BoosterConfig config = new BoosterConfig("test");

  @Test
  void isFoundByTheProviderLoaderFromTheProviderPackage() {
    ProviderInfrastructure infrastructure =
        ProviderLoader.loadInfrastructure("com.boostercloud.framework.provider.local", List.of());
    assertThat(infrastructure).isInstanceOf(LocalInfrastructure.class);
  }

  @Test
  void onlyStartIsSupported() {
    LocalInfrastructure infrastructure = Infrastructure.infrastructure(List.of());
    assertThat(infrastructure.supports(Operation.START)).isTrue();
    assertThat(infrastructure.supports(Operation.DEPLOY)).isFalse();
    assertThat(infrastructure.supports(Operation.NUKE)).isFalse();
    assertThat(infrastructure.supports(Operation.SYNTH)).isFalse();
  }

  @Test
  void unsupportedOperationsFailWithTheCliMessage() {
    LocalInfrastructure infrastructure = Infrastructure.infrastructure(null);
    Map<String, Runnable> operations = Map.of(
        "deploy", () -> infrastructure.deploy(config),
        "nuke", () -> infrastructure.nuke(config),
        "synth", () -> infrastructure.synth(config));
    operations.forEach((name, operation) -> assertThatThrownBy(operation::run)
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("Attempted to perform the '" + name + "' operation with a provider that does not support this "
            + "feature, please check your environment configuration."));
  }

  @Test
  void defaultOptions() {
    LocalInfrastructure.Options options = LocalInfrastructure.Options.defaults();
    assertThat(options.closeRuntimeOnStop()).isTrue();
    assertThat(options.registerShutdownHook()).isTrue();
    assertThat(options.shutdownTimeout()).isEqualTo(LocalInfrastructure.Options.DEFAULT_SHUTDOWN_TIMEOUT);
    assertThat(options.schedulerClock().zone()).isEqualTo(java.time.ZoneId.systemDefault());
    assertThat(LocalServer.BODY_LIMIT).isEqualTo(6 * 1024 * 1024);
  }

  @Test
  void httpCodes() {
    assertThat(HttpCodes.OK.code()).isEqualTo(200);
    assertThat(HttpCodes.BAD_REQUEST.code()).isEqualTo(400);
    assertThat(HttpCodes.NOT_AUTHORIZED.code()).isEqualTo(403);
    assertThat(HttpCodes.INTERNAL_ERROR.code()).isEqualTo(500);
  }

  @Test
  void requestFailedUsesTheErrorClassForStatusAndTitle() {
    var response = new com.boostercloud.framework.provider.local.infrastructure.web.MemoryHttpResponse();
    Http.requestFailed(new com.boostercloud.framework.types.errors.InvalidParameterError("bad input"), response);
    assertThat(response.sentStatus()).isEqualTo(400);
    assertThat(response.bodyAsJson()).isEqualTo(Map.of("title", "Invalid Parameter Error", "reason", "bad input"));

    var noMessage = new com.boostercloud.framework.provider.local.infrastructure.web.MemoryHttpResponse();
    Http.requestFailed(new RuntimeException(), noMessage);
    assertThat(noMessage.sentStatus()).isEqualTo(500);
    assertThat(noMessage.bodyAsJson()).isEqualTo(Map.of("title", "Runtime Exception", "reason", ""));
  }
}
