package com.boostercloud.framework.types.concepts;

/**
 * The sort key of a sequenced read model. Port of {@code SequenceKey}.
 *
 * @param name the sequence-key property name
 * @param value its value
 */
public record SequenceKey(String name, TimeKey value) {
  /**
   * @param name the property name
   * @param value the value text
   * @return the key
   */
  public static SequenceKey of(String name, String value) {
    return new SequenceKey(name, TimeKey.of(value));
  }
}
