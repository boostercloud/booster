package com.boostercloud.framework.types.concepts;

/**
 * Port of {@code EntityInterface}: entity instances have an {@code id}. Implementing it is optional (core reads the
 * {@code id} property by reflection).
 */
public interface EntityInterface {
  /**
   * @return the entity id
   */
  UUID id();
}
