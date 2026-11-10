package com.boostercloud.framework.provider.azure.infrastructure.templates;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TemplatesTest {

  @Test
  void exports_the_policy_template_verbatim() {
    assertThat(Templates.templates()).containsOnlyKeys("policy");
    assertThat(Templates.POLICY).startsWith("<policies>\n    <inbound>\n").endsWith("</on-error>\n</policies>")
        .contains("    <outbound>  \n")
        .contains("<set-backend-service base-url=\"https://{{functionAppName}}.azurewebsites.net/api\"/>");
  }

  @Test
  void renders_the_function_app_name() {
    assertThat(Templates.renderPolicy("myappfunc"))
        .contains("<set-backend-service base-url=\"https://myappfunc.azurewebsites.net/api\"/>");
  }
}
