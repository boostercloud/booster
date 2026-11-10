package com.boostercloud.rocket.webhook.e2e.app;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Projects;
import com.boostercloud.framework.core.decorators.ReadModel;
import com.boostercloud.framework.types.concepts.ProjectionResult;
import com.boostercloud.framework.types.concepts.UUID;

/** What a front end reads after the webhook was called. */
@ReadModel(authorize = All.class)
public record OrderReadModel(UUID id, String product, int quantity, String status, String source) {

  @Projects(entity = Order.class, joinKey = "id")
  public static ProjectionResult<OrderReadModel> projectOrder(Order order, OrderReadModel current) {
    return ProjectionResult.of(new OrderReadModel(order.id(), order.product(), order.quantity(), order.status(),
        order.source()));
  }
}
