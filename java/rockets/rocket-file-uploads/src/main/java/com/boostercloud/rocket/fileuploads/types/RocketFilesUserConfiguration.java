package com.boostercloud.rocket.fileuploads.types;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One storage the rocket manages ({@code RocketFilesUserConfiguration} of {@code rocket-files-params.ts}).
 *
 * <p>Azure uses {@code storageName} as the Storage Account name and {@code containerName} as the blob container; the
 * local provider uses them as the folders {@code <project>/<storageName>/<containerName>}. {@code directories} are
 * minimatch glob patterns ({@link Utils#isValidDirectory(String, List)}) naming the directories files may live in.
 * Nothing is validated here, as in TypeScript.
 *
 * @param storageName the storage (account) name; lower-cased by {@code BoosterRocketFiles}
 * @param containerName the container
 * @param directories the allowed directory patterns
 */
public record RocketFilesUserConfiguration(String storageName, String containerName, List<String> directories) {

  /** Canonical constructor: keeps an unmodifiable copy of the directories ({@code null} stays {@code null}). */
  public RocketFilesUserConfiguration {
    directories = directories == null ? null : Collections.unmodifiableList(new ArrayList<>(directories));
  }

  /**
   * @param newStorageName the storage name
   * @return a copy with another storage name
   */
  public RocketFilesUserConfiguration withStorageName(String newStorageName) {
    return new RocketFilesUserConfiguration(newStorageName, containerName, directories);
  }
}
