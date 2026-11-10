package com.boostercloud.framework.core.decorators;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Port of the {@code @GlobalErrorHandler()} decorator: the single application-wide error handler
 * ({@code config.globalErrorsHandler}). Its hooks are optional static methods, see
 * {@link com.boostercloud.framework.types.concepts.GlobalErrorHandlerInterface}.
 *
 * <p>Registration error: {@code An error handler called <NewClass> is already registered.} + clean-build hint.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GlobalErrorHandler {}
