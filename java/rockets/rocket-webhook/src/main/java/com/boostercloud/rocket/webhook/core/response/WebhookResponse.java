package com.boostercloud.rocket.webhook.core.response;

import com.boostercloud.framework.types.ConsoleLogger;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.TypeLevel;
import com.boostercloud.framework.types.errors.BoosterErrors;
import com.boostercloud.rocket.webhook.types.WebhookAPIErrorResult;
import com.boostercloud.rocket.webhook.types.WebhookAPIResult;
import com.boostercloud.rocket.webhook.types.WebhookAPISuccessResult;
import com.boostercloud.rocket.webhook.types.WebhookResponseType;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Port of {@code webhook-response.ts}: the results the rocket function returns to the transports.
 */
public final class WebhookResponse {

  private WebhookResponse() {}

  /**
   * {@code responseSuccess()}: the answer of a handler that returned nothing ({@code 200}, empty {@code text/plain}).
   *
   * @return the result
   */
  public static WebhookAPISuccessResult responseSuccess() {
    return responseSuccess(null);
  }

  /**
   * {@code responseSuccess(response)}.
   *
   * @param response the handler result (see {@link WebhookResponseBuilder})
   * @return {@code { headers, status: 200, body }}
   */
  public static WebhookAPISuccessResult responseSuccess(Object response) {
    return responseSuccess(response, ConsoleLogger.INSTANCE);
  }

  /**
   * {@code responseSuccess(response)} with the logger of the {@code Unexpected response type} line.
   *
   * @param response the handler result
   * @param logger the logger
   * @return {@code { headers, status: 200, body }}
   * @throws IllegalArgumentException when a file result has no {@code file}
   */
  public static WebhookAPISuccessResult responseSuccess(Object response, Logger logger) {
    WebhookResponseBuilder webhookResponseBuilder = new WebhookResponseBuilder(response, logger);
    Object body = webhookResponseBuilder.getBody();
    Map<String, Object> headers = webhookResponseBuilder.getHeaders();
    return new WebhookAPISuccessResult(headers, WebhookAPIResult.HTTP_SUCCESS_STATUS_CODE, body);
  }

  /**
   * {@code responseFailure(error)}: status {@code httpStatusCodeFor(error)} (400 {@code InvalidParameterError} /
   * {@code InvalidProtocolError}, 401 {@code NotAuthorizedError} / {@code BoosterTokenExpiredError} /
   * {@code BoosterTokenNotBeforeError}, 404 {@code NotFoundError}, 422 {@code InvalidVersionError}, else 500), body
   * {@code { title: toClassTitle(error), reason: error.message }} and the default headers plus
   * {@code Content-Type: application/json}.
   *
   * @param error the error
   * @return the result
   */
  public static WebhookAPIErrorResult responseFailure(Throwable error) {
    int statusCode = BoosterErrors.httpStatusCodeFor(error);
    WebhookAPIErrorResult.Body body = new WebhookAPIErrorResult.Body(TypeLevel.toClassTitle(error),
        error.getMessage() == null ? "" : error.getMessage());
    Map<String, Object> headers = new LinkedHashMap<>(WebhookResponseBuilder.DEFAULT_HEADERS);
    headers.put("Content-Type", WebhookResponseType.JSON.value());
    return new WebhookAPIErrorResult(statusCode, body, headers);
  }
}
