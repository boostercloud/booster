package com.boostercloud.rocket.webhook.core;

import com.boostercloud.framework.core.BoosterAuthorizer;
import com.boostercloud.framework.types.concepts.Authorize;
import com.boostercloud.rocket.webhook.types.WebhookAuthorizer;
import java.util.List;

/**
 * Port of {@code webhook-authorizer-builder.ts}: turns the {@code authorize} option of a webhook into an authorizer.
 *
 * <ul>
 *   <li>{@link Authorize#all()} ({@code 'all'}): {@code BoosterAuthorizer.allowAccess};</li>
 *   <li>{@link Authorize#roles(Class[])} ({@code [RoleA, RoleB]}): {@code BoosterAuthorizer.authorizeRoles(roles,
 *       user)}, i.e. the user must have one of the roles (simple class names); the request is ignored;</li>
 *   <li>{@link Authorize#custom(Object)} with a {@link WebhookAuthorizer}: that function;</li>
 *   <li>anything else ({@code null}, a custom value that is not a {@code WebhookAuthorizer}):
 *       {@code BoosterAuthorizer.denyAccess} ({@code NotAuthorizedError: Access denied for this resource}).</li>
 * </ul>
 */
public final class WebhookAuthorizerBuilder {

  /** {@code BoosterAuthorizer.allowAccess}. */
  static final WebhookAuthorizer ALLOW_ACCESS = (currentUser, request) -> BoosterAuthorizer.allowAccess();

  /** {@code BoosterAuthorizer.denyAccess}. */
  static final WebhookAuthorizer DENY_ACCESS = (currentUser, request) -> BoosterAuthorizer.denyAccess();

  private WebhookAuthorizerBuilder() {}

  /**
   * {@code build(authorize)}.
   *
   * @param authorize the option
   * @return the authorizer
   */
  public static WebhookAuthorizer build(Authorize<?> authorize) {
    return switch (authorize) {
      case Authorize.All<?> all -> ALLOW_ACCESS;
      case Authorize.Roles<?> roles -> authorizeRoles(roles.roles());
      case Authorize.Custom<?> custom when custom.authorizer() instanceof WebhookAuthorizer authorizer -> authorizer;
      case null, default -> DENY_ACCESS;
    };
  }

  private static WebhookAuthorizer authorizeRoles(List<Class<?>> roles) {
    return (currentUser, request) -> BoosterAuthorizer.authorizeRoles(roles, currentUser);
  }
}
