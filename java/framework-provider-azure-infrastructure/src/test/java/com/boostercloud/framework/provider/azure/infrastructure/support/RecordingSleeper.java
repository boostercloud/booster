package com.boostercloud.framework.provider.azure.infrastructure.support;

import com.boostercloud.framework.common.Retrier;
import java.util.ArrayList;
import java.util.List;

/** Records the waits instead of sleeping. */
public final class RecordingSleeper implements Retrier.Sleeper {

  private final List<Long> sleeps = new ArrayList<>();

  @Override
  public synchronized void sleep(long millis) {
    sleeps.add(millis);
  }

  /**
   * @return the recorded waits
   */
  public synchronized List<Long> sleeps() {
    return List.copyOf(sleeps);
  }
}
