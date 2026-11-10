package com.boostercloud.framework.provider.local.infrastructure;

import com.boostercloud.framework.provider.local.infrastructure.web.Router;
import com.boostercloud.framework.types.BoosterConfig;

/**
 * The infrastructure part of a rocket for the local provider. Port of the {@code InfrastructureRocket} interface of
 * {@code infrastructure-rocket.ts}.
 *
 * <p>A rocket package {@code com.acme.rocket.local} contains a public class {@code com.acme.rocket.local.Rocket}
 * implementing {@link com.boostercloud.framework.common.RocketBuilder} (public no-argument constructor) whose
 * {@code build(parameters)} returns an instance of this interface; applications list it in {@code config.rockets} as
 * {@code new RocketDescriptor("com.acme.rocket.local", parameters)}. {@code Infrastructure.infrastructure(rockets)}
 * loads every rocket with {@code RocketLoader} when the infrastructure is created.
 *
 * <p>When the server starts, {@link #mountStack} is called once per rocket, in {@code config.rockets} order, with the
 * router where the framework already mounted {@code /sensor/health} and {@code /graphql}: a rocket adds its routes
 * ({@code router.post("/my-hook", handler)}) or sub-routers ({@code router.use("/files", subRouter)}) and may start
 * background work (file watchers...). Requests reach rocket routes after the framework's JSON / urlencoded body parsers
 * and CORS headers, as in TypeScript. On a graceful shutdown {@link #unmountStack()} is called after the HTTP server
 * stopped, so rockets can stop their background work (TypeScript never calls it locally).
 */
public interface LocalInfrastructureRocket {

  /**
   * {@code mountStack(config, router, infrastructureRocketMetadata?)}.
   *
   * @param config the application configuration
   * @param router the router of the local server
   * @param metadata the server port
   */
  void mountStack(BoosterConfig config, Router router, InfrastructureRocketMetadata metadata);

  /**
   * {@code unmountStack?()}: releases what {@link #mountStack} started. Does nothing by default.
   */
  default void unmountStack() {}
}
