package com.boostercloud.framework.types.concepts;

/**
 * Port of {@code NotificationInterface}: a free-form event that does not belong to an entity. TypeScript lists the
 * optional well-known keys {@code partitionId} and {@code topic}; notifications are recognised by their class
 * registration ({@code config.notifications}), not by their shape, so implementing this marker is optional.
 */
public interface NotificationInterface {}
