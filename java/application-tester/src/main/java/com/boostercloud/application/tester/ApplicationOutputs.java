package com.boostercloud.application.tester;

/**
 * The endpoints of a running Booster application, as reported by a provider's test helper. Port of the TypeScript
 * {@code ApplicationOutputs} interface ({@code provider-test-helper.ts}); the component names are the TypeScript
 * property names.
 *
 * <p>Local provider values: {@code http://localhost:3000/graphql}, {@code ws://localhost:65529/websocket} and
 * {@code http://localhost:3000/sensor/health/}.
 *
 * @param graphqlURL HTTP endpoint for GraphQL queries and mutations
 * @param websocketURL WebSocket endpoint for GraphQL subscriptions (sub-protocol {@code graphql-ws})
 * @param healthURL the health sensor endpoint
 */
public record ApplicationOutputs(String graphqlURL, String websocketURL, String healthURL) {}
