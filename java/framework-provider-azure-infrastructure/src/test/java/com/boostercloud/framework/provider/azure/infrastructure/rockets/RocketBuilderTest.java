package com.boostercloud.framework.provider.azure.infrastructure.rockets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.common.PromisesError;
import com.boostercloud.framework.provider.azure.infrastructure.helper.FunctionZip;
import com.boostercloud.framework.provider.azure.infrastructure.rockets.InfrastructureRocket.Hook;
import com.boostercloud.framework.provider.azure.infrastructure.support.FakeHttpTransport;
import com.boostercloud.framework.provider.azure.infrastructure.support.Fixtures;
import com.boostercloud.framework.provider.azure.infrastructure.support.RecordingCommandRunner;
import com.boostercloud.framework.provider.azure.infrastructure.support.RecordingSleeper;
import com.boostercloud.framework.provider.azure.infrastructure.support.TestEnvironment;
import com.boostercloud.framework.provider.azure.infrastructure.synth.ApplicationSynth;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformApp;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformStack;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionAppFunctionsDefinition;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionAppV4Definition;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionDefinition;
import com.boostercloud.framework.types.BoosterConfig;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(TestEnvironment.class)
class RocketBuilderTest {

  @TempDir Path root;

  private BoosterConfig config;
  private ApplicationSynthStack stack;
  private FunctionZip functionZip;

  @BeforeEach
  void setUp() {
    config = Fixtures.config();
    TerraformStack terraformStack = new TerraformStack(new TerraformApp(root), "s");
    stack = new ApplicationSynth(terraformStack, config, Fixtures.CONFIGURATION, Fixtures.APP_CLASS).synth(null);
    functionZip = new FunctionZip(config,
        Fixtures.context(root, new RecordingCommandRunner(), new FakeHttpTransport(), new RecordingSleeper()));
  }

  /** A rocket that only mounts its stack, recording the calls. */
  static final class StackOnlyRocket implements InfrastructureRocket {
    final List<String> calls;
    final String name;
    final RuntimeException failure;
    RocketUtils utils;

    StackOnlyRocket(List<String> calls, String name, RuntimeException failure) {
      this.calls = calls;
      this.name = name;
      this.failure = failure;
    }

    @Override
    public ApplicationSynthStack mountStack(BoosterConfig config, ApplicationSynthStack stack, RocketUtils utils) {
      calls.add(name);
      this.utils = utils;
      if (failure != null) {
        throw failure;
      }
      return stack;
    }
  }

  @Test
  void mounts_every_rocket_in_order_with_fresh_utils() {
    List<String> calls = new ArrayList<>();
    StackOnlyRocket first = new StackOnlyRocket(calls, "first", null);
    StackOnlyRocket second = new StackOnlyRocket(calls, "second", null);
    new RocketBuilder(config, stack, List.of(first, second), functionZip).synthRocket();
    assertThat(calls).containsExactly("first", "second");
    assertThat(first.utils).isNotSameAs(second.utils);
    assertThat(first.utils.toTerraformName("my-app", "func")).isEqualTo("myappfunc");
  }

  @Test
  void runs_every_rocket_and_then_throws_the_failures_together() {
    List<String> calls = new ArrayList<>();
    List<InfrastructureRocket> rockets = List.of(
        new StackOnlyRocket(calls, "a", new IllegalStateException("first failed")),
        new StackOnlyRocket(calls, "b", null),
        new StackOnlyRocket(calls, "c", new IllegalArgumentException("third failed")));
    assertThatThrownBy(() -> new RocketBuilder(config, stack, rockets, functionZip).synthRocket())
        .isInstanceOfSatisfying(PromisesError.class, error -> assertThat(error.failedReasons()).hasSize(2))
        .hasMessage("IllegalStateException: first failed. IllegalArgumentException: third failed");
    assertThat(calls).containsExactly("a", "b", "c");
  }

  @Test
  void without_rockets_there_is_nothing_to_mount_or_upload() {
    RocketBuilder builder = new RocketBuilder(config, stack, null, functionZip);
    builder.synthRocket();
    assertThat(builder.mountRocketsZipResources()).isNull();
    builder.uploadRocketsFiles(null);
    assertThat(new RocketBuilder(config, stack, List.of(), functionZip).mountRocketsZipResources()).isEmpty();
  }

  /** Defines every package hook. */
  static final class AllHooksRocket implements InfrastructureRocket {
    final List<String> used = new ArrayList<>();

    @Override
    public ApplicationSynthStack mountStack(BoosterConfig config, ApplicationSynthStack stack, RocketUtils utils) {
      return stack;
    }

    @Override
    public String getFunctionAppName(ApplicationSynthStack stack) {
      return "legacy-app.name";
    }

    @Override
    @SuppressWarnings("deprecation")
    public List<FunctionDefinition> mountFunctions(BoosterConfig config, ApplicationSynthStack stack, RocketUtils u) {
      used.add("mountFunctions");
      return List.of();
    }

    @Override
    public List<FunctionAppFunctionsDefinition> mountCode(BoosterConfig config, ApplicationSynthStack s, RocketUtils u) {
      used.add("mountCode");
      return List.of();
    }

    @Override
    public List<FunctionAppV4Definition> mountFunctionsV4(BoosterConfig config, ApplicationSynthStack s, RocketUtils u) {
      used.add("mountFunctionsV4");
      return List.of(FunctionAppV4Definition.of("my-rocket.app", List.of()),
          FunctionAppV4Definition.of("second", List.of()));
    }
  }

  @Test
  void uses_only_the_first_defined_hook_by_priority_and_sanitizes_names() {
    AllHooksRocket rocket = new AllHooksRocket();
    List<RocketZipResource> resources = new RocketBuilder(config, stack, List.of(rocket), functionZip)
        .mountRocketsZipResources();
    assertThat(rocket.used).containsExactly("mountFunctionsV4");
    assertThat(resources).extracting(RocketZipResource::functionAppName).containsExactly("my_rocket_app", "second");
    assertThat(resources).extracting(resource -> resource.zip().fileName())
        .containsExactly("rocket_my_rocket_app.zip", "rocket_second.zip");
    assertThat(resources.get(0).zip().path()).isEqualTo(root.toAbsolutePath().normalize()
        .resolve("rocket_my_rocket_app.zip"));
    assertThat(RocketBuilder.sanitize("a--b..c_d")).isEqualTo("a_b_c_d");
  }

  /** The deprecated mountFunctions path. */
  static final class LegacyRocket implements InfrastructureRocket {
    final boolean withName;

    LegacyRocket(boolean withName) {
      this.withName = withName;
    }

    @Override
    public ApplicationSynthStack mountStack(BoosterConfig config, ApplicationSynthStack stack, RocketUtils utils) {
      return stack;
    }

    @Override
    @SuppressWarnings("deprecation")
    public List<FunctionDefinition> mountFunctions(BoosterConfig config, ApplicationSynthStack stack, RocketUtils u) {
      return List.of();
    }

    @Override
    public boolean defines(Hook hook) {
      return hook == Hook.GET_FUNCTION_APP_NAME ? withName : InfrastructureRocket.super.defines(hook);
    }

    @Override
    public String getFunctionAppName(ApplicationSynthStack stack) {
      return "legacy-app.name";
    }
  }

  @Test
  void the_deprecated_hook_keeps_the_unsanitized_function_app_name() {
    List<RocketZipResource> resources = new RocketBuilder(config, stack, List.of(new LegacyRocket(true)), functionZip)
        .mountRocketsZipResources();
    assertThat(resources).singleElement().satisfies(resource -> {
      assertThat(resource.functionAppName()).isEqualTo("legacy-app.name");
      assertThat(resource.zip().fileName()).isEqualTo("rocket_legacy_app_name.zip");
    });
  }

  @Test
  void the_deprecated_hook_needs_the_function_app_name() {
    RocketBuilder builder = new RocketBuilder(config, stack, List.of(new LegacyRocket(false)), functionZip);
    assertThatThrownBy(builder::mountRocketsZipResources)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("FunctionAppName not found on rocket [object Object]");
  }

  @Test
  void rockets_without_package_hooks_contribute_nothing() {
    List<RocketZipResource> resources = new RocketBuilder(config, stack,
        List.of(new StackOnlyRocket(new ArrayList<>(), "x", null)), functionZip).mountRocketsZipResources();
    assertThat(resources).isEmpty();
  }

  @Test
  void detects_the_defined_hooks() {
    InfrastructureRocket lambda = (c, s, u) -> s;
    assertThat(lambda.defines(Hook.MOUNT_CODE)).isFalse();
    assertThat(lambda.defines(Hook.GET_FUNCTION_APP_NAME)).isFalse();
    AllHooksRocket rocket = new AllHooksRocket();
    for (Hook hook : List.of(Hook.GET_FUNCTION_APP_NAME, Hook.MOUNT_FUNCTIONS, Hook.MOUNT_CODE,
        Hook.MOUNT_FUNCTIONS_V4)) {
      assertThat(rocket.defines(hook)).as(hook.methodName()).isTrue();
    }
    assertThat(rocket.defines(Hook.UNMOUNT_STACK)).isFalse();
  }
}
