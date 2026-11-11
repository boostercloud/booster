package com.boostercloud.framework.types.provider;

import java.util.Map;

/**
 * HTTP response building of a provider. Port of {@code ProviderAPIHandling}.
 */
public interface ProviderAPIHandling {
  /**
   * @param body the response body, or {@code null}
   * @param headers response headers (values may be numbers, strings or lists of strings), or {@code null}
   * @return the provider-native response
   */
  Object requestSucceeded(Object body, Map<String, Object> headers);

  /**
   * @param body the response body, or {@code null}
   * @return the provider-native response
   */
  default Object requestSucceeded(Object body) {
    return requestSucceeded(body, null);
  }

  /**
   * Builds the failure response: status {@code BoosterErrors.httpStatusCodeFor(error)}, title
   * {@code TypeLevel.toClassTitle(error)}, reason {@code error.getMessage()}.
   *
   * @param error the error
   * @return the provider-native response
   */
  Object requestFailed(Throwable error);

  /**
   * @param body the health result tree
   * @param isHealthy whether every component (except UNKNOWN rockets) is UP
   * @return the provider-native response
   */
  Object healthRequestResult(Object body, boolean isHealthy);
}
