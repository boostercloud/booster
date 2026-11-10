package com.boostercloud.integration.notifications;

import com.boostercloud.framework.core.decorators.Notification;
import com.boostercloud.framework.core.decorators.PartitionKey;

/**
 * A cart was abandoned ({@code src/notifications/cart-abandoned.ts}): a notification on the default topic
 * ({@code defaultTopic}), partitioned by {@code something} (the cart id).
 *
 * @param something the cart id
 */
@Notification
public record CartAbandoned(@PartitionKey String something) {}
