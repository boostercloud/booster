package com.boostercloud.framework.provider.azure.infrastructure.azure;

/** Obtains OAuth access tokens ({@code TokenCredential} of {@code @azure/identity}). */
@FunctionalInterface
public interface TokenCredential {

  /**
   * @param scope the scope, e.g. {@code https://management.azure.com/.default}
   * @return a valid access token
   * @throws AuthenticationError when the token cannot be obtained
   */
  String getToken(String scope);
}
