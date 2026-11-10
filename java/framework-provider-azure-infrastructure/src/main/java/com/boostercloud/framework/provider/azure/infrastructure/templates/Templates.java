package com.boostercloud.framework.provider.azure.infrastructure.templates;

import com.boostercloud.framework.provider.azure.infrastructure.helper.Utils;
import java.util.Map;

/**
 * Port of {@code templates/index.ts} and {@code templates/policy.ts}: the templates exported to rockets.
 *
 * <p>{@code templates/cdktf.ts} (the {@code cdktf.json} of {@code cdktn-cli}) has no counterpart: the Java
 * infrastructure synthesizes the Terraform JSON itself and runs Terraform directly, so no {@code cdktf.json} is
 * written.
 */
public final class Templates {

  /**
   * {@code templates.policy}: the legacy API Management policy (Mustache, variable {@code {{functionAppName}}}),
   * verbatim (the {@code <outbound>} line keeps its two trailing spaces).
   */
  public static final String POLICY =
      "<policies>\n"
      + "    <inbound>\n"
      + "        <base/>\n"
      + "        <set-backend-service base-url=\"https://{{functionAppName}}.azurewebsites.net/api\"/>\n"
      + "        <cors>\n"
      + "            <allowed-origins>\n"
      + "                <origin>*</origin>\n"
      + "            </allowed-origins>\n"
      + "            <allowed-methods>\n"
      + "                <method>GET</method>\n"
      + "                <method>POST</method>\n"
      + "                <method>OPTIONS</method>\n"
      + "            </allowed-methods>\n"
      + "            <allowed-headers>\n"
      + "                <header>accept</header>\n"
      + "                <header>accept-encoding</header>\n"
      + "                <header>access-control-request-headers</header>\n"
      + "                <header>access-control-request-method</header>\n"
      + "                <header>connection</header>\n"
      + "                <header>content-type</header>\n"
      + "                <header>host</header>\n"
      + "                <header>origin</header>\n"
      + "                <header>referer</header>\n"
      + "                <header>user-agent</header>\n"
      + "            </allowed-headers>\n"
      + "        </cors>\n"
      + "    </inbound>\n"
      + "    <backend>\n"
      + "        <base/>\n"
      + "    </backend>\n"
      + "    <outbound>  \n"
      + "        <base/>\n"
      + "    </outbound>\n"
      + "    <on-error>\n"
      + "        <base/>\n"
      + "    </on-error>\n"
      + "</policies>";

  private Templates() {}

  /**
   * @return {@code templates}: {@code { policy }}
   */
  public static Map<String, String> templates() {
    return Map.of("policy", POLICY);
  }

  /**
   * Renders {@link #POLICY} for a function app.
   *
   * @param functionAppName the function app name
   * @return the policy XML
   */
  public static String renderPolicy(String functionAppName) {
    return Utils.renderTemplate(POLICY, Map.of("functionAppName", functionAppName));
  }
}
