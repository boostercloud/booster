package com.boostercloud.framework.types.concepts;

/**
 * What {@code @Notification} registers in {@code config.notifications[className]}. Port of
 * {@code NotificationMetadata}.
 *
 * @param type the notification class
 */
public record NotificationMetadata(Class<?> type) {}
