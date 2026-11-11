package com.boostercloud.framework.types.concepts;

/**
 * What {@code @Event} registers in {@code config.events[className]}. Port of {@code EventMetadata}.
 *
 * @param type the event class
 */
public record EventMetadata(Class<?> type) {}
