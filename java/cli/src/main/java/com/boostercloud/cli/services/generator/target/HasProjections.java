package com.boostercloud.cli.services.generator.target;

import java.util.List;

/**
 * Generator information with projections ({@code HasProjections}).
 *
 * @param projections the projections, in the order they were given
 */
public record HasProjections(List<Projection> projections) {
  /** Makes the list immutable. */
  public HasProjections {
    projections = List.copyOf(projections);
  }
}
