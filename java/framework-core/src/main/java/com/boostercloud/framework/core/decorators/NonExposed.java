package com.boostercloud.framework.core.decorators;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Port of the {@code @NonExposed} decorator: hides a property (record component, field, constructor parameter of a
 * property, calculated field) or a method from the GraphQL schema. Appends its name to
 * {@code config.nonExposedGraphQLMetadataKey[<declaring class>]}; works on any scanned class (commands, queries, read
 * models, nested types).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
public @interface NonExposed {}
