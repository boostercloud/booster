package com.boostercloud.framework.types.concepts;

/**
 * Port of {@code DataMigrationInterface}: a class with a <b>static</b>
 * {@code public static void start(Register register)} method. Implementing this marker is optional.
 */
public interface DataMigrationInterface {
  /** Name of the static method core invokes. */
  String START_METHOD = "start";
}
