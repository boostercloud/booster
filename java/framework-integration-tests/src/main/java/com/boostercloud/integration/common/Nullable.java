package com.boostercloud.integration.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an optional property: the Java spelling of TypeScript's {@code ?} token ({@code readonly packID?: UUID}).
 * Booster honours any annotation with this simple name; like {@code ?}, it also makes the type arguments nullable
 * ({@code @Nullable List<String>} is the GraphQL {@code [String]}).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
public @interface Nullable {}
