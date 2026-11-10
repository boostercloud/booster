package com.boostercloud.application.tester.graphql;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The token a client authenticates with: the TypeScript {@code AuthToken = string | (() => string)}.
 *
 * <ul>
 *   <li>{@link Fixed}: a token string. Like TypeScript's {@code if (authToken)}, a {@code null} or empty string means
 *       "no token": no {@code Authorization} header and an empty {@code connection_init} payload.
 *   <li>{@link Dynamic}: a supplier evaluated for <b>every</b> HTTP operation and every websocket (re)connection, so a
 *       test can rotate tokens. A function is always truthy in TypeScript, so the header is always sent:
 *       {@code "Bearer " + supplier.get()}.
 * </ul>
 */
public sealed interface AuthToken {

  /**
   * @return the {@code Authorization} value to send ({@code Bearer <token>}), empty when there is no token
   */
  Optional<String> authorization();

  /**
   * @return no token
   */
  static AuthToken none() {
    return new Fixed(null);
  }

  /**
   * @param token the token, {@code null} or empty for none
   * @return a fixed token
   */
  static AuthToken of(String token) {
    return new Fixed(token);
  }

  /**
   * @param supplier evaluated on every use
   * @return a dynamic token
   */
  static AuthToken of(Supplier<String> supplier) {
    return new Dynamic(Objects.requireNonNull(supplier, "supplier"));
  }

  /**
   * A token string.
   *
   * @param token the token, {@code null} or empty for none
   */
  record Fixed(String token) implements AuthToken {
    @Override
    public Optional<String> authorization() {
      return token == null || token.isEmpty() ? Optional.empty() : Optional.of("Bearer " + token);
    }
  }

  /**
   * A token supplier, evaluated on every use.
   *
   * @param supplier the supplier
   */
  record Dynamic(Supplier<String> supplier) implements AuthToken {
    @Override
    public Optional<String> authorization() {
      return Optional.of("Bearer " + supplier.get());
    }
  }
}
