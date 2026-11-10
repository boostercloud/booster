package com.boostercloud.framework.types.provider;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.envelope.HealthEnvelope;
import java.util.List;
import java.util.Map;

/**
 * Health sensor support of a provider. Port of {@code ProviderSensorLibrary}.
 */
public interface ProviderSensorLibrary {
  /**
   * @param config the configuration
   * @return free-form details about the events database
   */
  Object databaseEventsHealthDetails(BoosterConfig config);

  /**
   * @param config the configuration
   * @return free-form details about the read models database
   */
  Object databaseReadModelsHealthDetails(BoosterConfig config);

  /**
   * @param config the configuration
   * @return whether the events database is up
   */
  boolean isDatabaseEventUp(BoosterConfig config);

  /**
   * @param config the configuration
   * @return whether the read models database is up
   */
  boolean areDatabaseReadModelsUp(BoosterConfig config);

  /**
   * @param config the configuration
   * @return the database URLs
   */
  List<String> databaseUrls(BoosterConfig config);

  /**
   * @param config the configuration
   * @return whether the GraphQL function answers
   */
  boolean isGraphQLFunctionUp(BoosterConfig config);

  /**
   * @param config the configuration
   * @return the GraphQL endpoint URL
   */
  String graphQLFunctionUrl(BoosterConfig config);

  /**
   * @param rawRequest the provider-native health request
   * @return the envelope
   */
  HealthEnvelope rawRequestToHealthEnvelope(Object rawRequest);

  /**
   * @param config the configuration
   * @return rocket function name to "is up"
   */
  Map<String, Boolean> areRocketFunctionsUp(BoosterConfig config);
}
