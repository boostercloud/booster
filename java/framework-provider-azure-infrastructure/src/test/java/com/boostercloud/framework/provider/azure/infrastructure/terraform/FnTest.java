package com.boostercloud.framework.provider.azure.infrastructure.terraform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FnTest {

  @Test
  void renders_merge_of_maps_as_an_interpolation() {
    Map<String, Object> defaults = new LinkedHashMap<>();
    defaults.put("A", "1");
    defaults.put("REF", "${azurerm_storage_account.sp.primary_connection_string}");
    defaults.put("MIXED", "https://${azurerm_cosmosdb_account.x.name}.documents.azure.com");
    Map<String, Object> rockets = new LinkedHashMap<>();
    rockets.put("ROCKET", "");
    assertThat(Fn.merge(List.of(defaults, rockets))).isEqualTo("${merge({\"A\" = \"1\", \"REF\" ="
        + " azurerm_storage_account.sp.primary_connection_string, \"MIXED\" ="
        + " \"https://${azurerm_cosmosdb_account.x.name}.documents.azure.com\"}, {\"ROCKET\" = \"\"})}");
  }

  @Test
  void escapes_literals() {
    assertThat(Fn.renderExpression("a \"quoted\" \\ value\nwith %{x}")).isEqualTo("\"a \\\"quoted\\\" \\\\ value\\nwith %%{x}\"");
  }

  @Test
  void renders_numbers_booleans_lists_and_nested_expressions() {
    assertThat(Fn.renderExpression(List.of(1, 2.5, true, "${x.y}"))).isEqualTo("[1, 2.5, true, x.y]");
    assertThat(Fn.renderExpression("${merge({\"a\" = \"}\"}, {})}")).isEqualTo("merge({\"a\" = \"}\"}, {})");
    assertThat(Fn.renderExpression("$${literal}")).isEqualTo("\"$${literal}\"");
  }
}
