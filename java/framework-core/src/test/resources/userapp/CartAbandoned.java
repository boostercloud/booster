package com.example.store.events;

import com.boostercloud.framework.core.decorators.Notification;
import com.boostercloud.framework.core.decorators.PartitionKey;
import com.boostercloud.framework.types.concepts.UUID;

@Notification(topic = "cart-abandoned")
public record CartAbandoned(@PartitionKey UUID cartId) {}
