package com.example.store.common;

import com.boostercloud.framework.types.concepts.ReadModelBeforeFunction;
import com.boostercloud.framework.types.envelope.ReadModelRequestEnvelope;
import com.boostercloud.framework.types.envelope.UserEnvelope;

public class CheckAccess implements ReadModelBeforeFunction {
  @Override
  public ReadModelRequestEnvelope apply(ReadModelRequestEnvelope request, UserEnvelope currentUser) {
    return request;
  }
}
