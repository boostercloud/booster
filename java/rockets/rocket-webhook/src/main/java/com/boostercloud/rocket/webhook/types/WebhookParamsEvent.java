package com.boostercloud.rocket.webhook.types;

import com.boostercloud.framework.types.concepts.Authorize;
import java.util.Arrays;
import java.util.List;

/**
 * Port of {@code WebhookParamsEvent} ({@code webhook-params.ts}): one webhook endpoint of the application.
 *
 * <pre>{@code
 * WebhookParamsEvent.builder()
 *     .route("clients/other")                         // endpoint: /clients/other (local), /api/clients/other (Azure)
 *     .handlerClass(FacebookHandler.class)            // a class with a static handle(WebhookEvent, Register)
 *     .allowedMethods(AllowedHttpMethod.GET, AllowedHttpMethod.POST)
 *     .authorize(Authorize.roles(Admin.class))        // optional: no authorize = public endpoint
 *     .multiPartConfig(MultiPartConfig.of(MultiPartConfig.Limits.fileSize(1_048_576)))
 *     .build();
 * }</pre>
 *
 * @param origin the endpoint name (deprecated: use {@code route}); used only when {@code route} is {@code null}
 * @param route the endpoint path, without leading slash ({@code test}, {@code clients/other}); matched exactly and
 *     case-sensitively against the request path
 * @param handlerClass the class whose static {@code handle(WebhookEvent, Register)} method handles the requests
 *     (TypeScript {@code WebhookHandlerClassInterface}); it may return a {@link WebhookHandlerReturnType} (or a
 *     {@code CompletionStage} of one) or nothing
 * @param multiPartConfig how {@code multipart/form-data} bodies are parsed, or {@code null} for Busboy's defaults
 * @param authorize who may call the endpoint: {@link Authorize#all()}, {@link Authorize#roles(Class[])} (checked
 *     against the roles of the request's token) or {@link Authorize#custom(Object)} with a {@link WebhookAuthorizer};
 *     {@code null} means <b>no check</b> (unlike commands and queries, where a missing {@code authorize} denies)
 * @param allowedMethods the accepted HTTP methods; {@code null} means {@code [POST]}, an empty list rejects every
 *     request
 */
public record WebhookParamsEvent(
    @Deprecated String origin,
    String route,
    Class<?> handlerClass,
    MultiPartConfig multiPartConfig,
    Authorize<WebhookAuthorizer> authorize,
    List<AllowedHttpMethod> allowedMethods) {

  /** Canonical constructor: copies {@code allowedMethods} ({@code null} stays {@code null}). */
  public WebhookParamsEvent {
    allowedMethods = allowedMethods == null ? null : List.copyOf(allowedMethods);
  }

  /**
   * @param route the endpoint
   * @param handlerClass the handler class
   * @return a public webhook accepting {@code POST}
   */
  public static WebhookParamsEvent of(String route, Class<?> handlerClass) {
    return builder().route(route).handlerClass(handlerClass).build();
  }

  /**
   * @return an empty builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * @return a builder with the values of this webhook
   */
  @SuppressWarnings("deprecation")
  public Builder toBuilder() {
    Builder builder = new Builder();
    builder.origin = origin;
    builder.route = route;
    builder.handlerClass = handlerClass;
    builder.multiPartConfig = multiPartConfig;
    builder.authorize = authorize;
    builder.allowedMethods = allowedMethods;
    return builder;
  }

  /** Builds {@link WebhookParamsEvent}s. */
  public static final class Builder {
    private String origin;
    private String route;
    private Class<?> handlerClass;
    private MultiPartConfig multiPartConfig;
    private Authorize<WebhookAuthorizer> authorize;
    private List<AllowedHttpMethod> allowedMethods;

    private Builder() {}

    /**
     * @param value the endpoint name
     * @return this builder
     * @deprecated use {@link #route(String)}
     */
    @Deprecated
    public Builder origin(String value) {
      this.origin = value;
      return this;
    }

    /**
     * @param value the endpoint path, without leading slash
     * @return this builder
     */
    public Builder route(String value) {
      this.route = value;
      return this;
    }

    /**
     * @param value the class with the static {@code handle(WebhookEvent, Register)} method
     * @return this builder
     */
    public Builder handlerClass(Class<?> value) {
      this.handlerClass = value;
      return this;
    }

    /**
     * @param value the multipart configuration
     * @return this builder
     */
    public Builder multiPartConfig(MultiPartConfig value) {
      this.multiPartConfig = value;
      return this;
    }

    /**
     * @param value {@code Authorize.all()}, {@code Authorize.roles(...)}, {@code Authorize.custom(...)} or
     *     {@code null} (no check)
     * @return this builder
     */
    public Builder authorize(Authorize<WebhookAuthorizer> value) {
      this.authorize = value;
      return this;
    }

    /**
     * {@code authorize: authorizerFunction}.
     *
     * @param authorizer the custom authorizer
     * @return this builder
     */
    public Builder authorize(WebhookAuthorizer authorizer) {
      this.authorize = Authorize.custom(authorizer);
      return this;
    }

    /**
     * {@code authorize: 'all'}.
     *
     * @return this builder
     */
    public Builder authorizeAll() {
      this.authorize = Authorize.all();
      return this;
    }

    /**
     * {@code authorize: [RoleA, RoleB]}.
     *
     * @param roles the role classes (matched by simple name)
     * @return this builder
     */
    public Builder authorizeRoles(Class<?>... roles) {
      this.authorize = Authorize.roles(roles);
      return this;
    }

    /**
     * @param methods the accepted methods
     * @return this builder
     */
    public Builder allowedMethods(AllowedHttpMethod... methods) {
      this.allowedMethods = Arrays.asList(methods);
      return this;
    }

    /**
     * @param methods the accepted methods, or {@code null} for the default ({@code [POST]})
     * @return this builder
     */
    public Builder allowedMethods(List<AllowedHttpMethod> methods) {
      this.allowedMethods = methods;
      return this;
    }

    /**
     * @return the webhook
     */
    public WebhookParamsEvent build() {
      return new WebhookParamsEvent(origin, route, handlerClass, multiPartConfig, authorize, allowedMethods);
    }
  }
}
