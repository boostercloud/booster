package com.boostercloud.framework.core.integration.app.notifications;

import com.boostercloud.framework.core.decorators.Notification;
import com.boostercloud.framework.core.decorators.PartitionKey;
import com.boostercloud.framework.types.concepts.UUID;

/**
 * A cart was abandoned (a notification: it belongs to no entity).
 *
 * @param cartId the cart
 */
@Notification(topic = "cart-abandoned")
public record CartAbandoned(@PartitionKey UUID cartId) {}
