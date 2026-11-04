package io.boostercloud.booster.azure.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AzureEventGridInfrastructureTest {
  private static AzureDeploymentConfig config(boolean uploads) {
    return new AzureDeploymentConfig("test-app", "00000000-0000-0000-0000-000000000001", "test-group", "westeurope",
        "example.invalid/test:1", "dev.example.App", List.of("uploads"), 1, 8080, uploads);
  }
  @Test void webhookExistsOnlyWhenUploadRocketIsEnabled() {
    JsonNode disabled = AzureInfrastructure.template(config(false)), enabled = AzureInfrastructure.template(config(true));
    assertEquals(0, resources(disabled, "Microsoft.EventGrid/systemTopics").size());
    assertEquals(0, resources(disabled, "Microsoft.EventGrid/systemTopics/eventSubscriptions").size());
    assertEquals(1, resources(enabled, "Microsoft.EventGrid/systemTopics").size());
    JsonNode subscription = resources(enabled, "Microsoft.EventGrid/systemTopics/eventSubscriptions").getFirst();
    JsonNode properties = subscription.path("properties");
    assertEquals("EventGridSchema", properties.path("eventDeliverySchema").asText());
    assertEquals("Microsoft.Storage.BlobCreated", properties.path("filter").path("includedEventTypes").get(0).asText());
    JsonNode destination = properties.path("destination").path("properties");
    assertTrue(destination.path("endpointUrl").asText().contains("/azure/files"));
    assertFalse(destination.path("endpointUrl").asText().contains("upstreamToken"));
    JsonNode header = destination.path("deliveryAttributeMappings").get(0);
    assertEquals("Authorization", header.path("name").asText());
    assertTrue(header.path("properties").path("isSecret").asBoolean());
    assertEquals("[concat('Bearer ', parameters('upstreamToken'))]", header.path("properties").path("value").asText());
  }
  @Test void callbackSecretRotationChangesRevisionAndStartupWaitsForDataResources() {
    JsonNode application = resources(AzureInfrastructure.template(config(true)), "Microsoft.App/containerApps").getFirst();
    JsonNode env = application.path("properties").path("template").path("containers").get(0).path("env");
    var variables = new HashMap<String, JsonNode>(); env.forEach(item -> variables.put(item.path("name").asText(), item));
    assertEquals("true", variables.get("BOOSTER_AZURE_UPLOADS_ENABLED").path("value").asText());
    assertEquals("upstream-token", variables.get("BOOSTER_AZURE_UPSTREAM_TOKEN").path("secretRef").asText());
    assertFalse(variables.get("BOOSTER_AZURE_UPSTREAM_TOKEN").has("value"));
    assertEquals("[uniqueString(parameters('upstreamToken'))]", variables.get("BOOSTER_AZURE_UPSTREAM_VERSION").path("value").asText());
    String dependencies = application.path("dependsOn").toString();
    assertTrue(dependencies.contains("sqlDatabases/containers")); assertTrue(dependencies.contains("sqlRoleAssignments"));
    assertTrue(dependencies.contains("extensionResourceId")); assertTrue(dependencies.contains("Microsoft.Authorization/roleAssignments"));
  }
  @Test void everyStorageAccountGetsContainersIdentityAndBlobNotifications() throws Exception {
    var base = config(true);
    var config = new AzureDeploymentConfig(base.application(), base.subscriptionId(), base.resourceGroup(), base.location(),
        base.image(), base.applicationClass(), base.containers(), base.replicas(), base.port(), true,
        Map.of("secondaccount123", List.of("documents", "images"), "thirdaccount123", List.of("archives")));
    var template = AzureInfrastructure.template(config);
    assertEquals(3, resources(template, "Microsoft.Storage/storageAccounts").size());
    assertEquals(4, resources(template, "Microsoft.Storage/storageAccounts/blobServices/containers").size());
    assertEquals(3, resources(template, "Microsoft.EventGrid/systemTopics").size());
    assertEquals(3, resources(template, "Microsoft.EventGrid/systemTopics/eventSubscriptions").size());
    long blobRoles = resources(template, "Microsoft.Authorization/roleAssignments").stream()
        .filter(role -> role.path("properties").path("roleDefinitionId").asText().contains("ba92f5b4-2d11-453d-a403-e96b0029c9fe")).count();
    assertEquals(3, blobRoles);
    JsonNode environment = resources(template, "Microsoft.App/containerApps").getFirst().path("properties").path("template").path("containers").get(0).path("env");
    JsonNode accounts = null;
    for (var variable : environment) if (variable.path("name").asText().equals("BOOSTER_AZURE_STORAGE_ACCOUNTS"))
      accounts = io.boostercloud.booster.core.Json.MAPPER.readTree(variable.path("value").asText());
    assertNotNull(accounts); assertEquals(3, accounts.size());
    assertEquals("https://secondaccount123.blob.core.windows.net", accounts.path("secondaccount123").path("endpoint").asText());
    assertEquals(2, accounts.path("secondaccount123").path("containers").size());
  }
  @Test void additionalAccountsRejectInvalidNamesAndPrimaryCollisions() {
    var base = config(true);
    for (String account : List.of("Uppercase", "bad-name", "[parameters('x')]", base.storageName()))
      assertThrows(IllegalArgumentException.class, () -> new AzureDeploymentConfig(base.application(), base.subscriptionId(), base.resourceGroup(), base.location(),
          base.image(), base.applicationClass(), base.containers(), base.replicas(), base.port(), true, Map.of(account, List.of("uploads"))));
  }
  private static List<JsonNode> resources(JsonNode template, String type) {
    var result = new ArrayList<JsonNode>();
    template.path("resources").forEach(resource -> { if (type.equals(resource.path("type").asText())) result.add(resource); });
    return result;
  }
}
