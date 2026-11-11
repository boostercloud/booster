package com.boostercloud.framework.core.decorators;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Port of the {@code @sequencedBy} parameter decorator: marks the sort-key property of a read model
 * ({@code config.readModelSequenceKeys[<ReadModel>]}).
 *
 * <p>Registration error (only one per class): {@code Error trying to register a sort key named `<p>` for class
 * `<C>`. It already had the sort key `<q>` defined and only one sort key is allowed for each read model.}
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.PARAMETER})
public @interface SequencedBy {}
