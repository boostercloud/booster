package com.boostercloud.framework.provider.local.infrastructure.controllers;

import com.boostercloud.framework.provider.local.infrastructure.Http;
import com.boostercloud.framework.provider.local.infrastructure.HttpCodes;
import com.boostercloud.framework.provider.local.infrastructure.web.HttpRequest;
import com.boostercloud.framework.provider.local.infrastructure.web.HttpResponse;
import com.boostercloud.framework.provider.local.infrastructure.web.NextFunction;
import com.boostercloud.framework.provider.local.infrastructure.web.Router;
import com.boostercloud.framework.provider.local.services.HealthService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The health endpoint: {@code GET /*} of a router the server mounts at {@code /sensor/health}. Port of
 * {@code controllers/health-controller.ts}.
 *
 * <p>The request goes to {@link HealthService#handleHealthRequest(Object)} with its URL relative to
 * {@code /sensor/health} (the component path). A success is answered with {@code 200} and the health results, even
 * when a component is down (the {@code status-code} header of the result is not used, as in TypeScript). A failure is
 * answered with its {@code code} and {@code { "title": ... }}: TypeScript reads {@code response.message}, which
 * failures never have, so the {@code reason} is not sent. When the service throws, {@link Http#requestFailed} answers
 * and the error is passed to {@code next}.
 */
public class HealthController {

  private final Router router = new Router();
  private final HealthService healthService;

  /**
   * @param healthService the service that hands requests to the application
   */
  public HealthController(HealthService healthService) {
    this.healthService = Objects.requireNonNull(healthService, "healthService");
    router.get("/*", this::handleHealth);
  }

  /**
   * @return the router with the {@code GET /*} route
   */
  public Router router() {
    return router;
  }

  /**
   * @return the service
   */
  public HealthService healthService() {
    return healthService;
  }

  /**
   * {@code handleHealth(req, res, next)}.
   *
   * @param request the request
   * @param response the response
   * @param next the next function, called only with an error
   */
  public void handleHealth(HttpRequest request, HttpResponse response, NextFunction next) {
    try {
      Object result = healthService.handleHealthRequest(request.toLocalHttpRequest());
      ApiResults.requirePresent(result, "status");
      if (ApiResults.isSuccess(result)) {
        response.status(HttpCodes.OK.code()).json(ApiResults.result(result));
      } else {
        Map<String, Object> body = new LinkedHashMap<>();
        Object title = ApiResults.title(result);
        Object message = ApiResults.message(result);
        if (title != null) {
          body.put("title", title);
        }
        if (message != null) {
          body.put("reason", message);
        }
        response.status(ApiResults.code(result)).json(body);
      }
    } catch (RuntimeException e) {
      Http.requestFailed(e, response);
      next.next(e);
    }
  }
}
