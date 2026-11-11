package com.boostercloud.framework.core.internal;

import com.boostercloud.framework.core.decorators.Trace;
import com.boostercloud.framework.core.instrumentation.Tracer;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Calls user code the way TypeScript does with {@code (cls as any)[methodName](...args)}: handlers, reducers,
 * projections, hooks and migrations are methods looked up <b>by name</b> at call time.
 *
 * <ul>
 *   <li>Like a JavaScript function, a method may declare fewer parameters than the arguments it is called with: the
 *       method with the most parameters (at most the number of arguments) whose parameter types accept the leading
 *       arguments is chosen ({@code handle(MyCommand command)} works where the framework passes
 *       {@code (command, register)}). Static methods are searched in the class and its superclasses (JavaScript
 *       inherits static members).</li>
 *   <li>A method annotated with {@link Trace} is wrapped with {@link Tracer} (the Java replacement of the TypeScript
 *       method decorator, which cannot intercept static calls).</li>
 *   <li>A {@link CompletionStage}/{@link Future} result is waited for (TypeScript awaits every result).</li>
 *   <li>Exceptions thrown by user code propagate unchanged, also checked ones (TypeScript rethrows the rejection
 *       as-is), so global error handlers receive the original error.</li>
 * </ul>
 *
 * <p>Internal to framework-core (used by every area of the module), not part of the user-facing API.
 */
public final class UserCode {

  private UserCode() {}

  /**
   * Finds and calls a static method.
   *
   * @param type the class
   * @param methodName the static method name
   * @param args the arguments
   * @return the (awaited) result, {@code null} for {@code void}
   * @throws IllegalStateException when no static method with that name accepts the arguments
   */
  public static Object invokeStatic(Class<?> type, String methodName, Object... args) {
    Method method = findStatic(type, methodName, args)
        .orElseThrow(() -> new IllegalStateException(missingMethodMessage(type, methodName, true, args)));
    return invoke(method, null, args);
  }

  /**
   * Finds a static method that can be called with the given arguments (or with a prefix of them).
   *
   * @param type the class
   * @param methodName the method name
   * @param args the arguments it will be called with
   * @return the method
   */
  public static Optional<Method> findStatic(Class<?> type, String methodName, Object... args) {
    return resolve(type, methodName, true, args);
  }

  /**
   * Finds an instance method that can be called with the given arguments (or with a prefix of them).
   *
   * @param type the class
   * @param methodName the method name
   * @param args the arguments it will be called with
   * @return the method
   */
  public static Optional<Method> findInstance(Class<?> type, String methodName, Object... args) {
    return resolve(type, methodName, false, args);
  }

  /**
   * @param type a class
   * @param methodName a method name
   * @return whether the class or one of its superclasses declares a static method with that name
   */
  public static boolean hasStaticMethod(Class<?> type, String methodName) {
    return !candidates(type, methodName, true).isEmpty();
  }

  /**
   * Calls a method with the first {@code method.getParameterCount()} arguments, honouring {@link Trace} and waiting
   * for asynchronous results.
   *
   * @param method the method
   * @param target the instance, {@code null} for static methods
   * @param args the arguments (extra ones are ignored, like in JavaScript)
   * @return the (awaited) result
   */
  public static Object invoke(Method method, Object target, Object... args) {
    Object[] actualArguments = Arrays.copyOf(args, method.getParameterCount());
    Trace trace = method.getAnnotation(Trace.class);
    if (trace == null) {
      return await(rawInvoke(method, target, actualArguments));
    }
    return Tracer.traceMethod(trace, method, target, Arrays.asList(actualArguments),
        () -> await(rawInvoke(method, target, actualArguments)));
  }

  /**
   * Creates an instance with the no-argument constructor (any visibility), as TypeScript's {@code new Class()}.
   *
   * @param type the class
   * @param <T> the type
   * @return the instance
   * @throws IllegalStateException when the class has no no-argument constructor
   */
  public static <T> T newInstance(Class<T> type) {
    Constructor<T> constructor;
    try {
      constructor = type.getDeclaredConstructor();
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException("The class " + type.getName() + " needs a constructor without parameters", e);
    }
    constructor.trySetAccessible();
    try {
      return constructor.newInstance();
    } catch (InvocationTargetException e) {
      throw rethrow(e.getCause());
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Could not instantiate " + type.getName() + ": " + e.getMessage(), e);
    }
  }

  /**
   * TypeScript's {@code await value}: waits for a {@link CompletionStage} or {@link Future} and returns its value
   * (rethrowing its failure unchanged); any other value is returned as is.
   *
   * @param value a value
   * @return the awaited value
   */
  public static Object await(Object value) {
    try {
      return switch (value) {
        case CompletionStage<?> stage -> stage.toCompletableFuture().join();
        case Future<?> future -> future.get();
        case null, default -> value;
      };
    } catch (CompletionException e) {
      throw rethrow(e.getCause() == null ? e : e.getCause());
    } catch (ExecutionException e) {
      throw rethrow(e.getCause() == null ? e : e.getCause());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for a result", e);
    }
  }

  /**
   * Rethrows any throwable without wrapping it (also checked exceptions).
   *
   * @param throwable the throwable
   * @return never returns; declared so callers can write {@code throw UserCode.rethrow(e)}
   */
  public static RuntimeException rethrow(Throwable throwable) {
    return UserCode.<RuntimeException>sneakyThrow(throwable);
  }

  @SuppressWarnings("unchecked")
  private static <E extends Throwable> E sneakyThrow(Throwable throwable) throws E {
    throw (E) throwable;
  }

  private static Object rawInvoke(Method method, Object target, Object[] args) {
    method.trySetAccessible();
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException e) {
      throw rethrow(e.getCause());
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("Could not call " + describe(method) + ": " + e.getMessage(), e);
    }
  }

  private static Optional<Method> resolve(Class<?> type, String methodName, boolean isStatic, Object[] args) {
    List<Method> candidates = candidates(type, methodName, isStatic);
    for (int arity = args.length; arity >= 0; arity--) {
      Method best = null;
      for (Method candidate : candidates) {
        if (candidate.getParameterCount() != arity || !accepts(candidate, args)) {
          continue;
        }
        if (best == null || isMoreSpecific(candidate, best)) {
          best = candidate;
        }
      }
      if (best != null) {
        return Optional.of(best);
      }
    }
    return Optional.empty();
  }

  private static List<Method> candidates(Class<?> type, String methodName, boolean isStatic) {
    List<Method> methods = new ArrayList<>();
    for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
      for (Method method : current.getDeclaredMethods()) {
        if (method.getName().equals(methodName) && Modifier.isStatic(method.getModifiers()) == isStatic
            && !method.isSynthetic() && !method.isBridge()) {
          methods.add(method);
        }
      }
    }
    return methods;
  }

  private static boolean accepts(Method method, Object[] args) {
    Class<?>[] parameterTypes = method.getParameterTypes();
    for (int i = 0; i < parameterTypes.length; i++) {
      Object argument = args[i];
      Class<?> parameterType = parameterTypes[i];
      if (argument == null ? parameterType.isPrimitive() : !wrap(parameterType).isInstance(argument)) {
        return false;
      }
    }
    return true;
  }

  private static boolean isMoreSpecific(Method candidate, Method current) {
    Class<?>[] candidateTypes = candidate.getParameterTypes();
    Class<?>[] currentTypes = current.getParameterTypes();
    boolean strictlyMoreSpecific = false;
    for (int i = 0; i < candidateTypes.length; i++) {
      if (!wrap(currentTypes[i]).isAssignableFrom(wrap(candidateTypes[i]))) {
        return false;
      }
      strictlyMoreSpecific |= !candidateTypes[i].equals(currentTypes[i]);
    }
    return strictlyMoreSpecific;
  }

  private static Class<?> wrap(Class<?> type) {
    if (!type.isPrimitive()) {
      return type;
    }
    return switch (type.getName()) {
      case "int" -> Integer.class;
      case "long" -> Long.class;
      case "double" -> Double.class;
      case "float" -> Float.class;
      case "boolean" -> Boolean.class;
      case "short" -> Short.class;
      case "byte" -> Byte.class;
      case "char" -> Character.class;
      default -> Void.class;
    };
  }

  private static String missingMethodMessage(Class<?> type, String methodName, boolean isStatic, Object[] args) {
    String kind = isStatic ? "static " : "";
    if (candidates(type, methodName, isStatic).isEmpty()) {
      return type.getSimpleName() + "." + methodName + " is not a function: the class " + type.getName()
          + " must declare a " + kind + "method called \"" + methodName + "\"";
    }
    List<String> argumentTypes = Arrays.stream(args).map(arg -> arg == null ? "null" : arg.getClass().getSimpleName()).toList();
    return "No " + kind + "method " + type.getSimpleName() + "." + methodName + " accepts the arguments " + argumentTypes;
  }

  private static String describe(Method method) {
    return method.getDeclaringClass().getName() + "." + method.getName();
  }
}
