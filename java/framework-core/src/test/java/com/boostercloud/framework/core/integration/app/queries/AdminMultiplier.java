package com.boostercloud.framework.core.integration.app.queries;

import com.boostercloud.framework.types.concepts.QueryBeforeFunction;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import java.util.LinkedHashMap;
import java.util.Map;

/** Before hook of {@link CartTotalQuantity}: admins see the totals multiplied by 10. */
public class AdminMultiplier implements QueryBeforeFunction {
  @Override
  public Map<String, Object> apply(Map<String, Object> input, UserEnvelope currentUser) {
    Map<String, Object> result = new LinkedHashMap<>(input);
    if (currentUser != null && currentUser.hasRole("Admin")) {
      result.put("multiplier", 10);
    }
    return result;
  }
}
