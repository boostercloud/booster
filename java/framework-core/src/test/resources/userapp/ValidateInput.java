package com.example.store.common;

import com.boostercloud.framework.types.concepts.CommandBeforeFunction;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import java.util.Map;

public class ValidateInput implements CommandBeforeFunction {
  @Override
  public Map<String, Object> apply(Map<String, Object> input, UserEnvelope currentUser) {
    if (((Number) input.get("quantity")).doubleValue() < 0) {
      throw new IllegalArgumentException("Negative quantity");
    }
    return input;
  }
}
