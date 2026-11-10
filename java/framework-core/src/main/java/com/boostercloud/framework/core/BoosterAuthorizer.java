package com.boostercloud.framework.core;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.internal.UserCode;
import com.boostercloud.framework.types.concepts.Authorize;
import com.boostercloud.framework.types.concepts.CommandAuthorizer;
import com.boostercloud.framework.types.concepts.CommandRoleAccess;
import com.boostercloud.framework.types.concepts.EventStreamAuthorizer;
import com.boostercloud.framework.types.concepts.EventStreamRoleAccess;
import com.boostercloud.framework.types.concepts.HealthAuthorizer;
import com.boostercloud.framework.types.concepts.HealthRoleAccess;
import com.boostercloud.framework.types.concepts.QueryAuthorizer;
import com.boostercloud.framework.types.concepts.QueryRoleAccess;
import com.boostercloud.framework.types.concepts.ReadModelAuthorizer;
import com.boostercloud.framework.types.concepts.ReadModelRoleAccess;
import com.boostercloud.framework.types.envelope.CommandEnvelope;
import com.boostercloud.framework.types.envelope.EventSearchRequest;
import com.boostercloud.framework.types.envelope.HealthEnvelope;
import com.boostercloud.framework.types.envelope.QueryEnvelope;
import com.boostercloud.framework.types.envelope.ReadModelRequestEnvelope;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.framework.types.errors.NotAuthorizedError;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Port of {@code booster-authorizer.ts}: the built-in authorizers and the factory that turns an {@code authorize}
 * attribute into an authorizer.
 *
 * <p>Authorizers return normally to allow and throw to deny (TypeScript resolves/rejects a promise). The built-ins
 * implement every authorizer interface ({@link CommandAuthorizer}, {@link QueryAuthorizer},
 * {@link ReadModelAuthorizer}, {@link HealthAuthorizer}, {@link EventStreamAuthorizer}) so that, as in TypeScript, the
 * same objects are shared: {@link #ALLOW_ACCESS} and {@link #DENY_ACCESS} can be compared by identity.
 */
public final class BoosterAuthorizer {

  /** Message of the {@link NotAuthorizedError} thrown when access is denied. */
  public static final String ACCESS_DENIED_MESSAGE = "Access denied for this resource";

  /** The authorizer of {@code authorize: 'all'} (TypeScript {@code BoosterAuthorizer.allowAccess}). */
  public static final BuiltInAuthorizer ALLOW_ACCESS = new BuiltInAuthorizer(true);

  /** The default authorizer, which denies everybody (TypeScript {@code BoosterAuthorizer.denyAccess}). */
  public static final BuiltInAuthorizer DENY_ACCESS = new BuiltInAuthorizer(false);

  private BoosterAuthorizer() {}

  /** {@code allowAccess()}: returns normally. */
  public static void allowAccess() {
    // Access granted: nothing to do.
  }

  /**
   * {@code denyAccess()}.
   *
   * @throws NotAuthorizedError always, with {@value #ACCESS_DENIED_MESSAGE}
   */
  public static void denyAccess() {
    throw new NotAuthorizedError(ACCESS_DENIED_MESSAGE);
  }

  /**
   * {@code authorizeRoles(authorizedRoles, user)}: allows when the user has at least one of the roles (matched by the
   * role classes' simple names, case-sensitive); denies anonymous users, users without roles and an empty role list.
   *
   * @param authorizedRoles the role classes
   * @param user the current user, or {@code null}
   * @throws NotAuthorizedError when access is denied
   */
  public static void authorizeRoles(List<Class<?>> authorizedRoles, UserEnvelope user) {
    if (user != null && userHasSomeRole(user, authorizedRoles)) {
      allowAccess();
      return;
    }
    denyAccess();
  }

  /**
   * {@code authorizeRoles.bind(null, roles)}: an authorizer that checks the given roles.
   *
   * @param authorizedRoles the role classes
   * @return the authorizer
   */
  public static RolesAuthorizer authorizeRoles(List<Class<?>> authorizedRoles) {
    return new RolesAuthorizer(authorizedRoles);
  }

  /**
   * {@code build(attributes)}: {@link Authorize.All} gives {@link #ALLOW_ACCESS}, {@link Authorize.Roles} a new
   * {@link RolesAuthorizer}, {@link Authorize.Custom} the custom authorizer itself, and {@code null} (the attribute
   * is missing) {@link #DENY_ACCESS}.
   *
   * @param authorize the attribute value, or {@code null}
   * @param authorizerType the authorizer interface
   * @param <A> the authorizer interface
   * @return the authorizer
   */
  public static <A> A build(Authorize<A> authorize, Class<A> authorizerType) {
    return switch (authorize) {
      case null -> authorizerType.cast(DENY_ACCESS);
      case Authorize.All<A> ignored -> authorizerType.cast(ALLOW_ACCESS);
      case Authorize.Roles<A> roles -> authorizerType.cast(new RolesAuthorizer(roles.roles()));
      case Authorize.Custom<A> custom -> custom.authorizer();
    };
  }

  /**
   * @param attributes the command attributes, or {@code null}
   * @return the command authorizer
   */
  public static CommandAuthorizer build(CommandRoleAccess attributes) {
    return build(attributes == null ? null : attributes.authorize(), CommandAuthorizer.class);
  }

  /**
   * @param attributes the query attributes, or {@code null}
   * @return the query authorizer
   */
  public static QueryAuthorizer build(QueryRoleAccess attributes) {
    return build(attributes == null ? null : attributes.authorize(), QueryAuthorizer.class);
  }

  /**
   * @param attributes the read model attributes, or {@code null}
   * @return the read model authorizer
   */
  public static ReadModelAuthorizer build(ReadModelRoleAccess attributes) {
    return build(attributes == null ? null : attributes.authorize(), ReadModelAuthorizer.class);
  }

  /**
   * @param attributes the health attributes, or {@code null}
   * @return the health authorizer
   */
  public static HealthAuthorizer build(HealthRoleAccess attributes) {
    return build(attributes == null ? null : attributes.authorize(), HealthAuthorizer.class);
  }

  /**
   * The inline equivalent in {@code @Entity}: the event stream authorizer of {@code authorizeReadEvents}.
   *
   * @param attributes the entity attributes, or {@code null}
   * @return the event stream authorizer
   */
  public static EventStreamAuthorizer build(EventStreamRoleAccess attributes) {
    return build(attributes == null ? null : attributes.authorizeReadEvents(), EventStreamAuthorizer.class);
  }

  /**
   * Converts the {@code authorize} member of an annotation ({@code Class<?>[]}) into an {@link Authorize}:
   * {@code {}} is {@code null} (deny), {@code {All.class}} is {@link Authorize.All}, a single class implementing the
   * authorizer interface is {@link Authorize.Custom} (instantiated with its no-argument constructor), and any other
   * list is {@link Authorize.Roles}.
   *
   * @param classes the annotation value
   * @param authorizerType the authorizer interface
   * @param <A> the authorizer interface
   * @return the attribute, or {@code null}
   * @throws IllegalArgumentException when {@code All} or an authorizer class is combined with other classes
   */
  public static <A> Authorize<A> fromAnnotation(Class<?>[] classes, Class<A> authorizerType) {
    if (classes == null || classes.length == 0) {
      return null;
    }
    if (classes.length == 1 && classes[0] == All.class) {
      return Authorize.all();
    }
    if (classes.length == 1 && authorizerType.isAssignableFrom(classes[0])) {
      return Authorize.custom(authorizerType.cast(UserCode.newInstance(classes[0])));
    }
    for (Class<?> type : classes) {
      if (type == All.class || authorizerType.isAssignableFrom(type)) {
        throw new IllegalArgumentException("Invalid authorize value " + Arrays.toString(classes) + ": "
            + type.getSimpleName() + " must be the only class when it is All or a "
            + authorizerType.getSimpleName());
      }
    }
    return Authorize.roles(List.of(classes));
  }

  private static boolean userHasSomeRole(UserEnvelope user, List<Class<?>> authorizedRoles) {
    List<String> roles = user.roles();
    return roles != null && authorizedRoles.stream().anyMatch(roleClass -> roles.contains(roleClass.getSimpleName()));
  }

  /** {@link #ALLOW_ACCESS} and {@link #DENY_ACCESS}: implement every authorizer interface. */
  public static final class BuiltInAuthorizer
      implements CommandAuthorizer, QueryAuthorizer, ReadModelAuthorizer, HealthAuthorizer, EventStreamAuthorizer {
    private final boolean allow;

    private BuiltInAuthorizer(boolean allow) {
      this.allow = allow;
    }

    private void check() {
      if (allow) {
        allowAccess();
      } else {
        denyAccess();
      }
    }

    @Override
    public void authorize(UserEnvelope currentUser, CommandEnvelope commandEnvelope) {
      check();
    }

    @Override
    public void authorize(UserEnvelope currentUser, QueryEnvelope queryEnvelope) {
      check();
    }

    @Override
    public void authorize(UserEnvelope currentUser, ReadModelRequestEnvelope readModelRequestEnvelope) {
      check();
    }

    @Override
    public void authorize(UserEnvelope currentUser, HealthEnvelope healthEnvelope) {
      check();
    }

    @Override
    public void authorize(UserEnvelope currentUser, EventSearchRequest eventSearchRequest) {
      check();
    }

    @Override
    public String toString() {
      return allow ? "BoosterAuthorizer.allowAccess" : "BoosterAuthorizer.denyAccess";
    }
  }

  /**
   * {@code authorizeRoles.bind(null, roles)}: ignores the envelope and checks the user's roles.
   *
   * @param roles the authorized role classes
   */
  public record RolesAuthorizer(List<Class<?>> roles)
      implements CommandAuthorizer, QueryAuthorizer, ReadModelAuthorizer, HealthAuthorizer, EventStreamAuthorizer {

    /**
     * @param roles the authorized role classes
     */
    public RolesAuthorizer {
      roles = List.copyOf(Objects.requireNonNull(roles, "roles"));
    }

    @Override
    public void authorize(UserEnvelope currentUser, CommandEnvelope commandEnvelope) {
      authorizeRoles(roles, currentUser);
    }

    @Override
    public void authorize(UserEnvelope currentUser, QueryEnvelope queryEnvelope) {
      authorizeRoles(roles, currentUser);
    }

    @Override
    public void authorize(UserEnvelope currentUser, ReadModelRequestEnvelope readModelRequestEnvelope) {
      authorizeRoles(roles, currentUser);
    }

    @Override
    public void authorize(UserEnvelope currentUser, HealthEnvelope healthEnvelope) {
      authorizeRoles(roles, currentUser);
    }

    @Override
    public void authorize(UserEnvelope currentUser, EventSearchRequest eventSearchRequest) {
      authorizeRoles(roles, currentUser);
    }
  }
}
