package com.boostercloud.framework.provider.azure.infrastructure.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.common.RunCommandError;
import com.boostercloud.framework.provider.azure.infrastructure.process.Command;
import com.boostercloud.framework.provider.azure.infrastructure.support.RecordingCommandRunner;
import com.boostercloud.framework.provider.azure.infrastructure.types.Resource;
import com.boostercloud.framework.provider.azure.infrastructure.types.ResourceGroup;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AzCliHelperTest {

  private final RecordingCommandRunner commands = new RecordingCommandRunner();
  private final AzCliHelper az = new AzCliHelper(commands);

  @Test
  void shows_the_resource_group_of_the_application() {
    commands.answering(command -> "{\"id\":\"/subscriptions/s/resourceGroups/mystoreproductionrg\",\"location\":"
        + "\"eastus\",\"managedBy\":null,\"name\":\"mystoreproductionrg\",\"properties\":{}}");
    assertThat(az.getResourceGroup("my-store", "production")).isEqualTo(new ResourceGroup(
        "/subscriptions/s/resourceGroups/mystoreproductionrg", "eastus", null, "mystoreproductionrg"));
    assertThat(commands.commands()).containsExactly(new Command(Path.of("."),
        List.of("az", "group", "show", "--name", "mystoreproductionrg"), Map.of(), true));
  }

  @Test
  void rejects_outputs_saying_could_not_be_found() {
    commands.answering(command -> "Resource group 'x' could not be found.");
    assertThatThrownBy(() -> az.getResourceGroup("my-store", "production"))
        .isInstanceOf(IllegalStateException.class).hasMessage("Resource Group for application my-store does not exist");
    assertThatThrownBy(() -> az.showResourcesInResourceGroup("rg")).hasMessage("Resource Group rg does not exist");
    assertThatThrownBy(() -> az.showResourceInfo("rg", "name", "type")).hasMessage("Resource name does not exist");
    assertThatThrownBy(() -> az.getCosmosConnectionStrings("my-store", "production"))
        .hasMessage("Resource mystoreproductionrg does not exist");
  }

  @Test
  void propagates_the_standard_error_of_failed_commands() {
    commands.failing("az group show", "ERROR: (ResourceGroupNotFound) Resource group 'mystoreproductionrg' could not"
        + " be found.");
    assertThatThrownBy(() -> az.getResourceGroup("my-store", "production"))
        .isInstanceOf(RunCommandError.class).hasMessageContaining("ResourceGroupNotFound");
  }

  @Test
  void lists_and_shows_resources_and_cosmos_connection_strings() {
    commands.answering(command -> switch (command.arguments().get(1)) {
      case "resource" -> command.arguments().get(2).equals("list")
          ? "[{\"id\":\"i\",\"location\":\"l\",\"name\":\"n\",\"type\":\"t\",\"properties\":{\"a\":1}}]"
          : "{\"id\":\"i\",\"location\":\"l\",\"name\":\"n\",\"type\":\"t\"}";
      default -> "{\"connectionStrings\":[{\"connectionString\":\"AccountEndpoint=x;\",\"description\":\"Primary\"}]}";
    });
    assertThat(az.showResourcesInResourceGroup("rg")).containsExactly(new Resource("i", "l", null, "n", "t",
        Map.of("a", 1)));
    assertThat(az.showResourceInfo("rg", "n", "Microsoft.Web/sites")).isEqualTo(new Resource("i", "l", null, "n", "t",
        null));
    assertThat(az.getCosmosConnectionStrings("my-store", "production")).isEqualTo(Map.of("connectionStrings",
        List.of(Map.of("connectionString", "AccountEndpoint=x;", "description", "Primary"))));
    assertThat(commands.commands()).extracting(Command::commandLine).containsExactly(
        "az resource list --resource-group rg",
        "az resource show --resource-group rg --name n --resource-type Microsoft.Web/sites",
        "az cosmosdb list-connection-strings --resource-group mystoreproductionrg --name mystoreproductionrgcdba");
  }
}
