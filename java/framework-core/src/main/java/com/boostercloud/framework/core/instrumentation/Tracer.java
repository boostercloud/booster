package com.boostercloud.framework.core.instrumentation;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.decorators.Trace;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Dates;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.instrumentation.TraceInfo;
import com.boostercloud.framework.types.instrumentation.TraceTypes;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * The behaviour of the TypeScript {@code @Trace(actionType, description)} method decorator
 * ({@code instrumentation/decorator/trace.ts}). Java cannot replace a method at class-definition time, so the
 * framework wraps its own trace points explicitly with {@link #trace(String, Class, String, List, Supplier)} and wraps
 * user methods annotated with {@link Trace} when it calls them ({@link #traceMethod}).
 *
 * <p>Semantics (identical to TypeScript): the <b>global</b> {@link Booster#findConfig() Booster configuration} is
 * read on every call; when tracing is disabled for the action the body simply runs. Otherwise one {@link TraceInfo}
 * is built ({@code className}, {@code methodName}, {@code args}, {@code description}, a fresh {@code traceId}, and
 * {@code internal} only when {@code includeInternal} is set), the start time is taken, {@code onStart} runs (when it
 * throws, the body and {@code onEnd} do not run), then the body, and finally — also when the body throws —
 * {@code elapsedInvocationMillis} is set on the same object and {@code onEnd} runs; an exception from
 * {@code onEnd} replaces the outcome of the body.
 */
public final class Tracer {

  private Tracer() {}

  /**
   * Traces a framework method (the TypeScript {@code @Trace} on a framework class).
   *
   * @param actionType the action, e.g. {@code COMMAND_HANDLER}
   * @param declaringClass the class whose method is traced ({@code TraceInfo.className} is its simple name)
   * @param methodName the traced method
   * @param args the method arguments (live list, may contain {@code null})
   * @param body the method body
   * @param <T> the result type
   * @return the body's result
   */
  public static <T> T trace(String actionType, Class<?> declaringClass, String methodName, List<Object> args,
      Supplier<T> body) {
    return trace(actionType, null, declaringClass, methodName, declaringClass, null, args, body);
  }

  /**
   * Traces a user method annotated with {@link Trace}.
   *
   * @param trace the annotation
   * @param method the method
   * @param target the instance, or {@code null} for a static method
   * @param args the arguments
   * @param body the invocation
   * @param <T> the result type
   * @return the body's result
   */
  public static <T> T traceMethod(Trace trace, Method method, Object target, List<Object> args, Supplier<T> body) {
    String description = trace.description().isEmpty() ? null : trace.description();
    Object internalTarget = target == null ? method.getDeclaringClass() : target;
    return trace(trace.value(), description, method.getDeclaringClass(), method.getName(), internalTarget, method,
        args, body);
  }

  /**
   * The general form.
   *
   * @param actionType the action
   * @param description the description, or {@code null}
   * @param declaringClass the class of the traced method
   * @param methodName the traced method
   * @param target the {@code internal.target} (the instance, or the class for static methods)
   * @param method the {@code internal.method}, or {@code null} to look it up by name when needed
   * @param args the arguments
   * @param body the body
   * @param <T> the result type
   * @return the body's result
   */
  public static <T> T trace(String actionType, String description, Class<?> declaringClass, String methodName,
      Object target, Method method, List<Object> args, Supplier<T> body) {
    BoosterConfig config = Booster.findConfig().orElse(null);
    if (!TraceNotifier.isTraceEnabled(actionType, config)) {
      return body.get();
    }
    TraceInfo parameters = new TraceInfo(declaringClass == null ? "" : declaringClass.getSimpleName(), methodName, args,
        UUID.generate(), description);
    if (config.traceConfiguration.includeInternal) {
      parameters.internal = new TraceInfo.Internal(target, method != null ? method : findMethod(declaringClass, methodName));
    }
    long startTime = Dates.nowEpochMillis();
    TraceNotifier.notifyTrace(TraceTypes.START, actionType, parameters, config);
    try {
      return body.get();
    } finally {
      parameters.elapsedInvocationMillis = Dates.nowEpochMillis() - startTime;
      TraceNotifier.notifyTrace(TraceTypes.END, actionType, parameters, config);
    }
  }

  private static Method findMethod(Class<?> declaringClass, String methodName) {
    if (declaringClass == null) {
      return null;
    }
    return Arrays.stream(declaringClass.getDeclaredMethods())
        .filter(candidate -> candidate.getName().equals(methodName) && !candidate.isSynthetic())
        .findFirst()
        .orElse(null);
  }
}
