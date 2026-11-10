package com.boostercloud.framework.types.concepts;

/**
 * What {@code @GlobalErrorHandler} registers in {@code config.globalErrorsHandler}. Port of
 * {@code GlobalErrorHandlerMetadata}.
 *
 * @param type the handler class
 */
public record GlobalErrorHandlerMetadata(Class<?> type) {}
