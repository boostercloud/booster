package com.boostercloud.framework.provider.azure.infrastructure.terraform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.types.BoosterJson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TerraformStackTest {

  @TempDir Path root;

  @Test
  void synthesizes_resources_data_outputs_and_providers() throws IOException {
    TerraformApp app = new TerraformApp(root);
    TerraformStack stack = new TerraformStack(app, "demo");
    TerraformProvider provider = new TerraformProvider(stack, "azureFeature", "azurerm", "azurerm", "~> 5.0");
    provider.setAttribute("features", List.of(Map.of()));
    TerraformResource group = new TerraformResource(stack, "rg", "azurerm_resource_group")
        .setAttribute("name", "demo-rg").provider(provider);
    TerraformResource account = new TerraformResource(stack, "sa", "azurerm_storage_account")
        .setAttribute("location", group.ref("location"))
        .dependsOn(group)
        .ignoreChanges("tags");
    account.addOverride("network_rules.default_action", "Deny");
    TerraformDataSource data = new TerraformDataSource(stack, "ip", "azurerm_public_ip");
    data.setAttribute("name", "x");
    new TerraformOutput(provider, "url", "http://" + data.ref("fqdn"), "The URL");

    assertThat(group.fqn()).isEqualTo("azurerm_resource_group.rg");
    assertThat(data.ref("fqdn")).isEqualTo("${data.azurerm_public_ip.ip.fqdn}");
    app.synth();

    Map<String, Object> json = BoosterJson.parseObject(
        Files.readString(root.resolve("cdktf.out/stacks/demo/cdk.tf.json")));
    assertThat(json).containsOnlyKeys("//", "data", "output", "provider", "resource", "terraform");
    assertThat(json.get("provider")).isEqualTo(Map.of("azurerm", List.of(Map.of("features", List.of(Map.of())))));
    assertThat(json.get("terraform")).isEqualTo(Map.of(
        "backend", Map.of("local", Map.of("path", root.toAbsolutePath().normalize() + "/terraform.demo.tfstate")),
        "required_providers", Map.of("azurerm", Map.of("source", "azurerm", "version", "~> 5.0"))));
    @SuppressWarnings("unchecked")
    Map<String, Object> resources = (Map<String, Object>) json.get("resource");
    assertThat(resources.get("azurerm_storage_account")).isEqualTo(Map.of("sa", Map.of(
        "//", Map.of("metadata", Map.of("path", "demo/sa", "uniqueId", "sa")),
        "location", "${azurerm_resource_group.rg.location}",
        "depends_on", List.of("azurerm_resource_group.rg"),
        "lifecycle", Map.of("ignore_changes", List.of("tags")),
        "network_rules", Map.of("default_action", "Deny"))));
    assertThat(json.get("output")).isEqualTo(Map.of(LogicalIds.makeUniqueId(List.of("azureFeature", "url"), true),
        Map.of("value", "http://${data.azurerm_public_ip.ip.fqdn}", "description", "The URL")));
    assertThat(json.get("//")).isEqualTo(Map.of(
        "metadata", Map.of("backend", "local", "stackName", "demo", "version", TerraformStack.CDKTN_VERSION),
        "outputs", Map.of("azureFeature", Map.of("url", "azureFeatureurl_" + LogicalIds.pathHash(
            List.of("azureFeature", "url"))))));

    Map<String, Object> manifest = BoosterJson.parseObject(Files.readString(root.resolve("cdktf.out/manifest.json")));
    assertThat(manifest).isEqualTo(Map.of("version", TerraformStack.CDKTN_VERSION, "stacks", Map.of("demo", Map.of(
        "name", "demo", "constructPath", "demo", "synthesizedStackPath", "stacks/demo/cdk.tf.json",
        "workingDirectory", "stacks/demo", "annotations", List.of(), "dependencies", List.of()))));
  }

  @Test
  void writes_sorted_keys_with_two_spaces() {
    TerraformStack stack = new TerraformStack(new TerraformApp(root), "s");
    new TerraformResource(stack, "b", "t").setAttribute("z", 1).setAttribute("a", true);
    assertThat(stack.toJson()).contains("""
          "resource": {
            "t": {
              "b": {
                "//": {
                  "metadata": {
                    "path": "s/b",
                    "uniqueId": "b"
                  }
                },
                "a": true,
                "z": 1
              }
            }
          },
        """);
  }

  @Test
  void rejects_duplicated_construct_ids_with_the_constructs_message() {
    TerraformStack stack = new TerraformStack(new TerraformApp(root), "my-storeproduction");
    new TerraformResource(stack, "pip", "azurerm_public_ip");
    assertThatThrownBy(() -> new TerraformResource(stack, "pip", "azurerm_public_ip"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("There is already a Construct with name 'pip' in TerraformStack [my-storeproduction]");
    TerraformProvider provider = new TerraformProvider(stack, "azureFeature", "azurerm", "azurerm", "~> 5.0");
    new TerraformOutput(provider, "httpURL", "x", null);
    assertThatThrownBy(() -> new TerraformOutput(provider, "httpURL", "y", null))
        .hasMessage("There is already a Construct with name 'httpURL' in AzurermProvider [azureFeature]");
  }

  @Test
  void elements_need_a_stack() {
    TerraformApp app = new TerraformApp(root);
    assertThatThrownBy(() -> new TerraformResource(app, "orphan", "t"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("No stack could be identified for the construct at path 'orphan'");
  }
}
