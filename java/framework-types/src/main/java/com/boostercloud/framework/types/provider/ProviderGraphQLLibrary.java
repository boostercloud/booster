package com.boostercloud.framework.types.provider;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.envelope.GraphQLRequestEnvelopeOrError;
import java.util.Map;

/**
 * GraphQL request/response translation of a provider. Port of {@code ProviderGraphQLLibrary}.
 */
public interface ProviderGraphQLLibrary {
  /**
   * @param config the configuration
   * @param rawGraphQLRequest the provider-native request
   * @return the parsed request, or an error envelope when it could not be parsed
   */
  GraphQLRequestEnvelopeOrError rawToEnvelope(BoosterConfig config, Object rawGraphQLRequest);

  /**
   * Builds the provider-native response.
   *
   * @param result the GraphQL result (or {@code null})
   * @param headers response headers, or {@code null} (TypeScript distinguishes {@code handleResult(undefined)} from
   *     {@code handleResult(null, headers)} only through the headers)
   * @return the provider-native response
   */
  Object handleResult(Object result, Map<String, String> headers);

  /**
   * {@code handleResult(result)} without headers.
   *
   * @param result the GraphQL result (or {@code null})
   * @return the provider-native response
   */
  default Object handleResult(Object result) {
    return handleResult(result, null);
  }
}
