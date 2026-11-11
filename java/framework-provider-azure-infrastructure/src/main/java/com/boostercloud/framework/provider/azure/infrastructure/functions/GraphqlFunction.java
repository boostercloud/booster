package com.boostercloud.framework.provider.azure.infrastructure.functions;

import com.boostercloud.framework.provider.azure.infrastructure.types.Binding;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionConfig;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionDefinition;
import com.boostercloud.framework.types.BoosterConfig;
import java.util.List;

/**
 * Port of {@code functions/graphql-function.ts}: the legacy (Functions v3, Node) definition of the GraphQL HTTP
 * function. Not used by the deploy flow (the Java function apps are described by
 * {@link com.boostercloud.framework.provider.azure.infrastructure.helper.FunctionsCodeGenerator}); kept because its
 * shape is the {@code FunctionDefinition} contract and is pinned by tests.
 */
public final class GraphqlFunction {

  private final BoosterConfig config;

  /**
   * @param config the configuration
   */
  public GraphqlFunction(BoosterConfig config) {
    this.config = config;
  }

  /**
   * @return {@code graphql}: anonymous {@code httpTrigger} {@code rawRequest} (POST) and {@code http} {@code $return},
   *     {@code ../dist/index.js} / {@code boosterServeGraphQL}
   */
  public FunctionDefinition getFunctionDefinition() {
    return new FunctionDefinition("graphql", new FunctionConfig(
        List.of(
            Binding.of(LegacyFunctions.ordered(
                "authLevel", "anonymous", "type", "httpTrigger", "direction", "in", "name", "rawRequest",
                "methods", List.of("post"))),
            Binding.of(LegacyFunctions.ordered("type", "http", "direction", "out", "name", "$return"))),
        config.functionRelativePath,
        LegacyFunctions.entryPoint(config.serveGraphQLHandler)));
  }
}
