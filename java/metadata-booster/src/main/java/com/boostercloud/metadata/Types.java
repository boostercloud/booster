package com.boostercloud.metadata;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Generic type utilities used by the metadata reader, exposed for the modules that bind raw values to Java types
 * (instance creation, GraphQL resolvers).
 */
public final class Types {
  private Types() {}

  /**
   * @param type a reflective type
   * @return its raw class, or {@code null} for unresolved type variables and unbounded wildcards
   */
  public static Class<?> rawClass(Type type) {
    return MetadataExtractors.rawClassOf(type);
  }

  /**
   * Resolves the type arguments of a generic supertype as seen from {@code type}: the element type of
   * {@code List<String>} or of {@code class Tags extends ArrayList<String>} seen as {@code Iterable}, the key and
   * value types of a {@code Map}. Unknown arguments are {@code Object.class}; unresolved variables stay variables.
   *
   * @param type a type
   * @param supertype a generic supertype of the raw class of {@code type}
   * @return the arguments of {@code supertype}
   */
  public static Type[] typeArguments(Type type, Class<?> supertype) {
    return MetadataExtractors.typeArguments(type, supertype, Map.of());
  }

  /**
   * The bindings of the type variables of a class: those of its own parameters when {@code type} is parameterized
   * ({@code Page<Cart>} binds {@code T -> Cart}) plus those of its generic superclasses.
   *
   * @param type a class or parameterized type
   * @return variable to bound type
   */
  public static Map<TypeVariable<?>, Type> bindings(Type type) {
    Class<?> raw = rawClass(type);
    Map<TypeVariable<?>, Type> bindings = new HashMap<>(raw == null ? Map.of() : MetadataExtractors.bindingsFor(raw));
    if (type instanceof ParameterizedType parameterized && raw != null) {
      TypeVariable<?>[] variables = raw.getTypeParameters();
      Type[] arguments = parameterized.getActualTypeArguments();
      for (int i = 0; i < variables.length && i < arguments.length; i++) {
        bindings.put(variables[i], arguments[i]);
      }
    }
    return bindings;
  }

  /**
   * Replaces the bound type variables inside a type.
   *
   * @param type a type that may mention type variables
   * @param bindings variable bindings
   * @return the substituted type
   */
  public static Type resolve(Type type, Map<TypeVariable<?>, Type> bindings) {
    return MetadataExtractors.resolve(type, bindings);
  }

  /**
   * @param rawType a generic class
   * @param arguments its type arguments
   * @return the parameterized type {@code rawType<arguments>}
   */
  public static ParameterizedType parameterized(Class<?> rawType, Type... arguments) {
    Objects.requireNonNull(rawType, "rawType");
    if (rawType.getTypeParameters().length != arguments.length) {
      throw new IllegalArgumentException(rawType.getName() + " has " + rawType.getTypeParameters().length
          + " type parameters, but " + arguments.length + " arguments were given");
    }
    return new MetadataExtractors.ResolvedParameterizedType(rawType, arguments.clone(), rawType.getDeclaringClass());
  }
}
