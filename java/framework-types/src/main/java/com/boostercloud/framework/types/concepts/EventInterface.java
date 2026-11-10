package com.boostercloud.framework.types.concepts;

/**
 * Port of {@code EventInterface}: every event exposes the id of the entity it belongs to. Implementing this interface
 * is optional; core looks the {@code entityID()} instance method up by reflection, so a plain record with that method
 * is an event too.
 */
public interface EventInterface {
  /**
   * @return id of the entity this event belongs to
   */
  UUID entityID();
}
