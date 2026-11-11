package com.boostercloud.framework.provider.local.infrastructure.controllers;

import com.boostercloud.framework.provider.local.library.ApiAdapter;
import java.util.Map;

/**
 * Reads the {@code APIResult} core returns from {@code boosterServeGraphQL}/{@code boosterHealth}: the
 * {@link ApiAdapter} records, or a map with the same fields (TypeScript reads them as plain object properties).
 */
final class ApiResults {

  private ApiResults() {}

  /**
   * Fails like TypeScript reading a property of {@code undefined}.
   *
   * @param response the result
   * @param property the property TypeScript reads first
   */
  static void requirePresent(Object response, String property) {
    if (response == null) {
      throw new IllegalStateException("Cannot read properties of undefined (reading '" + property + "')");
    }
  }

  /**
   * @param response the result
   * @return {@code response.status === 'success'}
   */
  static boolean isSuccess(Object response) {
    if (response instanceof ApiAdapter.Success) {
      return true;
    }
    return response instanceof Map<?, ?> map && ApiAdapter.Success.STATUS.equals(map.get("status"));
  }

  /**
   * @param response the result
   * @return {@code response.result} ({@code null} when absent)
   */
  static Object result(Object response) {
    if (response instanceof ApiAdapter.Success success) {
      return success.result();
    }
    return response instanceof Map<?, ?> map ? map.get("result") : null;
  }

  /**
   * @param response the result
   * @return whether {@code response.result} is present (TypeScript sends nothing for {@code undefined})
   */
  static boolean hasResult(Object response) {
    if (response instanceof ApiAdapter.Success) {
      return true;
    }
    return response instanceof Map<?, ?> map && map.containsKey("result");
  }

  /**
   * @param response a failure result
   * @return {@code response.code}
   */
  static int code(Object response) {
    if (response instanceof ApiAdapter.Failure failure) {
      return failure.code();
    }
    if (response instanceof Map<?, ?> map && map.get("code") instanceof Number number) {
      return number.intValue();
    }
    throw new IllegalArgumentException("Invalid status code: " + (response instanceof Map<?, ?> map
        ? map.get("code") : "undefined"));
  }

  /**
   * @param response a failure result
   * @return {@code response.title}
   */
  static Object title(Object response) {
    if (response instanceof ApiAdapter.Failure failure) {
      return failure.title();
    }
    return response instanceof Map<?, ?> map ? map.get("title") : null;
  }

  /**
   * @param response a failure result
   * @return {@code response.message}: always absent for the provider's failures (they carry {@code reason})
   */
  static Object message(Object response) {
    return response instanceof Map<?, ?> map ? map.get("message") : null;
  }
}
