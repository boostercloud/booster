package com.boostercloud.framework.core.decorators;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Port of the deprecated {@code @Returns(ReturnClass)} decorator: it does nothing except printing the TypeScript
 * deprecation notice to the standard error when the annotated class is registered. Command handlers may return any
 * type without it.
 *
 * @deprecated [EOL v3] return any type from {@code handle} instead
 */
@Deprecated
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Returns {
  /**
   * @return the returned class (ignored)
   */
  Class<?> value();
}
