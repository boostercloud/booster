package com.boostercloud.cli.services.generator.target;

import java.util.List;

/**
 * Generator information with fields ({@code HasFields}).
 *
 * @param fields the fields, in the order they were given
 */
public record HasFields(List<Field> fields) {
  /** Makes the list immutable. */
  public HasFields {
    fields = List.copyOf(fields);
  }
}
