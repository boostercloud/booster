package com.example.cartdemo.queries;

import com.boostercloud.framework.core.decorators.Query;
import com.boostercloud.framework.types.concepts.QueryInfo;
import com.boostercloud.framework.types.concepts.UUID;

@Query(authorize = /* Specify authorized roles here. Use All.class to authorize anyone */)
public record CartTotal(
    UUID cartId
) {
  public static String handle(CartTotal query, QueryInfo queryInfo) {
    /* YOUR CODE HERE */
  }
}
