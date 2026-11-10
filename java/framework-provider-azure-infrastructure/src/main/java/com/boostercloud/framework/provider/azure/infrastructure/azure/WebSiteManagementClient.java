package com.boostercloud.framework.provider.azure.infrastructure.azure;

import static com.boostercloud.framework.provider.azure.infrastructure.azure.ArmClient.segment;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The two {@code @azure/arm-appservice} ({@code WebSiteManagementClient.webApps}) operations the deploy flow uses.
 */
public final class WebSiteManagementClient {

  /** The {@code Microsoft.Web} API version. */
  public static final String API_VERSION = "2023-12-01";

  private final ArmClient arm;

  /**
   * @param arm the Resource Manager client
   */
  public WebSiteManagementClient(ArmClient arm) {
    this.arm = Objects.requireNonNull(arm, "arm");
  }

  /**
   * The publishing credentials of a site ({@code User} of {@code @azure/arm-appservice}).
   *
   * @param name the resource name ({@code User.name}: the site name, used as the Kudu host)
   * @param publishingUserName the publishing user
   * @param publishingPassword the publishing password
   * @param scmUri the SCM URI, if returned
   */
  public record User(String name, String publishingUserName, String publishingPassword, String scmUri) {}

  /**
   * The keys of a function app host ({@code HostKeys}).
   *
   * @param masterKey the master key
   * @param functionKeys the host function keys
   * @param systemKeys the system keys (extensions such as {@code webpubsub_extension})
   */
  public record HostKeys(String masterKey, Map<String, String> functionKeys, Map<String, String> systemKeys) {}

  /**
   * {@code webApps.beginListPublishingCredentials(resourceGroupName, name)} + {@code pollUntilDone()}: {@code POST
   * /resourceGroups/<rg>/providers/Microsoft.Web/sites/<name>/config/publishingcredentials/list}.
   *
   * @param resourceGroupName the resource group
   * @param name the function app
   * @return the credentials
   */
  public User listPublishingCredentials(String resourceGroupName, String name) {
    Map<String, Object> result = arm.beginAndWait("POST", sitePath(resourceGroupName, name)
        + "/config/publishingcredentials/list", API_VERSION, null);
    Map<?, ?> properties = result.get("properties") instanceof Map<?, ?> map ? map : Map.of();
    return new User(
        string(result.get("name")),
        string(properties.get("publishingUserName")),
        string(properties.get("publishingPassword")),
        string(properties.get("scmUri")));
  }

  /**
   * {@code webApps.listHostKeys(resourceGroupName, name)}: {@code POST .../sites/<name>/host/default/listkeys}.
   *
   * @param resourceGroupName the resource group
   * @param name the function app
   * @return the keys
   */
  public HostKeys listHostKeys(String resourceGroupName, String name) {
    Map<String, Object> result = arm.request("POST", sitePath(resourceGroupName, name) + "/host/default/listkeys",
        API_VERSION, null);
    return new HostKeys(string(result.get("masterKey")), stringMap(result.get("functionKeys")),
        stringMap(result.get("systemKeys")));
  }

  private static String sitePath(String resourceGroupName, String name) {
    return "/resourceGroups/" + segment(resourceGroupName) + "/providers/Microsoft.Web/sites/" + segment(name);
  }

  private static String string(Object value) {
    return value == null ? null : value.toString();
  }

  private static Map<String, String> stringMap(Object value) {
    if (!(value instanceof Map<?, ?> map)) {
      return null;
    }
    Map<String, String> result = new LinkedHashMap<>();
    map.forEach((key, item) -> result.put(String.valueOf(key), item == null ? null : item.toString()));
    return result;
  }
}
