package com.boostercloud.framework.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.core.fixtures.app.App;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.EnvironmentVariables;
import com.boostercloud.framework.types.InternalInfo;
import com.boostercloud.framework.types.injectable.Injectable;
import com.boostercloud.framework.types.injectable.InjectableCommand;
import com.boostercloud.framework.types.provider.ProviderInfrastructure;
import com.boostercloud.framework.types.provider.ProviderInfrastructure.Operation;
import com.boostercloud.framework.types.provider.ProviderLibrary;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@code Booster.run}: the CLI contract of {@code java/cli/README.md}. */
class BoosterRunnerTest extends BoosterTestSupport {

  private final ByteArrayOutputStream out = new ByteArrayOutputStream();
  private final ByteArrayOutputStream err = new ByteArrayOutputStream();
  private ProviderInfrastructure infrastructure;

  @BeforeEach
  @SuppressWarnings("deprecation")
  void setUp() {
    ProviderLibrary provider = mock(ProviderLibrary.class);
    infrastructure = mock(ProviderInfrastructure.class);
    when(provider.infrastructure()).thenReturn(infrastructure);
    when(infrastructure.supports(any())).thenReturn(true);
    Booster.config().setProvider(provider);
  }

  private BoosterRunner.Outcome run(String... args) {
    return BoosterRunner.run(App.class, args, new PrintStream(out, true, StandardCharsets.UTF_8),
        new PrintStream(err, true, StandardCharsets.UTF_8));
  }

  private String errors() {
    return err.toString(StandardCharsets.UTF_8);
  }

  @Test
  void startsTheLocalServerOnThePortAndKeepsTheJvm() {
    BoosterRunner.Outcome outcome = run("start", "--port", "4000");

    assertThat(outcome).isEqualTo(new BoosterRunner.Outcome(0, false));
    verify(infrastructure).start(Booster.config(), 4000);
    assertThat(InternalInfo.boosterLocalPort()).isEqualTo("4000");
    assertThat(Booster.config().commandHandlers).containsKey("CreateCart");
  }

  @Test
  void startUsesTheDefaultPort() {
    assertThat(run("start").exitCode()).isZero();

    verify(infrastructure).start(Booster.config(), 3000);
  }

  @Test
  void rejectsInvalidArguments() {
    assertThat(run()).isEqualTo(new BoosterRunner.Outcome(2, true));
    assertThat(run("start", "--port", "abc").exitCode()).isEqualTo(2);
    assertThat(run("start", "--verbose").exitCode()).isEqualTo(2);
    assertThat(run("synth", "extra").exitCode()).isEqualTo(2);
    assertThat(run("dance").exitCode()).isEqualTo(2);
    assertThat(errors()).contains(BoosterRunner.USAGE).contains("Unknown action 'dance'");
    verify(infrastructure, never()).start(any(), org.mockito.ArgumentMatchers.anyInt());
  }

  @Test
  void runsTheOneShotOperations() {
    assertThat(run("synth")).isEqualTo(new BoosterRunner.Outcome(0, true));
    assertThat(run("deploy").exitCode()).isZero();
    assertThat(run("nuke").exitCode()).isZero();

    BoosterConfig config = Booster.config();
    verify(infrastructure).synth(config);
    verify(infrastructure).deploy(config);
    verify(infrastructure).nuke(config);
  }

  @Test
  void failsWhenTheProviderDoesNotSupportTheOperation() {
    when(infrastructure.supports(Operation.DEPLOY)).thenReturn(false);

    assertThat(run("deploy").exitCode()).isEqualTo(1);
    assertThat(errors()).contains("Attempted to perform the 'deploy' operation with a provider that does not support"
        + " this feature, please check your environment configuration.");
  }

  @Test
  void failsWhenTheOperationFails() {
    doThrow(new IllegalStateException("terraform failed")).when(infrastructure).nuke(any());

    assertThat(run("nuke").exitCode()).isEqualTo(1);
    assertThat(errors()).contains("The 'nuke' operation failed: IllegalStateException: terraform failed");
  }

  @Test
  void failsWhenTheApplicationCannotStart() {
    Booster.configureCurrentEnv(config -> config.schemaMigrations.put("Gap", new java.util.LinkedHashMap<>(java.util.Map.of(3,
        new com.boostercloud.framework.types.concepts.SchemaMigrationMetadata(Object.class, "m", 3, Object.class, Object.class)))));

    assertThat(run("start").exitCode()).isEqualTo(1);
    assertThat(errors()).contains("Booster could not start the application");
  }

  @Test
  void runsTheInjectedCommandsWithTheCliHook() {
    EnvironmentVariables.set(EnvironmentVariables.BOOSTER_CLI_HOOK, " true ");
    List<List<String>> calls = new ArrayList<>();
    Booster.config().injectable = Injectable.of(
        InjectableCommand.of("test", "Says hello", (config, arguments) -> calls.add(arguments)),
        InjectableCommand.of("fail", (config, arguments) -> {
          throw new IllegalStateException("command failed");
        }));

    assertThat(run("test", "a", "b").exitCode()).isZero();
    assertThat(calls).containsExactly(List.of("a", "b"));
    assertThat(run("--help").exitCode()).isZero();
    assertThat(out.toString(StandardCharsets.UTF_8)).contains("COMMANDS").contains("- test  Says hello").contains("- fail");
    assertThat(run("fail").exitCode()).isEqualTo(1);
    assertThat(run("unknown").exitCode()).isEqualTo(1);
    assertThat(errors()).contains("Invalid subcommand for boost - use one of 'test', 'fail'");
    verify(infrastructure, never()).start(any(), org.mockito.ArgumentMatchers.anyInt());
  }

  @Test
  void theCliHookWithoutInjectableCommandsDoesNothing() {
    EnvironmentVariables.set(EnvironmentVariables.BOOSTER_CLI_HOOK, "true");

    assertThat(run("anything")).isEqualTo(new BoosterRunner.Outcome(0, true));
    assertThat(out.toString(StandardCharsets.UTF_8)).isEmpty();
  }
}
