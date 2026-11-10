package com.boostercloud.cli.services.generator.target;

import java.util.List;

/**
 * Generator information with the events an entity reduces ({@code HasReaction}).
 *
 * @param events the events, in the order they were given
 */
public record HasReaction(List<ReactionEvent> events) {
  /** Makes the list immutable. */
  public HasReaction {
    events = List.copyOf(events);
  }
}
