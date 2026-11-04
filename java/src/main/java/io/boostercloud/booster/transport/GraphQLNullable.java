package io.boostercloud.booster.transport;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks an optional record field in the generated GraphQL contract; fields otherwise default to required. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
public @interface GraphQLNullable {}
