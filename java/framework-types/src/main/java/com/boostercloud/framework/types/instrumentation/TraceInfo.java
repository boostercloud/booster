package com.boostercloud.framework.types.instrumentation;

import com.boostercloud.framework.types.concepts.UUID;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Information about a traced invocation, passed to {@code onStart} and {@code onEnd}. Port of {@code TraceInfo}. The
 * same mutable object is passed to both handlers; {@link #elapsedInvocationMillis} is set before {@code onEnd}.
 */
public class TraceInfo {
  /** Simple name of the class declaring the traced method. */
  public String className;

  /** Name of the traced method. */
  public String methodName;

  /** Arguments of the invocation. */
  public List<Object> args;

  /** Id shared by the start and end notifications of one invocation. */
  public UUID traceId;

  /** Duration of the invocation, set for {@link TraceTypes#END}. */
  public Long elapsedInvocationMillis;

  /** Internal details, only when {@code traceConfiguration.includeInternal} is true. */
  public Internal internal;

  /** Optional description given to the trace. */
  public String description;

  /** Creates an empty trace info. */
  public TraceInfo() {}

  /**
   * @param className simple name of the declaring class
   * @param methodName name of the method
   * @param args invocation arguments
   * @param traceId trace id
   * @param description optional description
   */
  public TraceInfo(String className, String methodName, List<Object> args, UUID traceId, String description) {
    this.className = className;
    this.methodName = methodName;
    this.args = args;
    this.traceId = traceId;
    this.description = description;
  }

  /**
   * TypeScript's {@code internal: { target, descriptor }}; the JavaScript property descriptor becomes the reflective
   * {@link Method}.
   *
   * @param target the object (or class, for static methods) the method was invoked on
   * @param method the traced method, when known
   */
  public record Internal(Object target, Method method) {}

  @Override
  public String toString() {
    return "TraceInfo{" + className + "." + methodName + ", traceId=" + traceId
        + (elapsedInvocationMillis == null ? "" : ", elapsed=" + elapsedInvocationMillis + "ms") + "}";
  }
}
