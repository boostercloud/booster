package com.boostercloud.framework.core.decorators;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Port of the {@code @Entity} / {@code @Entity({ authorizeReadEvents })} decorator: registers the class in
 * {@code config.entities} with the authorizer of its event stream (GraphQL {@code eventsByEntity}). Reducers are
 * static methods annotated with {@link Reduces}.
 *
 * <p>Registration error: {@code An entity called <Name> is already registered} + new line + clean-build hint ending
 * in two periods, as in TypeScript.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Entity {
  /**
   * Who may read the event stream; same rules as {@link Command#authorize()}, with
   * {@link com.boostercloud.framework.types.concepts.EventStreamAuthorizer} custom authorizers. Denied by default.
   *
   * @return the authorization
   */
  Class<?>[] authorizeReadEvents() default {};
}
