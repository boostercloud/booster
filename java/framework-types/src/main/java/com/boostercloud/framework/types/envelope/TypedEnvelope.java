package com.boostercloud.framework.types.envelope;

/**
 * An envelope that carries a typed value. Port of {@code TypedEnvelope}.
 */
public interface TypedEnvelope extends Envelope {
  /**
   * @return the class name of the value
   */
  String typeName();

  /**
   * @return the schema version of the value
   */
  int version();
}
