package com.boostercloud.framework.provider.azure.cosmos;

import java.util.Map;

/**
 * A failed Cosmos DB request ({@code ErrorResponse} of {@code @azure/cosmos}). {@link #code()} is the HTTP status
 * (the adapters compare it with 409 and 412, like TypeScript compares {@code error.code}); the message is the
 * service's {@code message}.
 */
public class CosmosException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final int code;
  private final Integer substatus;
  private final Long retryAfterInMs;
  private final transient Map<String, String> headers;
  private final String body;

  /**
   * @param message the service message
   * @param code the HTTP status code
   * @param substatus the {@code x-ms-substatus}, or {@code null}
   * @param retryAfterInMs the {@code x-ms-retry-after-ms}, or {@code null}
   * @param headers the response headers
   * @param body the raw response body, or {@code null}
   */
  public CosmosException(
      String message, int code, Integer substatus, Long retryAfterInMs, Map<String, String> headers, String body) {
    super(message);
    this.code = code;
    this.substatus = substatus;
    this.retryAfterInMs = retryAfterInMs;
    this.headers = headers == null ? Map.of() : Map.copyOf(headers);
    this.body = body;
  }

  /**
   * @param message the message
   * @param code the HTTP status code
   * @return an exception without headers (tests and adapters)
   */
  public static CosmosException of(String message, int code) {
    return new CosmosException(message, code, null, null, Map.of(), null);
  }

  /**
   * @return the HTTP status code ({@code error.code})
   */
  public int code() {
    return code;
  }

  /**
   * @return the substatus, or {@code null}
   */
  public Integer substatus() {
    return substatus;
  }

  /**
   * @return the server-provided retry delay, or {@code null}
   */
  public Long retryAfterInMs() {
    return retryAfterInMs;
  }

  /**
   * @return the response headers
   */
  public Map<String, String> headers() {
    return headers;
  }

  /**
   * @return the raw response body, or {@code null}
   */
  public String body() {
    return body;
  }
}
