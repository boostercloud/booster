package com.boostercloud.framework.provider.local.store;

import java.nio.file.Path;

/**
 * Thrown when loading a data file in which more than {@code corruptAlertThreshold} of the lines cannot be parsed.
 * The message is NeDB's: {@code 25% of the data file is corrupt, more than given corruptAlertThreshold (10%).
 * Cautiously refusing to start NeDB to prevent dataloss.}
 */
public final class CorruptDataFileException extends NedbError {

  private final transient Path file;
  private final double corruptionRate;
  private final int corruptItems;
  private final int dataLength;

  CorruptDataFileException(Path file, double corruptionRate, int corruptItems, int dataLength, double threshold) {
    super((int) Math.floor(100 * corruptionRate) + "% of the data file is corrupt, more than given corruptAlertThreshold ("
        + (int) Math.floor(100 * threshold) + "%). Cautiously refusing to start NeDB to prevent dataloss.");
    this.file = file;
    this.corruptionRate = corruptionRate;
    this.corruptItems = corruptItems;
    this.dataLength = dataLength;
  }

  /**
   * @return the data file
   */
  public Path file() {
    return file;
  }

  /**
   * @return corrupt lines / non-empty lines
   */
  public double corruptionRate() {
    return corruptionRate;
  }

  /**
   * @return the number of corrupt lines
   */
  public int corruptItems() {
    return corruptItems;
  }

  /**
   * @return the number of non-empty lines
   */
  public int dataLength() {
    return dataLength;
  }
}
