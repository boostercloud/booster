package io.boostercloud.booster.core;

public final class OptimisticConcurrencyException extends BoosterException {
  public OptimisticConcurrencyException(String message) { super(Code.CONFLICT, message); }
  public OptimisticConcurrencyException(StreamId stream, long expected, long actual) {
    this("Concurrent modification of " + stream + ": expected version " + expected + ", actual " + actual);
  }
}
