package com.boostercloud.framework.core.decorators;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The {@code auth} settings of a {@link Role} (TypeScript {@code AuthMetadata}). An empty {@link #signUpMethods()} and
 * {@code skipConfirmation = false} are stored as absent ({@code { auth: {} }}).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface RoleAuth {
  /**
   * @return {@code "email"} and/or {@code "phone"}
   */
  String[] signUpMethods() default {};

  /**
   * @return whether sign-ups are confirmed automatically
   */
  boolean skipConfirmation() default false;
}
