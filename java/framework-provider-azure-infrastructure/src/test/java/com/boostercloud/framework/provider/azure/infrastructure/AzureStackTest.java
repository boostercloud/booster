package com.boostercloud.framework.provider.azure.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.provider.azure.infrastructure.rockets.InfrastructureRocket;
import com.boostercloud.framework.provider.azure.infrastructure.rockets.RocketUtils;
import com.boostercloud.framework.provider.azure.infrastructure.support.Fixtures;
import com.boostercloud.framework.provider.azure.infrastructure.support.TestEnvironment;
import com.boostercloud.framework.provider.azure.infrastructure.terraform.TerraformApp;
import com.boostercloud.framework.provider.azure.infrastructure.types.ApplicationSynthStack;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.concepts.ReadModelMetadata;
import com.boostercloud.framework.types.rockets.RocketDescriptor;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(TestEnvironment.class)
class AzureStackTest {

  @TempDir Path root;

  private AzureStack stack(BoosterConfig config) {
    return new AzureStack(new TerraformApp(root), config.appName + config.environmentName, config, null,
        Fixtures.CONFIGURATION, Fixtures.APP_CLASS);
  }

  private static InfrastructureRocket named(String name) {
    return new InfrastructureRocket() {
      @Override
      public ApplicationSynthStack mountStack(BoosterConfig config, ApplicationSynthStack stack, RocketUtils utils) {
        return stack;
      }

      @Override
      public String getFunctionAppName(ApplicationSynthStack stack) {
        return name;
      }
    };
  }

  private static final InfrastructureRocket UNNAMED = (config, stack, utils) -> stack;

  record Params(String rocketProviderPackage) {}

  private static String rocketSettings(AzureStack stack) {
    String appSettings = (String) stack.applicationStack.functionApp.attribute("app_settings");
    return appSettings.substring(appSettings.lastIndexOf(", {") + 2);
  }

  @Test
  void the_default_settings_are_computed_with_the_whole_stack() {
    AzureStack stack = stack(Fixtures.config());
    assertThat(stack.defaultApplicationSettings).containsEntry("WebPubSubConnectionString",
        "${azurerm_web_pubsub.mystoreproductionwps.primary_connection_string}")
        .containsEntry("WEBSITE_CONTENTSHARE", "mystoreproductionfunc")
        .containsEntry("BOOSTER_APP_CLASS", Fixtures.APP_CLASS);
    assertThat(stack.applicationStack.functionApp.attribute("app_settings")).isEqualTo(Map.of());
  }

  @Test
  void without_rockets_the_rocket_settings_are_empty() {
    AzureStack stack = stack(Fixtures.config());
    stack.addAppSettingsToFunctionApp(null);
    assertThat(rocketSettings(stack)).isEqualTo("{\"ROCKET_FUNCTION_APP_NAMES\" = \"\", \"ROCKET_PACKAGE_MAPPING\" = \"\"})}");
  }

  @Test
  void joins_every_rocket_function_app_name_and_maps_packages_to_the_first_one() {
    BoosterConfig config = Fixtures.config();
    config.rockets = List.of(
        new RocketDescriptor("pkg.a", Map.of("rocketProviderPackage", "@boostercloud/rocket-file-uploads-azure")),
        new RocketDescriptor("pkg.b", List.of("webhook params")),
        new RocketDescriptor("pkg.c", new Params("rocket-no-scope-local")),
        new RocketDescriptor("pkg.d", Map.of("rocketProviderPackage", "nodash")),
        new RocketDescriptor("pkg.e", Map.of("rocketProviderPackage", "")),
        new RocketDescriptor("pkg.f", null));
    AzureStack stack = stack(config);
    stack.addAppSettingsToFunctionApp(List.of(UNNAMED, named("firstfunc"), named("secondfunc")));
    assertThat(rocketSettings(stack)).isEqualTo("{\"ROCKET_FUNCTION_APP_NAMES\" = \",firstfunc,secondfunc\","
        + " \"ROCKET_PACKAGE_MAPPING\" = \"rocket-file-uploads:firstfunc;rocket-no-scope:firstfunc;nodash:firstfunc\"})}");
  }

  @Test
  void duplicate_packages_overwrite_and_pairs_without_function_app_are_skipped() {
    BoosterConfig config = Fixtures.config();
    config.rockets = List.of(
        new RocketDescriptor("a", Map.of("rocketProviderPackage", "@x/rocket-one-azure")),
        new RocketDescriptor("b", Map.of("rocketProviderPackage", "@y/rocket-one-local")));
    AzureStack withoutNames = stack(config);
    withoutNames.addAppSettingsToFunctionApp(List.of(UNNAMED));
    assertThat(rocketSettings(withoutNames)).isEqualTo(
        "{\"ROCKET_FUNCTION_APP_NAMES\" = \"\", \"ROCKET_PACKAGE_MAPPING\" = \"\"})}");
    AzureStack withNames = stack(config);
    withNames.addAppSettingsToFunctionApp(List.of(named("onefunc")));
    assertThat(rocketSettings(withNames)).isEqualTo(
        "{\"ROCKET_FUNCTION_APP_NAMES\" = \"onefunc\", \"ROCKET_PACKAGE_MAPPING\" = \"rocket-one:onefunc\"})}");
  }

  @Test
  void fails_without_function_app() {
    AzureStack stack = stack(Fixtures.config());
    stack.applicationStack.functionApp = null;
    assertThatThrownBy(() -> stack.addAppSettingsToFunctionApp(List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Function app not defined");
  }

  @Test
  void read_models_whose_construct_id_collides_fail_the_synthesis() {
    BoosterConfig config = Fixtures.config();
    config.readModels.put("Pip", new ReadModelMetadata(Fixtures.CartReadModel.class, List.of(), null, List.of()));
    assertThatThrownBy(() -> stack(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("There is already a Construct with name 'pip' in AzureStack [my-storeproduction]");
  }

  @Test
  void parameters_that_cannot_be_serialised_have_no_package() {
    assertThat(AzureStack.rocketProviderPackageOf(new Object() {
      @SuppressWarnings("unused")
      private final Object self = this;
    })).isNull();
    assertThat(AzureStack.rocketProviderPackageOf(new Params("p"))).isEqualTo("p");
    assertThat(AzureStack.rocketProviderPackageOf(Map.of("rocketProviderPackage", 1))).isNull();
  }
}
