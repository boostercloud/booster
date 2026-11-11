package com.boostercloud.framework.provider.local.library;

import com.boostercloud.framework.types.TypeLevel;
import com.boostercloud.framework.types.errors.BoosterErrors;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the results core returns from {@code boosterServeGraphQL} and {@code boosterHealth}; the local infrastructure
 * turns them into HTTP responses. Port of {@code library/api-adapter.ts}.
 */
public final class ApiAdapter {

  private ApiAdapter() {}

  /**
   * {@code APIResult}: {@code { status: 'success', result, headers }} or
   * {@code { status: 'failure', code, title, reason }}.
   */
  public sealed interface APIResult permits Success, Failure {
    /**
     * @return {@code "success"} or {@code "failure"}
     */
    String status();
  }

  /**
   * A successful result.
   *
   * @param result the body
   * @param headers the response headers (values are numbers, strings or lists of strings); never {@code null}
   */
  @JsonInclude(JsonInclude.Include.NON_ABSENT)
  @JsonPropertyOrder({"status", "result", "headers"})
  public record Success(Object result, Map<String, Object> headers) implements APIResult {
    /** {@code 'success'}. */
    public static final String STATUS = "success";

    @Override
    @JsonProperty("status")
    public String status() {
      return STATUS;
    }
  }

  /**
   * A failed result.
   *
   * @param code the HTTP status code
   * @param title the error class as a title ({@code "Not Authorized Error"})
   * @param reason the error message
   */
  @JsonInclude(JsonInclude.Include.NON_ABSENT)
  @JsonPropertyOrder({"status", "code", "title", "reason"})
  public record Failure(int code, String title, String reason) implements APIResult {
    /** {@code 'failure'}. */
    public static final String STATUS = "failure";

    @Override
    @JsonProperty("status")
    public String status() {
      return STATUS;
    }
  }

  /**
   * {@code requestSucceeded(body?, headers?)}: {@code { status: 'success', result: body, headers: { ...headers } }}.
   *
   * @param body the body, or {@code null}
   * @param headers the headers, or {@code null}
   * @return the result (headers is always an object)
   */
  public static Success requestSucceeded(Object body, Map<String, ?> headers) {
    return new Success(body, headers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(headers));
  }

  /**
   * {@code requestFailed(error)}: status {@code httpStatusCodeFor(error)}, title {@code toClassTitle(error)}, reason
   * {@code error.message}.
   *
   * @param error the error
   * @return the result
   */
  public static Failure requestFailed(Throwable error) {
    return new Failure(BoosterErrors.httpStatusCodeFor(error), TypeLevel.toClassTitle(error), error.getMessage());
  }

  /**
   * {@code healthRequestResult(body, isHealthy)}: a success whose {@code status-code} header is {@code 200} or
   * {@code 503}.
   *
   * @param body the health results
   * @param isHealthy whether every component is up
   * @return the result
   */
  public static Success healthRequestResult(Object body, boolean isHealthy) {
    Map<String, Object> headers = new LinkedHashMap<>();
    headers.put("status-code", isHealthy ? 200 : 503);
    return new Success(body, headers);
  }
}
