package com.boostercloud.framework.core.decorators;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Port of the {@code @CalculatedField({ dependsOn })} decorator: a no-argument instance method of a read model that is
 * exposed as a property (TypeScript: a getter). The metadata module reads it by simple name
 * ({@code MetadataExtractors.CALCULATED_FIELD_ANNOTATION}): the property gets {@code isGetAccessor = true} and
 * {@code dependencies = dependsOn}, which widens {@code select} projections.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CalculatedField {
  /**
   * @return the properties the value is computed from
   */
  String[] dependsOn() default {};
}
