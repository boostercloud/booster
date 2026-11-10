package com.boostercloud.framework.core.services.pubsub;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.envelope.ReadModelRequestEnvelope;
import java.util.Iterator;
import java.util.List;

/**
 * Port of {@code FilteredReadModelPubSub}: an in-memory pub-sub over the changed read models, yielding those whose
 * class and filters match the subscription (including the in-memory filter evaluator).
 *
 * <p>Skeleton: implemented by stage <b>core-2b</b> (spec 07 §3.6.1-3.6.3).
 */
public class FilteredReadModelPubSub implements ReadModelPubSub {

  private final List<Object> readModels;

  /**
   * @param readModels the changed read model instances
   */
  public FilteredReadModelPubSub(List<Object> readModels) {
    this.readModels = List.copyOf(readModels);
  }

  /**
   * @return the changed read models
   */
  public List<Object> readModels() {
    return readModels;
  }

  @Override
  public Iterator<Object> asyncIterator(ReadModelRequestEnvelope readModelRequestEnvelope, BoosterConfig config) {
    throw new UnsupportedOperationException("not implemented yet: FilteredReadModelPubSub#asyncIterator");
  }
}
