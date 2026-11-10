package com.boostercloud.framework.types.concepts;

import java.util.List;
import java.util.Objects;

/**
 * The value of an {@code authorize} option, the port of the TypeScript union
 * {@code 'all' | Array<Class<RoleInterface>> | XAuthorizer}. Core resolves it into an authorizer function
 * ({@code BoosterAuthorizer.build}) with the TypeScript rules: {@link All} always allows, {@link Roles} allows a
 * user that has at least one of the roles (matched by simple class name; an empty list always denies),
 * {@link Custom} delegates to the function, and an absent option ({@code null}) denies.
 *
 * @param <A> the authorizer function type ({@link CommandAuthorizer}, {@link QueryAuthorizer}, ...)
 */
public sealed interface Authorize<A> permits Authorize.All, Authorize.Roles, Authorize.Custom {

  /**
   * TypeScript {@code 'all'}.
   *
   * @param <A> the authorizer function type
   */
  record All<A>() implements Authorize<A> {}

  /**
   * TypeScript {@code [RoleA, RoleB]}.
   *
   * @param roles the role classes
   * @param <A> the authorizer function type
   */
  record Roles<A>(List<Class<?>> roles) implements Authorize<A> {
    /** Canonical constructor; copies {@code roles}. */
    public Roles {
      roles = List.copyOf(roles);
    }

    /**
     * @return the simple names of the roles, the values compared against {@code UserEnvelope.roles}
     */
    public List<String> roleNames() {
      return roles.stream().map(Class::getSimpleName).toList();
    }
  }

  /**
   * A custom authorizer function.
   *
   * @param authorizer the function
   * @param <A> the authorizer function type
   */
  record Custom<A>(A authorizer) implements Authorize<A> {
    /** Canonical constructor; rejects {@code null}. */
    public Custom {
      Objects.requireNonNull(authorizer, "authorizer");
    }
  }

  /**
   * @param <A> the authorizer function type
   * @return {@code 'all'}
   */
  static <A> Authorize<A> all() {
    return new All<>();
  }

  /**
   * @param roles the role classes
   * @param <A> the authorizer function type
   * @return the role list option
   */
  static <A> Authorize<A> roles(Class<?>... roles) {
    return new Roles<>(List.of(roles));
  }

  /**
   * @param roles the role classes
   * @param <A> the authorizer function type
   * @return the role list option
   */
  static <A> Authorize<A> roles(List<Class<?>> roles) {
    return new Roles<>(roles);
  }

  /**
   * @param authorizer a custom authorizer
   * @param <A> the authorizer function type
   * @return the custom option
   */
  static <A> Authorize<A> custom(A authorizer) {
    return new Custom<>(authorizer);
  }
}
