package com.boostercloud.integration.queries;

import com.boostercloud.framework.core.decorators.Query;
import com.boostercloud.framework.types.concepts.QueryInfo;
import com.boostercloud.integration.roles.Admin;
import com.boostercloud.integration.roles.UserWithEmail;

/** A query restricted to roles ({@code src/queries/cart-with-role.ts}). */
@Query(authorize = {Admin.class, UserWithEmail.class})
public record CartWithRole() {

  /**
   * @param query the query
   * @param queryInfo the request information
   * @return {@code true}
   */
  public static boolean handle(CartWithRole query, QueryInfo queryInfo) {
    return true;
  }
}
