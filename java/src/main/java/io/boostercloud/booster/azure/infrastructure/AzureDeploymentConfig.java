package io.boostercloud.booster.azure.infrastructure;

import com.fasterxml.jackson.databind.DeserializationFeature;
import io.boostercloud.booster.core.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Collections;
import java.util.Objects;

/** Explicit deployment inputs. Credentials are supplied by Azure Identity, never by this file. */
public record AzureDeploymentConfig(String application, String subscriptionId, String resourceGroup,
    String location, String image, String applicationClass, List<String> containers, int replicas, int port, boolean enableUploads,
    Map<String, List<String>> additionalStorageAccounts) {
  public AzureDeploymentConfig(String application, String subscriptionId, String resourceGroup,
      String location, String image, String applicationClass, List<String> containers, int replicas, int port) {
    this(application, subscriptionId, resourceGroup, location, image, applicationClass, containers, replicas, port, false, Map.of());
  }
  public AzureDeploymentConfig(String application, String subscriptionId, String resourceGroup,
      String location, String image, String applicationClass, List<String> containers, int replicas, int port, boolean enableUploads) {
    this(application, subscriptionId, resourceGroup, location, image, applicationClass, containers, replicas, port, enableUploads, Map.of());
  }
  public AzureDeploymentConfig {
    require(application, "[a-z][a-z0-9-]{2,25}", "application (3–26 lowercase letters, digits or hyphens)");
    require(subscriptionId, "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "subscriptionId");
    require(resourceGroup, "[a-zA-Z0-9_().-]{1,80}", "resourceGroup");
    if (resourceGroup.endsWith(".")) throw new IllegalArgumentException("resourceGroup must not end with a dot");
    require(location, "[a-z][a-z0-9]{1,40}", "location");
    require(image, "[^\\s]{3,512}", "image");
    require(applicationClass, "[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+", "applicationClass");
    containers = containers == null || containers.isEmpty() ? List.of("uploads") : List.copyOf(containers);
    validateContainers(containers);
    var accounts = new TreeMap<String, List<String>>();
    if (additionalStorageAccounts != null) {
      for (var entry : additionalStorageAccounts.entrySet()) {
        require(entry.getKey(), "[a-z0-9]{3,24}", "additional storage account (3–24 lowercase letters or digits)");
        if (entry.getKey().equals("bj" + resourceSuffix(subscriptionId, resourceGroup, application)))
          throw new IllegalArgumentException("Additional storage account duplicates the primary account");
        var names = List.copyOf(Objects.requireNonNull(entry.getValue(), "Additional account containers required"));
        validateContainers(names); accounts.put(entry.getKey(), names);
      }
    }
    if (accounts.size() > 16) throw new IllegalArgumentException("At most 16 additional storage accounts are supported per deployment");
    additionalStorageAccounts = Collections.unmodifiableMap(accounts);
    if (replicas == 0) replicas = 1;
    if (replicas < 1 || replicas > 20) throw new IllegalArgumentException("replicas must be between 1 and 20");
    if (port == 0) port = 8080;
    if (port < 1024 || port > 65535) throw new IllegalArgumentException("port must be between 1024 and 65535");
  }
  private static void require(String value, String pattern, String name) {
    if (value == null || !value.matches(pattern)) throw new IllegalArgumentException("Invalid " + name);
  }
  private static void validateContainers(List<String> containers) {
    if (containers.isEmpty() || containers.size() > 32 || containers.stream().distinct().count() != containers.size())
      throw new IllegalArgumentException("Choose 1–32 distinct storage containers per account");
    for (String container : containers) {
      require(container, "[a-z0-9][a-z0-9-]{1,61}[a-z0-9]", "container");
      if (container.contains("--")) throw new IllegalArgumentException("Container names cannot contain consecutive hyphens");
    }
  }
  public static AzureDeploymentConfig read(Path path) throws IOException {
    try (var input = Files.newInputStream(path)) {
      return Json.MAPPER.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(input, AzureDeploymentConfig.class);
    }
  }
  /** Resource names must be globally unique without leaking subscription IDs. */
  public String suffix() {
    return resourceSuffix(subscriptionId, resourceGroup, application);
  }
  private static String resourceSuffix(String subscriptionId, String resourceGroup, String application) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
          (subscriptionId.toLowerCase(java.util.Locale.ROOT) + "/" + resourceGroup.toLowerCase(java.util.Locale.ROOT) + "/" + application)
              .getBytes(StandardCharsets.UTF_8))).substring(0, 12);
    } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
  }
  public String storageName() { return "bj" + suffix(); }
  public Map<String, List<String>> storageAccounts() {
    var accounts = new TreeMap<>(additionalStorageAccounts); accounts.put(storageName(), containers);
    return Collections.unmodifiableMap(accounts);
  }
  public String cosmosName() { return application + "-" + suffix(); }
  public String hubNamespace() { return application + "-" + suffix(); }
  public String pubsubName() { return application + "-" + suffix(); }
  public String resourceGroupPath() { return "/subscriptions/" + subscriptionId + "/resourceGroups/" + resourceGroup; }
  public String deploymentName() { return "booster-java-" + application; }
}
