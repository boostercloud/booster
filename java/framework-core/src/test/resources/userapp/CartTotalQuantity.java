package com.example.store.queries;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Query;
import com.boostercloud.framework.types.concepts.QueryInfo;
import com.boostercloud.framework.types.concepts.UUID;

@Query(authorize = All.class)
public record CartTotalQuantity(UUID cartId) {
  public static double handle(CartTotalQuantity query, QueryInfo queryInfo) {
    return 3;
  }
}
