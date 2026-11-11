package com.boostercloud.framework.core.services;

import com.boostercloud.framework.core.BoosterReadModelsReader;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.searcher.Searcher;

/**
 * Port of {@code services/read-model-searcher.ts} ({@code readModelSearcher(config, readModelClass)}): a
 * {@link Searcher} backed by a new {@link BoosterReadModelsReader}. No I/O happens until a terminal method of the
 * searcher runs; it works for any class, registered or not.
 */
public final class ReadModelSearcher {

  private ReadModelSearcher() {}

  /**
   * @param config the configuration
   * @param readModelClass the read model class
   * @param <T> the read model type
   * @return the searcher
   */
  public static <T> Searcher<T> readModelSearcher(BoosterConfig config, Class<T> readModelClass) {
    BoosterReadModelsReader readModelsReader = new BoosterReadModelsReader(config);
    return new Searcher<>(readModelClass, readModelsReader::readModelSearch, readModelsReader::finderByIdFunction);
  }
}
