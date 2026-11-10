package com.boostercloud.framework.types.concepts;

/**
 * Port of {@code ReadModelInterface}: read model instances have an {@code id}. Implementing it is optional. The
 * framework-owned {@code boosterMetadata} is kept in the stored document (see {@link BoosterMetadata}); a read model
 * class may declare a {@code boosterMetadata} property to receive it.
 */
public interface ReadModelInterface {
  /**
   * @return the read model id
   */
  UUID id();

  /**
   * @return the framework metadata when the class exposes it, {@code null} otherwise
   */
  default BoosterMetadata boosterMetadata() {
    return null;
  }
}
