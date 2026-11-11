package com.boostercloud.framework.types.provider;

/**
 * Everything a provider implements and core calls through {@code config.provider()}. Port of {@code ProviderLibrary}.
 *
 * <p>All TypeScript {@code Promise}-returning methods are blocking here (they run on virtual threads); failures are
 * unchecked exceptions. Parameter order is kept exactly as in TypeScript (note that {@code events.store},
 * {@code storeSnapshot}, {@code storeDispatched} and {@code produce} take {@code config} <em>last</em>).
 *
 * <p>A provider is a class {@code <providerPackage>.Provider} with a
 * {@code public static ProviderLibrary provider(List<RocketDescriptor> rockets)} method (see {@link ProviderLoader}).
 */
public interface ProviderLibrary {
  /**
   * @return the event store
   */
  ProviderEventsLibrary events();

  /**
   * @return the read model store and subscriptions
   */
  ProviderReadModelsLibrary readModels();

  /**
   * @return GraphQL request/response translation
   */
  ProviderGraphQLLibrary graphQL();

  /**
   * @return HTTP response building
   */
  ProviderAPIHandling api();

  /**
   * @return websocket connection storage and messaging
   */
  ProviderConnectionsLibrary connections();

  /**
   * @return scheduled command triggers
   */
  ScheduledCommandsLibrary scheduled();

  /**
   * TypeScript {@code infrastructure: () => ProviderInfrastructure}: loads the (deploy-time) infrastructure lazily.
   * Typically implemented with {@link ProviderLoader#loadInfrastructure(String, java.util.List)}.
   *
   * @return the infrastructure
   */
  ProviderInfrastructure infrastructure();

  /**
   * @return rocket request translation
   */
  ProviderRocketLibrary rockets();

  /**
   * @return health sensor support
   */
  ProviderSensorLibrary sensor();
}
