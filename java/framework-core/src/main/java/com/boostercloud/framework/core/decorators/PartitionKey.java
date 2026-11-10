package com.boostercloud.framework.core.decorators;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Port of the {@code @partitionKey} parameter decorator: marks the property of a {@link Notification} whose (string)
 * value is used as the partition key of the stored notification. Put it on a record component, a field or the
 * constructor parameter of a property.
 *
 * <p>Registration error (only one per class): {@code Error trying to register a partition key named `<p>` for class
 * `<C>`. It already had the partition key `<q>` defined and only one partition key is allowed for each notification
 * event.}
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.PARAMETER})
public @interface PartitionKey {}
