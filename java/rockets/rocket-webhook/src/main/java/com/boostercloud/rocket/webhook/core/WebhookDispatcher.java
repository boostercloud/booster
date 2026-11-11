package com.boostercloud.rocket.webhook.core;

import com.boostercloud.framework.common.Loggers;
import com.boostercloud.framework.core.RegisterHandler;
import com.boostercloud.framework.core.internal.UserCode;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.rocket.webhook.core.response.WebhookResponse;
import com.boostercloud.rocket.webhook.types.AllowedHttpMethod;
import com.boostercloud.rocket.webhook.types.Helper;
import com.boostercloud.rocket.webhook.types.MultiPartConfig;
import com.boostercloud.rocket.webhook.types.WebhookAPIResult;
import com.boostercloud.rocket.webhook.types.WebhookEvent;
import com.boostercloud.rocket.webhook.types.WebhookHandlerClassInterface;
import com.boostercloud.rocket.webhook.types.WebhookHttpRequest;
import com.boostercloud.rocket.webhook.types.WebhookMultipartForm;
import com.boostercloud.rocket.webhook.types.WebhookParams;
import com.boostercloud.rocket.webhook.types.WebhookParamsEvent;
import com.boostercloud.rocket.webhook.types.WebhookRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Port of {@code webhook-dispatcher.ts}: the rocket function registered by {@link BoosterWebhook}. For one request,
 * strictly in this order:
 *
 * <ol>
 *   <li>logs {@code Webhook: request received} and generates the request id;</li>
 *   <li>finds the webhook whose route equals the request path without {@code /api/} or {@code /}
 *       ({@link Helper#getRouteFromRequest}; exact, case-sensitive, first match): otherwise
 *       {@code Could not find route <path> in parameters.} (500);</li>
 *   <li>checks the method against {@code allowedMethods} ({@code [POST]} when absent): otherwise
 *       {@code Method <METHOD> not allowed} (500);</li>
 *   <li>verifies the {@code authorization} token when there is one ({@link WebhookTokenVerifier}, 401 on failure)
 *       and runs the {@code authorize} option ({@link WebhookAuthorizationChecker}; none = public);</li>
 *   <li>builds the {@link WebhookEvent} (parsing {@code multipart/form-data} bodies);</li>
 *   <li>calls the handler class's static {@code handle(event, register)};</li>
 *   <li>stores the registered events ({@code RegisterHandler.handle}): only after the handler returned;</li>
 *   <li>answers {@code 200} with the handler result ({@link WebhookResponse#responseSuccess}).</li>
 * </ol>
 *
 * <p>Every failure becomes a {@code WebhookAPIErrorResult} ({@link WebhookResponse#responseFailure}): this method
 * never throws.
 */
public final class WebhookDispatcher {

  private static final Pattern MULTIPART = Pattern.compile("multipart/form-data", Pattern.CASE_INSENSITIVE);

  private WebhookDispatcher() {}

  /**
   * {@code dispatch(config, request, params)}.
   *
   * @param config the configuration (the one {@code boosterRocketDispatcher} passes)
   * @param request the raw rocket request ({@code { req, ... }}, see {@link WebhookRequest#from(Object)})
   * @param params the webhooks of the application
   * @return the {@link WebhookAPIResult}
   */
  public static WebhookAPIResult dispatch(BoosterConfig config, Object request, List<WebhookParamsEvent> params) {
    Logger logger = Loggers.getLogger(config, "WebhookDispatcher#dispatch");
    try {
      logger.info("Webhook: request received");
      UUID requestId = UUID.generate();
      WebhookRequest webhookRequest = WebhookRequest.from(request);
      WebhookParamsEvent webhookParamsEvent = getWebhookParamsEvent(params, webhookRequest);

      // Default allowedMethods to [POST] if not defined
      List<AllowedHttpMethod> allowedMethods = webhookParamsEvent.allowedMethods() != null
          ? webhookParamsEvent.allowedMethods()
          : List.of(AllowedHttpMethod.POST);

      // Check if the request method is allowed
      String method = webhookRequest.req().method();
      if (allowedMethods.stream().noneMatch(allowed -> allowed != null && allowed.value().equals(method))) {
        return WebhookResponse.responseFailure(new IllegalStateException("Method " + Js.string(method) + " not allowed"));
      }

      Class<?> handlerClass = webhookParamsEvent.handlerClass();

      UserEnvelope userEnvelope = WebhookTokenVerifier.verify(config, webhookRequest);
      WebhookAuthorizationChecker.authorize(config, webhookParamsEvent.authorize(), userEnvelope, webhookRequest);

      WebhookEvent webhookEvent = toWebhookEvent(webhookParamsEvent, webhookRequest);
      Register register = new Register(requestId, new LinkedHashMap<>(), RegisterHandler::flush, userEnvelope);
      Object result = handle(handlerClass, webhookEvent, register);
      RegisterHandler.handle(config, register);
      Logger responseLogger = Loggers.getLogger(config, "WebhookResponseBuilder");
      if (Js.isTruthy(result)) {
        return WebhookResponse.responseSuccess(result, responseLogger);
      }
      return WebhookResponse.responseSuccess(null, responseLogger);
    } catch (Exception e) {
      return WebhookResponse.responseFailure(e);
    }
  }

  private static Object handle(Class<?> handlerClass, WebhookEvent webhookEvent, Register register) {
    if (handlerClass == null) {
      throw new IllegalArgumentException("Cannot read properties of undefined (reading '"
          + WebhookHandlerClassInterface.HANDLE_METHOD + "')");
    }
    return UserCode.invokeStatic(handlerClass, WebhookHandlerClassInterface.HANDLE_METHOD, webhookEvent, register);
  }

  /**
   * {@code toWebhookEvent(webhookParamsEvent, request)}.
   *
   * @param webhookParamsEvent the webhook
   * @param request the request
   * @return what the handler receives ({@code method} is always {@code POST}, as in TypeScript)
   */
  @SuppressWarnings("deprecation")
  static WebhookEvent toWebhookEvent(WebhookParamsEvent webhookParamsEvent, WebhookRequest request) {
    WebhookHttpRequest req = request.req();
    WebhookMultipartForm multiPart = resolveMultipart(req, webhookParamsEvent.multiPartConfig());
    return new WebhookEvent(
        webhookParamsEvent.origin(),
        webhookParamsEvent.route() != null ? webhookParamsEvent.route() : webhookParamsEvent.origin(),
        "POST",
        req.url(),
        req.originalUrl(),
        req.headers(),
        req.query(),
        req.params(),
        req.rawBody(),
        req.body(),
        multiPart);
  }

  /**
   * {@code getWebhookParamsEvent(params, request)}.
   *
   * @param params the webhooks
   * @param request the request
   * @return the first webhook whose route is the request's
   * @throws IllegalStateException {@code Could not find route <route> in parameters.}, or the
   *     {@code Undefined route parameter} error of a webhook without route met before the match
   */
  static WebhookParamsEvent getWebhookParamsEvent(List<WebhookParamsEvent> params, WebhookRequest request) {
    String requestRoute = Helper.getRouteFromRequest(request);
    for (WebhookParamsEvent param : params) {
      if (WebhookParams.getRoute(param).equals(requestRoute)) {
        return param;
      }
    }
    throw new IllegalStateException("Could not find route " + requestRoute + " in parameters.");
  }

  private static WebhookMultipartForm resolveMultipart(WebhookHttpRequest req, MultiPartConfig config) {
    String contype = req.headers().get("content-type");
    if (!Js.isTruthy(contype) || !hasMultiPart(contype)) {
      return null;
    }
    return ParseMultiPart.parseMultipartFormData(req, config);
  }

  private static boolean hasMultiPart(String contype) {
    return MULTIPART.matcher(contype).find();
  }
}
