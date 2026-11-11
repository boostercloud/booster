package com.boostercloud.metadata;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;

/**
 * One method declared by a class (static or instance). Java counterpart of the entries of the TypeScript
 * {@code ClassMetadata.methods} array, which describe only the name and the return type; the Java version also
 * describes the parameters (TypeScript lists that as a missing feature) and keeps the reflective {@link Method}.
 *
 * @param name the method name
 * @param typeInfo the return type ({@code void} methods give {@code {name: "void", typeGroup: Other}})
 * @param isStatic whether the method is static (Booster handlers such as {@code handle} are static)
 * @param parameters the parameters, in order; names require compiling with {@code -parameters}
 *     (otherwise {@code arg0}, {@code arg1}, ...)
 * @param method the reflective method
 */
public record MethodMetadata(
    String name, TypeMetadata typeInfo, boolean isStatic, List<PropertyMetadata> parameters, Method method)
    implements MemberMetadata {

  /** Canonical constructor; copies {@code parameters} into an immutable list. */
  public MethodMetadata {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(typeInfo, "typeInfo");
    Objects.requireNonNull(method, "method");
    parameters = List.copyOf(parameters);
  }

  /**
   * @return whether the method returns nothing ({@code void} or {@code Void})
   */
  public boolean returnsVoid() {
    Class<?> returnType = method.getReturnType();
    return returnType == void.class || returnType == Void.class;
  }

  @Override
  public String toString() {
    return "MethodMetadata{name=" + name + ", static=" + isStatic + ", returns=" + typeInfo.name()
        + ", parameters=" + parameters.stream().map(p -> p.name() + ": " + p.typeInfo().name()).toList() + "}";
  }
}
