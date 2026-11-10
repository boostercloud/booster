package com.boostercloud.framework.provider.local.infrastructure;

/**
 * What the local server tells a rocket when it mounts it. Port of {@code InfrastructureRocketMetadata}
 * ({@code infrastructure-rocket.ts}).
 *
 * @param port the port the HTTP server listens on (the bound port, also when it was started with port 0)
 */
public record InfrastructureRocketMetadata(int port) {}
