package com.boostercloud.rocket.webhook.azure.functions;

import com.boostercloud.framework.provider.azure.functions.BoosterAzureFunctions;
import com.boostercloud.framework.provider.azure.infrastructure.helper.FunctionAppLayout;
import com.boostercloud.framework.provider.azure.infrastructure.types.Binding;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionConfig;
import com.boostercloud.framework.provider.azure.infrastructure.types.FunctionDefinition;
import com.boostercloud.framework.types.BoosterConfig;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Port of {@code functions/webhook-function.ts}. TypeScript generates, per webhook, the JavaScript of an
 * {@code app.http(...)} registration of the Node v4 programming model; a Java function app declares functions with
 * {@code function.json} files, so this generates the equivalent definition: same function name, route, methods and
 * authorization level, bound to {@link BoosterAzureFunctions#ROCKET_HTTP_ENTRY_POINT}, which turns the HTTP request
 * into the rocket request {@code { req: { method, url, originalUrl, headers, query, params, rawBody, body } }} exactly
 * like the generated handler and calls {@code boosterRocketDispatcher}.
 *
 * <pre>{@code
 * {
 *   "bindings": [
 *     { "type": "httpTrigger", "direction": "in", "name": "request", "methods": ["POST", "GET"],
 *       "authLevel": "anonymous", "route": "clients/other", "dataType": "binary" },
 *     { "type": "http", "direction": "out", "name": "$return" }
 *   ],
 *   "scriptFile": "../<app>.jar",
 *   "entryPoint": "com.boostercloud.framework.provider.azure.functions.BoosterAzureFunctions.rocketHttp"
 * }
 * }</pre>
 */
public final class WebhookFunction {

  /** The HTTP methods of every webhook function ({@code allowedMethods} is checked by the dispatcher). */
  public static final List<String> METHODS = List.of("POST", "GET");

  /** The authorization level of every webhook function. */
  public static final String AUTH_LEVEL = "anonymous";

  /** The binding name {@code BoosterAzureFunctions.rocketHttp} reads. */
  public static final String REQUEST_BINDING = "request";

  /**
   * The body is bound as bytes, so multipart uploads reach the rocket unchanged (TypeScript reads
   * {@code request.arrayBuffer()}).
   */
  public static final String DATA_TYPE = "binary";

  private static final Pattern NON_NAME_CHARACTERS = Pattern.compile("[^a-zA-Z0-9_]");

  private WebhookFunction() {}

  /**
   * {@code generateFunctionsCode(endpoint)}: the function of one webhook. Its name is the route with every character
   * outside {@code [a-zA-Z0-9_]} replaced by {@code _} ({@code clients/other} → {@code clients_other}; routes that
   * differ only in those characters collide, as in TypeScript); its route is the webhook route verbatim (under the
   * {@code api/} prefix of the default {@code host.json}).
   *
   * @param config the configuration (the application jar name)
   * @param endpoint the webhook route
   * @return the definition
   * @throws IllegalArgumentException {@code Webhook endpoint must be a non-empty string}
   */
  public static FunctionDefinition generateFunctionsCode(BoosterConfig config, String endpoint) {
    if (endpoint == null || endpoint.isEmpty()) {
      throw new IllegalArgumentException("Webhook endpoint must be a non-empty string");
    }
    return new FunctionDefinition(functionName(endpoint), new FunctionConfig(
        List.of(Binding.httpTrigger(REQUEST_BINDING, METHODS, AUTH_LEVEL, endpoint).with("dataType", DATA_TYPE),
            Binding.httpReturn()),
        FunctionAppLayout.scriptFile(config),
        BoosterAzureFunctions.ROCKET_HTTP_ENTRY_POINT));
  }

  /**
   * @param endpoint a webhook route
   * @return the function name ({@code endpoint.replace(/[^a-zA-Z0-9_]/g, '_')})
   */
  public static String functionName(String endpoint) {
    return NON_NAME_CHARACTERS.matcher(endpoint).replaceAll("_");
  }
}
