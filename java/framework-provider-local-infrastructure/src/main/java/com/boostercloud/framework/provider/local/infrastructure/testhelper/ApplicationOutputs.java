package com.boostercloud.framework.provider.local.infrastructure.testhelper;

/**
 * The endpoints of a running local application. Port of the {@code ApplicationOutputs} interface of
 * {@code local-test-helper.ts} (the same shape as the application tester's {@code ApplicationOutputs}).
 *
 * @param graphqlURL {@code http://localhost:<port>/graphql}
 * @param websocketURL {@code ws://localhost:<websocket port>/websocket}
 * @param healthURL {@code http://localhost:<port>/sensor/health/}
 */
public record ApplicationOutputs(String graphqlURL, String websocketURL, String healthURL) {}
