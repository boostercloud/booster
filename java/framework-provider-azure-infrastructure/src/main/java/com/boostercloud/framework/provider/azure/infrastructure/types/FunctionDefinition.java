package com.boostercloud.framework.provider.azure.infrastructure.types;

import java.util.Objects;

/**
 * A function of a function app ({@code FunctionDefinition} of {@code types/functionDefinition.ts}): its name (the
 * folder of its {@code function.json}) and configuration. The TypeScript aliases {@code ScheduleFunctionDefinition},
 * {@code GraphQLFunctionDefinition}, {@code HttpFunctionDefinition}, {@code EventHandlerFunctionDefinition},
 * {@code EventStreamProducerHandlerFunctionDefinition}, {@code EventStreamConsumerHandlerFunctionDefinition},
 * {@code SubscriptionsNotifierFunctionDefinition} and {@code SocketsFunctionDefinition} only narrow the binding types
 * and are all this record.
 *
 * @param name the function name
 * @param config the {@code function.json} content
 */
public record FunctionDefinition(String name, FunctionConfig config) {

  /** Canonical constructor. */
  public FunctionDefinition {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(config, "config");
  }
}
