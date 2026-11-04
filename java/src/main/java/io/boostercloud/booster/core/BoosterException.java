package io.boostercloud.booster.core;

public class BoosterException extends RuntimeException {
  public enum Code { VALIDATION, FORBIDDEN, NOT_FOUND, CONFLICT, INTERNAL }
  private final Code code;
  public BoosterException(Code code, String message) { super(message); this.code = code; }
  public BoosterException(Code code, String message, Throwable cause) { super(message, cause); this.code = code; }
  public Code code() { return code; }
}
