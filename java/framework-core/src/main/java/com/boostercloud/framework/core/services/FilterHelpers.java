package com.boostercloud.framework.core.services;

import com.boostercloud.framework.core.decorators.Trace;
import com.boostercloud.framework.core.internal.UserCode;
import com.boostercloud.framework.types.concepts.CommandBeforeFunction;
import com.boostercloud.framework.types.concepts.QueryBeforeFunction;
import com.boostercloud.framework.types.concepts.ReadModelBeforeFunction;
import com.boostercloud.framework.types.envelope.ReadModelRequestEnvelope;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Port of {@code services/filter-helpers.ts}: applies {@code before} hook chains strictly in order, each hook
 * receiving the previous hook's result (hooks may mutate their input and/or return another object).
 *
 * <p>TypeScript has one {@code applyBeforeFunctions} for command and query hooks; Java has two methods because the
 * hook interfaces are distinct types. Hook methods annotated with {@code @Trace} are traced.
 */
public final class FilterHelpers {

  private FilterHelpers() {}

  /**
   * {@code applyReadModelRequestBeforeFunctions(envelope, beforeHooks, currentUser)}.
   *
   * @param readModelRequestEnvelope the request
   * @param beforeHooks the hooks
   * @param currentUser the user, or {@code null}
   * @return the result of the last hook (the input when there are none)
   */
  public static ReadModelRequestEnvelope applyReadModelRequestBeforeFunctions(ReadModelRequestEnvelope readModelRequestEnvelope,
      List<? extends ReadModelBeforeFunction> beforeHooks, UserEnvelope currentUser) {
    ReadModelRequestEnvelope result = readModelRequestEnvelope;
    for (ReadModelBeforeFunction beforeHook : beforeHooks) {
      ReadModelRequestEnvelope input = result;
      result = traced(beforeHook, input, currentUser, () -> beforeHook.apply(input, currentUser));
    }
    return result;
  }

  /**
   * {@code applyBeforeFunctions(commandInput, beforeHooks, currentUser)} for command hooks.
   *
   * @param commandInput the raw input
   * @param beforeHooks the hooks
   * @param currentUser the user, or {@code null}
   * @return the result of the last hook (the input when there are none)
   */
  public static Map<String, Object> applyBeforeFunctions(Map<String, Object> commandInput,
      List<? extends CommandBeforeFunction> beforeHooks, UserEnvelope currentUser) {
    Map<String, Object> result = commandInput;
    for (CommandBeforeFunction beforeHook : beforeHooks) {
      Map<String, Object> input = result;
      result = traced(beforeHook, input, currentUser, () -> beforeHook.apply(input, currentUser));
    }
    return result;
  }

  /**
   * {@code applyBeforeFunctions(queryInput, beforeHooks, currentUser)} for query hooks.
   *
   * @param queryInput the raw input
   * @param beforeHooks the hooks
   * @param currentUser the user, or {@code null}
   * @return the result of the last hook (the input when there are none)
   */
  public static Map<String, Object> applyQueryBeforeFunctions(Map<String, Object> queryInput,
      List<? extends QueryBeforeFunction> beforeHooks, UserEnvelope currentUser) {
    Map<String, Object> result = queryInput;
    for (QueryBeforeFunction beforeHook : beforeHooks) {
      Map<String, Object> input = result;
      result = traced(beforeHook, input, currentUser, () -> beforeHook.apply(input, currentUser));
    }
    return result;
  }

  /** Calls a hook directly, or through {@link UserCode#invoke} when its {@code apply} method carries {@code @Trace}. */
  @SuppressWarnings("unchecked")
  private static <T> T traced(Object hook, Object input, UserEnvelope currentUser, Supplier<T> directCall) {
    Optional<Method> traced = UserCode.findInstance(hook.getClass(), "apply", input, currentUser)
        .filter(method -> method.isAnnotationPresent(Trace.class));
    if (traced.isPresent()) {
      return (T) UserCode.invoke(traced.get(), hook, input, currentUser);
    }
    return directCall.get();
  }
}
