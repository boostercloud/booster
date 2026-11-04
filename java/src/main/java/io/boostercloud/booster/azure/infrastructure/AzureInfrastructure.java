package io.boostercloud.booster.azure.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.boostercloud.booster.core.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Deterministic ARM synthesis for a Java 27 container and the Azure provider services. */
public final class AzureInfrastructure {
  public static final String OWNER_TAG = "booster-java-application";
  private AzureInfrastructure() {}
  public static ObjectNode template(AzureDeploymentConfig config) {
    ObjectNode template = Json.MAPPER.createObjectNode();
    template.put("$schema", "https://schema.management.azure.com/schemas/2019-04-01/deploymentTemplate.json#");
    template.put("contentVersion", "1.0.0.0");
    template.putObject("parameters").putObject("upstreamToken").put("type", "secureString")
        .put("defaultValue", "[newGuid()]");
    ArrayNode resources = template.putArray("resources");
    List<String> applicationDependencies = new ArrayList<>();
    String identityName = config.application() + "-identity";
    String identityId = id("Microsoft.ManagedIdentity/userAssignedIdentities", identityName);
    String principal = "[reference(" + expression(identityId) + ", '2023-01-31').principalId]";
    var identity = resource("Microsoft.ManagedIdentity/userAssignedIdentities", "2023-01-31", identityName, config);
    identity.putObject("properties"); resources.add(identity);

    String cosmosId = id("Microsoft.DocumentDB/databaseAccounts", config.cosmosName());
    var cosmos = resource("Microsoft.DocumentDB/databaseAccounts", "2024-05-15", config.cosmosName(), config);
    cosmos.put("kind", "GlobalDocumentDB");
    cosmos.set("properties", node(Map.of("databaseAccountOfferType", "Standard", "disableLocalAuth", true,
        "consistencyPolicy", Map.of("defaultConsistencyLevel", "Session"),
        "locations", List.of(Map.of("locationName", config.location(), "failoverPriority", 0)),
        "capabilities", List.of(Map.of("name", "EnableServerless")))));
    resources.add(cosmos);
    String database = config.cosmosName() + "/booster";
    var db = child("Microsoft.DocumentDB/databaseAccounts/sqlDatabases", "2024-05-15", database, cosmosId);
    db.set("properties", node(Map.of("resource", Map.of("id", "booster")))); resources.add(db);
    String dbId = id("Microsoft.DocumentDB/databaseAccounts/sqlDatabases", config.cosmosName(), "booster");
    for (String name : List.of("journal", "state", "leases")) {
      var container = child("Microsoft.DocumentDB/databaseAccounts/sqlDatabases/containers", "2024-05-15", database + "/" + name, dbId);
      var properties = container.putObject("properties").putObject("resource");
      properties.put("id", name);
      properties.set("partitionKey", node(Map.of("paths", List.of(name.equals("leases") ? "/id" : "/pk"), "kind", "Hash", "version", 2)));
      if (name.equals("state")) properties.put("defaultTtl", -1);
      properties.set("indexingPolicy", node(Map.of("indexingMode", "consistent", "automatic", true,
          "includedPaths", List.of(Map.of("path", "/*")), "excludedPaths", List.of(Map.of("path", "/\"_etag\"/?")))));
      resources.add(container);
      applicationDependencies.add(id("Microsoft.DocumentDB/databaseAccounts/sqlDatabases/containers", config.cosmosName(), "booster", name));
    }
    var cosmosRole = child("Microsoft.DocumentDB/databaseAccounts/sqlRoleAssignments", "2024-05-15",
        "[concat('" + config.cosmosName() + "/', guid(" + expression(cosmosId) + ", 'booster-java-data'))]", cosmosId, identityId);
    cosmosRole.set("properties", node(Map.of("roleDefinitionId", "[concat(" + expression(cosmosId) + ", '/sqlRoleDefinitions/00000000-0000-0000-0000-000000000002')]",
        "principalId", principal, "scope", cosmosId))); resources.add(cosmosRole);
    applicationDependencies.add("[resourceId('Microsoft.DocumentDB/databaseAccounts/sqlRoleAssignments', '" + config.cosmosName()
        + "', guid(" + expression(cosmosId) + ", 'booster-java-data'))]");

    String storageId = id("Microsoft.Storage/storageAccounts", config.storageName());
    for (var account : config.storageAccounts().entrySet()) {
      String accountId = id("Microsoft.Storage/storageAccounts", account.getKey());
      var storage = resource("Microsoft.Storage/storageAccounts", "2023-05-01", account.getKey(), config);
      storage.put("kind", "StorageV2"); storage.set("sku", node(Map.of("name", "Standard_LRS")));
      storage.set("properties", node(Map.of("minimumTlsVersion", "TLS1_2", "supportsHttpsTrafficOnly", true,
          "allowBlobPublicAccess", false, "allowSharedKeyAccess", false)));
      resources.add(storage);
      for (String name : account.getValue()) {
        var container = child("Microsoft.Storage/storageAccounts/blobServices/containers", "2023-05-01",
            account.getKey() + "/default/" + name, accountId);
        container.set("properties", node(Map.of("publicAccess", "None"))); resources.add(container);
        applicationDependencies.add(id("Microsoft.Storage/storageAccounts/blobServices/containers", account.getKey(), "default", name));
      }
      applicationDependencies.add(addRole(resources, accountId, identityId, principal, "ba92f5b4-2d11-453d-a403-e96b0029c9fe", "blob-data"));
    }

    String eventHubId = id("Microsoft.EventHub/namespaces", config.hubNamespace());
    var hubNamespace = resource("Microsoft.EventHub/namespaces", "2024-01-01", config.hubNamespace(), config);
    hubNamespace.set("sku", node(Map.of("name", "Standard", "tier", "Standard", "capacity", 1)));
    hubNamespace.set("properties", node(Map.of("minimumTlsVersion", "1.2", "disableLocalAuth", true))); resources.add(hubNamespace);
    var hub = child("Microsoft.EventHub/namespaces/eventhubs", "2024-01-01", config.hubNamespace() + "/booster", eventHubId);
    hub.set("properties", node(Map.of("partitionCount", 2, "retentionDescription", Map.of("cleanupPolicy", "Delete", "retentionTimeInHours", 24)))); resources.add(hub);
    applicationDependencies.add(id("Microsoft.EventHub/namespaces/eventhubs", config.hubNamespace(), "booster"));
    applicationDependencies.add(addRole(resources, eventHubId, identityId, principal, "f526a384-b230-433a-b45c-95f59c4a2dec", "hub-send"));
    applicationDependencies.add(addRole(resources, eventHubId, identityId, principal, "a638d3c7-ab3a-418d-83e6-5f17a39d4fde", "hub-receive"));

    String pubsubId = id("Microsoft.SignalRService/WebPubSub", config.pubsubName());
    var pubsub = resource("Microsoft.SignalRService/WebPubSub", "2024-03-01", config.pubsubName(), config);
    pubsub.set("sku", node(Map.of("name", "Standard_S1", "tier", "Standard", "capacity", 1)));
    pubsub.set("properties", node(Map.of("disableLocalAuth", true, "disableAadAuth", false,
        "publicNetworkAccess", "Enabled", "tls", Map.of("clientCertEnabled", false)))); resources.add(pubsub);
    applicationDependencies.add(addRole(resources, pubsubId, identityId, principal, "12cf5a90-567b-43ae-8102-96cf46c7d9b4", "pubsub-owner"));

    String environmentName = config.application() + "-environment";
    String environmentId = id("Microsoft.App/managedEnvironments", environmentName);
    var environment = resource("Microsoft.App/managedEnvironments", "2024-03-01", environmentName, config);
    environment.set("properties", node(Map.of("workloadProfiles", List.of(Map.of("name", "Consumption", "workloadProfileType", "Consumption")))));
    resources.add(environment);
    var application = resource("Microsoft.App/containerApps", "2024-03-01", config.application(), config);
    application.set("identity", node(Map.of("type", "UserAssigned", "userAssignedIdentities", Map.of(identityId, Map.of()))));
    applicationDependencies.addAll(List.of(environmentId, identityId, cosmosId, storageId, eventHubId, pubsubId));
    application.set("dependsOn", node(applicationDependencies));
    List<Map<String, String>> env = new ArrayList<>();
    env.add(env("AZURE_CLIENT_ID", "[reference(" + expression(identityId) + ", '2023-01-31').clientId]"));
    env.add(env("BOOSTER_AZURE_COSMOS_ENDPOINT", "https://" + config.cosmosName() + ".documents.azure.com:443/"));
    env.add(env("BOOSTER_AZURE_DATABASE", "booster"));
    env.add(env("BOOSTER_AZURE_JOURNAL_CONTAINER", "journal"));
    env.add(env("BOOSTER_AZURE_STATE_CONTAINER", "state"));
    env.add(env("BOOSTER_AZURE_LEASE_CONTAINER", "leases"));
    env.add(env("BOOSTER_AZURE_APPLICATION", config.application()));
    env.add(env("BOOSTER_AZURE_EVENTHUB_NAMESPACE", config.hubNamespace() + ".servicebus.windows.net"));
    env.add(env("BOOSTER_AZURE_EVENTHUB_NAME", "booster"));
    env.add(env("BOOSTER_AZURE_WEBPUBSUB_ENDPOINT", "https://" + config.pubsubName() + ".webpubsub.azure.com"));
    env.add(env("BOOSTER_AZURE_STORAGE_ENDPOINT", "https://" + config.storageName() + ".blob.core.windows.net"));
    env.add(env("BOOSTER_AZURE_STORAGE_CONTAINERS", String.join(",", config.containers())));
    var storageAccounts = Json.MAPPER.createObjectNode();
    config.storageAccounts().forEach((account, containers) -> storageAccounts.set(account,
        node(Map.of("endpoint", "https://" + account + ".blob.core.windows.net", "containers", containers))));
    env.add(env("BOOSTER_AZURE_STORAGE_ACCOUNTS", storageAccounts.toString()));
    env.add(env("BOOSTER_AZURE_UPLOADS_ENABLED", Boolean.toString(config.enableUploads())));
    env.add(env("BOOSTER_APPLICATION_CLASS", config.applicationClass()));
    env.add(env("PORT", Integer.toString(config.port())));
    env.add(Map.of("name", "BOOSTER_AZURE_UPSTREAM_TOKEN", "secretRef", "upstream-token"));
    // Secret changes alone do not create a Container Apps revision. A nonsecret
    // version changes the template so workers reload the rotated callback token.
    env.add(env("BOOSTER_AZURE_UPSTREAM_VERSION", "[uniqueString(parameters('upstreamToken'))]"));
    application.set("properties", node(Map.of("managedEnvironmentId", environmentId,
        "configuration", Map.of("activeRevisionsMode", "Single", "secrets", List.of(Map.of("name", "upstream-token", "value", "[parameters('upstreamToken')]")), "ingress", Map.of("external", true,
            "targetPort", config.port(), "transport", "http", "allowInsecure", false)),
        "template", Map.of("terminationGracePeriodSeconds", 60, "containers", List.of(Map.of("name", "booster", "image", config.image(), "env", env,
            "resources", Map.of("cpu", 1.0, "memory", "2Gi"),
            "probes", List.of(Map.of("type", "Liveness", "httpGet", Map.of("path", "/health", "port", config.port()),
                "initialDelaySeconds", 30, "periodSeconds", 30),
                Map.of("type", "Readiness", "httpGet", Map.of("path", "/health", "port", config.port()), "periodSeconds", 10)))),
            "scale", Map.of("minReplicas", config.replicas(), "maxReplicas", config.replicas())))));
    resources.add(application);
    String applicationId = id("Microsoft.App/containerApps", config.application());
    var hubConfig = child("Microsoft.SignalRService/WebPubSub/hubs", "2024-03-01", config.pubsubName() + "/booster", pubsubId, applicationId);
    hubConfig.set("properties", node(Map.of("eventHandlers", List.of(Map.of("urlTemplate",
        "[concat('https://', reference(" + expression(applicationId) + ", '2024-03-01').configuration.ingress.fqdn, '/azure/webpubsub?code=', uriComponent(parameters('upstreamToken')))]",
        "userEventPattern", "*", "systemEvents", List.of("connect", "connected", "disconnected"))))));
    resources.add(hubConfig);
    if (config.enableUploads()) {
      for (String account : config.storageAccounts().keySet()) {
        String accountId = id("Microsoft.Storage/storageAccounts", account);
        String topicName = config.application() + "-" + account + "-uploads";
        String topicId = id("Microsoft.EventGrid/systemTopics", topicName);
        var topic = resource("Microsoft.EventGrid/systemTopics", "2022-06-15", topicName, config);
        topic.set("dependsOn", node(List.of(accountId)));
        topic.set("properties", node(Map.of("source", accountId, "topicType", "Microsoft.Storage.StorageAccounts")));
        resources.add(topic);
        var eventSubscription = child("Microsoft.EventGrid/systemTopics/eventSubscriptions", "2022-06-15",
            topicName + "/uploads", topicId, applicationId);
        eventSubscription.set("properties", node(Map.of(
            "eventDeliverySchema", "EventGridSchema",
            "filter", Map.of("includedEventTypes", List.of("Microsoft.Storage.BlobCreated")),
            "retryPolicy", Map.of("maxDeliveryAttempts", 30, "eventTimeToLiveInMinutes", 1440),
            "destination", Map.of("endpointType", "WebHook", "properties", Map.of(
                "endpointUrl", "[concat('https://', reference(" + expression(applicationId) + ", '2024-03-01').configuration.ingress.fqdn, '/azure/files')]",
                "maxEventsPerBatch", 1, "preferredBatchSizeInKilobytes", 64,
                "deliveryAttributeMappings", List.of(Map.of("name", "Authorization", "type", "Static", "properties", Map.of(
                    "value", "[concat('Bearer ', parameters('upstreamToken'))]", "isSecret", true))))))));
        resources.add(eventSubscription);
      }
    }
    var outputs = template.putObject("outputs");
    outputs.set("url", output("[concat('https://', reference(" + expression(applicationId) + ", '2024-03-01').configuration.ingress.fqdn)]"));
    outputs.set("cosmosEndpoint", output("https://" + config.cosmosName() + ".documents.azure.com:443/"));
    outputs.set("storageEndpoint", output("https://" + config.storageName() + ".blob.core.windows.net"));
    outputs.set("webPubSubEndpoint", output("https://" + config.pubsubName() + ".webpubsub.azure.com"));
    return template;
  }
  public static Path write(AzureDeploymentConfig config, Path path) throws IOException {
    path = path.toAbsolutePath().normalize();
    if (path.getParent() != null) Files.createDirectories(path.getParent());
    Files.writeString(path, Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(template(config)) + "\n");
    return path;
  }
  private static ObjectNode resource(String type, String version, String name, AzureDeploymentConfig config) {
    var result = child(type, version, name); result.put("location", config.location());
    result.set("tags", node(Map.of(OWNER_TAG, config.application()))); return result;
  }
  private static ObjectNode child(String type, String version, String name, String... dependencies) {
    var result = Json.MAPPER.createObjectNode().put("type", type).put("apiVersion", version).put("name", name);
    if (dependencies.length > 0) result.set("dependsOn", node(List.of(dependencies)));
    return result;
  }
  private static String addRole(ArrayNode resources, String scope, String identity, String principal, String role, String label) {
    var assignment = child("Microsoft.Authorization/roleAssignments", "2022-04-01",
        "[guid(" + expression(scope) + ", '" + label + "', " + expression(identity) + ")]", scope, identity);
    assignment.put("scope", scope);
    assignment.set("properties", node(Map.of("roleDefinitionId", "[subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '" + role + "')]",
        "principalId", principal, "principalType", "ServicePrincipal")));
    resources.add(assignment);
    return "[extensionResourceId(" + expression(scope) + ", 'Microsoft.Authorization/roleAssignments', guid("
        + expression(scope) + ", '" + label + "', " + expression(identity) + "))]";
  }
  private static String id(String type, String... names) {
    return "[resourceId('" + type + "', '" + String.join("', '", names) + "')]";
  }
  private static String expression(String value) { return value.substring(1, value.length() - 1); }
  private static JsonNode node(Object value) { return Json.MAPPER.valueToTree(value); }
  private static JsonNode output(String value) { return node(Map.of("type", "string", "value", value)); }
  private static Map<String, String> env(String name, String value) { return Map.of("name", name, "value", value); }
}
