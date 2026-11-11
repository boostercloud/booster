package com.boostercloud.framework.common;

/**
 * The entry point of an infrastructure rocket: Java replacement of the npm package's default export
 * ({@code (params) => InfrastructureRocket}). A rocket implementation package {@code com.acme.rocket.azure} contains a
 * public class {@code com.acme.rocket.azure.Rocket} implementing this interface with a public no-argument constructor.
 */
@FunctionalInterface
public interface RocketBuilder {
  /**
   * @param parameters the {@code parameters} of the rocket descriptor
   * @return the infrastructure rocket (an object with a {@code mountStack} method, provider-specific)
   */
  Object build(Object parameters);
}
