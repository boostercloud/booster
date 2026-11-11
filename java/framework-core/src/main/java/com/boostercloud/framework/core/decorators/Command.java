package com.boostercloud.framework.core.decorators;

import com.boostercloud.framework.types.concepts.CommandBeforeFunction;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Port of the {@code @Command(attributes)} decorator: registers the class in {@code config.commandHandlers} under its
 * simple name, with its property and method metadata, an authorizer and the {@code before} hooks. The class must
 * declare {@code public static <R> handle(TheCommand command, Register register)} (a {@code void} result is returned
 * to GraphQL as {@code true}).
 *
 * <pre>{@code
 * @Command(authorize = All.class, before = {ValidateInput.class})
 * public record ChangeCartItem(UUID cartId, UUID productId, double quantity) {
 *   public static void handle(ChangeCartItem command, Register register) { ... }
 * }
 * }</pre>
 *
 * <p>Registration errors (thrown by {@code Booster.start}): {@code A command called <Name> is already registered.}
 * followed by a new line and the clean-build hint, as in TypeScript.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Command {
  /**
   * Who may run the command: {@code {}} (the default) denies everybody, {@code All.class} allows everybody, a list of
   * {@link Role} classes allows users with one of those roles, and a single class implementing
   * {@link com.boostercloud.framework.types.concepts.CommandAuthorizer} (with a no-argument constructor) is a custom
   * authorizer.
   *
   * @return the authorization
   */
  Class<?>[] authorize() default {};

  /**
   * Hooks applied in order to the raw input before the command instance is created (each class needs a no-argument
   * constructor).
   *
   * @return the hook classes
   */
  Class<? extends CommandBeforeFunction>[] before() default {};
}
