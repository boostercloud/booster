package com.boostercloud.application.tester;

import java.util.Objects;

/**
 * Plain HTTP endpoints of the application under test. Port of {@code http-helper.ts}. The health endpoint is
 * called with {@code com.boostercloud.framework.common.HttpService} (TypeScript: {@code request} from
 * {@code framework-common-helpers}).
 */
public final class HttpHelper {

  private final ProviderTestHelper providerTestHelper;

  /**
   * @param providerTestHelper the provider's test helper
   */
  public HttpHelper(ProviderTestHelper providerTestHelper) {
    this.providerTestHelper = Objects.requireNonNull(providerTestHelper, "providerTestHelper");
  }

  /**
   * @return {@code outputs().healthURL()} of the provider's test helper, read on every call
   */
  public String getHealthUrl() {
    return providerTestHelper.outputs().healthURL();
  }
}
