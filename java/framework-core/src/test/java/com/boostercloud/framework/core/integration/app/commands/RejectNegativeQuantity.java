package com.boostercloud.framework.core.integration.app.commands;

import com.boostercloud.framework.types.concepts.CommandBeforeFunction;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.framework.types.errors.InvalidParameterError;
import java.util.LinkedHashMap;
import java.util.Map;

/** Before hook of {@link ChangeCartItem}: rejects negative quantities and rounds the others down to 99. */
public class RejectNegativeQuantity implements CommandBeforeFunction {
  @Override
  public Map<String, Object> apply(Map<String, Object> input, UserEnvelope currentUser) {
    Number quantity = (Number) input.get("quantity");
    if (quantity.doubleValue() < 0) {
      throw new InvalidParameterError("The quantity cannot be negative");
    }
    Map<String, Object> result = new LinkedHashMap<>(input);
    result.put("quantity", Math.min(quantity.intValue(), 99));
    return result;
  }
}
