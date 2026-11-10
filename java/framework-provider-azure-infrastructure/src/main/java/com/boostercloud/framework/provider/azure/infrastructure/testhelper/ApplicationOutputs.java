package com.boostercloud.framework.provider.azure.infrastructure.testhelper;

/**
 * The endpoints of a deployed application ({@code ApplicationOutputs} of {@code test-helper/azure-test-helper.ts},
 * the same shape as application-tester's {@code ApplicationOutputs}).
 *
 * @param graphqlURL the GraphQL endpoint
 * @param websocketURL the Web PubSub client endpoint of the {@code booster} hub
 * @param healthURL the health endpoint (with a trailing slash)
 */
public record ApplicationOutputs(String graphqlURL, String websocketURL, String healthURL) {}
