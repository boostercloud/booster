package com.boostercloud.framework.provider.azure.infrastructure.functions;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.provider.azure.infrastructure.helper.FunctionZip;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionDefinition;
import com.boostercloud.framework.types.BoosterConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Port of {@code test/infrastructure/functions/graphql-functions.test.ts}. */
class GraphqlFunctionsTest {

  private final BoosterConfig config = new BoosterConfig("test");

  @Test
  void create_the_expected_GraphQLFunctionDefiniton() {
    FunctionDefinition definition = new GraphqlFunction(config).getFunctionDefinition();
    assertThat(definition).isNotNull();
    assertThat(definition.name()).isEqualTo("graphql");
    assertThat(definition.config().bindings().get(0).type()).isEqualTo("httpTrigger");
    assertThat(definition.config().bindings().get(0).name()).isEqualTo("rawRequest");
    assertThat(definition.config().bindings().get(0).direction()).isEqualTo("in");
    assertThat(definition.config().bindings().get(0).get("authLevel")).isEqualTo("anonymous");
    assertThat(definition.config().bindings().get(0).get("methods")).isEqualTo(List.of("post"));
    assertThat(definition.config().bindings().get(1).type()).isEqualTo("http");
    assertThat(definition.config().bindings().get(1).name()).isEqualTo("$return");
    assertThat(definition.config().bindings().get(1).direction()).isEqualTo("out");
    assertThat(definition.config().scriptFile()).isEqualTo("../dist/index.js");
    assertThat(definition.config().entryPoint()).isEqualTo("boosterServeGraphQL");
  }

  @Test
  void writes_the_function_json_of_the_legacy_layout() {
    assertThat(FunctionZip.functionJson(new GraphqlFunction(config).getFunctionDefinition().config())).isEqualTo("""
        {
          "bindings": [
            {
              "authLevel": "anonymous",
              "type": "httpTrigger",
              "direction": "in",
              "name": "rawRequest",
              "methods": [
                "post"
              ]
            },
            {
              "type": "http",
              "direction": "out",
              "name": "$return"
            }
          ],
          "scriptFile": "../dist/index.js",
          "entryPoint": "boosterServeGraphQL"
        }""");
  }
}
