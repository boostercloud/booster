package com.boostercloud.framework.core.decorators;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Port of the {@code @Role(roleMetadata = { auth: {} })} decorator: registers the class in {@code config.roles}
 * (no duplicate check, last registration wins). Users have a role when their token's roles claim contains the class's
 * simple name.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Role {
  /**
   * @return the authentication settings ({@code { auth: {} }} by default)
   */
  RoleAuth auth() default @RoleAuth;
}
