package com.boostercloud.metadata;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.AnnotatedWildcardType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * Reflection-based extraction of {@link ClassMetadata} and {@link TypeMetadata}. Java counterpart of
 * {@code metadata-extractors.ts} ({@code getClassInfo}, {@code getTypeInfo}); instead of reading the TypeScript
 * checker at compile time it reads Java reflection at run time, and caches the result per class.
 */
public final class MetadataExtractors {

  /** Recursion limit of type arguments, same as TypeScript ({@code if (8 < depth)}). */
  public static final int DEPTH_LIMIT = 8;

  /** Simple name of the annotation that marks a method as a calculated field. */
  public static final String CALCULATED_FIELD_ANNOTATION = "CalculatedField";

  /** Simple name of the annotation that marks a property or type argument as nullable. */
  public static final String NULLABLE_ANNOTATION = "Nullable";

  private static final ClassValue<ClassMetadata> CLASS_METADATA =
      new ClassValue<>() {
        @Override
        protected ClassMetadata computeValue(Class<?> type) {
          return buildClassMetadata(type);
        }
      };

  private MetadataExtractors() {}

  /** Declaration context of a type, the Java analogue of the TypeScript declaration node passed down. */
  private record Declaration(boolean nullable, boolean getAccessor) {
    static final Declaration NONE = new Declaration(false, false);
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Class metadata
  // ---------------------------------------------------------------------------------------------------------------

  /**
   * Returns the metadata of a class, computing it on first use.
   *
   * @param type the class
   * @return the cached metadata
   */
  public static ClassMetadata classMetadataOf(Class<?> type) {
    Objects.requireNonNull(type, "type");
    if (type.isPrimitive() || type.isArray()) {
      throw new IllegalArgumentException("Cannot get class metadata of " + type.getTypeName());
    }
    if (type.getSimpleName().isEmpty()) {
      throw new IllegalArgumentException(
          "Could not extract typeName for type " + type.getName() + ": anonymous classes have no name");
    }
    return CLASS_METADATA.get(type);
  }

  /**
   * Describes a type without declaration context.
   *
   * @param type the type
   * @return its metadata
   */
  public static TypeMetadata typeMetadataOf(Type type) {
    return describe(type, null, 0, Declaration.NONE, Map.of());
  }

  private static ClassMetadata buildClassMetadata(Class<?> type) {
    Map<TypeVariable<?>, Type> bindings = bindingsFor(type);
    List<PropertyMetadata> fields = new ArrayList<>();
    List<Class<?>> hierarchy = type.isRecord() ? List.of(type) : hierarchyRootFirst(type);
    if (type.isRecord()) {
      for (RecordComponent component : type.getRecordComponents()) {
        fields.add(recordComponentProperty(component, bindings));
      }
    } else {
      for (Class<?> owner : hierarchy) {
        for (Field field : DeclarationOrder.fields(owner)) {
          int modifiers = field.getModifiers();
          if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic()) {
            continue;
          }
          fields.add(fieldProperty(field, bindings));
        }
      }
    }
    for (Class<?> owner : hierarchy) {
      for (Method method : DeclarationOrder.methods(owner)) {
        if (isCalculatedField(method)) {
          fields.add(calculatedProperty(method, bindings));
        }
      }
    }
    List<MethodMetadata> methods = new ArrayList<>();
    for (Method method : DeclarationOrder.methods(type)) {
      if (isListedMethod(type, method)) {
        methods.add(methodMetadata(method, bindings));
      }
    }
    return new ClassMetadata(type.getSimpleName(), type, fields, methods);
  }

  private static List<Class<?>> hierarchyRootFirst(Class<?> type) {
    Deque<Class<?>> chain = new ArrayDeque<>();
    for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
      if (current != type && isJdk(current)) {
        break;
      }
      chain.addFirst(current);
    }
    return List.copyOf(chain);
  }

  private static boolean isJdk(Class<?> type) {
    ClassLoader loader = type.getClassLoader();
    return loader == null || loader == ClassLoader.getPlatformClassLoader();
  }

  private static PropertyMetadata recordComponentProperty(
      RecordComponent component, Map<TypeVariable<?>, Type> bindings) {
    boolean nullable = hasNullableAnnotation(component)
        || recordComponentRelatedElements(component).stream().anyMatch(MetadataExtractors::hasNullableAnnotation)
        || hasNullableTypeAnnotation(component.getAnnotatedType());
    TypeMetadata typeInfo =
        describe(component.getGenericType(), component.getAnnotatedType(), 0, new Declaration(nullable, false), bindings);
    return new PropertyMetadata(component.getName(), typeInfo, List.of(), PropertyMetadata.Kind.RECORD_COMPONENT, component);
  }

  private static PropertyMetadata fieldProperty(Field field, Map<TypeVariable<?>, Type> bindings) {
    boolean nullable = hasNullableAnnotation(field) || hasNullableTypeAnnotation(field.getAnnotatedType());
    TypeMetadata typeInfo =
        describe(field.getGenericType(), field.getAnnotatedType(), 0, new Declaration(nullable, false), bindings);
    return new PropertyMetadata(field.getName(), typeInfo, List.of(), PropertyMetadata.Kind.FIELD, field);
  }

  private static PropertyMetadata calculatedProperty(Method method, Map<TypeVariable<?>, Type> bindings) {
    boolean nullable = hasNullableAnnotation(method) || hasNullableTypeAnnotation(method.getAnnotatedReturnType());
    TypeMetadata typeInfo = describe(
        method.getGenericReturnType(), method.getAnnotatedReturnType(), 0, new Declaration(nullable, true), bindings);
    return new PropertyMetadata(
        method.getName(), typeInfo, calculatedFieldDependencies(method), PropertyMetadata.Kind.CALCULATED, method);
  }

  private static MethodMetadata methodMetadata(Method method, Map<TypeVariable<?>, Type> bindings) {
    boolean nullable = hasNullableAnnotation(method) || hasNullableTypeAnnotation(method.getAnnotatedReturnType());
    TypeMetadata returnType = describe(
        method.getGenericReturnType(), method.getAnnotatedReturnType(), 0, new Declaration(nullable, false), bindings);
    List<PropertyMetadata> parameters = new ArrayList<>();
    for (Parameter parameter : method.getParameters()) {
      boolean parameterNullable =
          hasNullableAnnotation(parameter) || hasNullableTypeAnnotation(parameter.getAnnotatedType());
      TypeMetadata parameterType = describe(
          parameter.getParameterizedType(),
          parameter.getAnnotatedType(),
          0,
          new Declaration(parameterNullable, false),
          bindings);
      parameters.add(
          new PropertyMetadata(parameter.getName(), parameterType, List.of(), PropertyMetadata.Kind.PARAMETER, parameter));
    }
    return new MethodMetadata(
        method.getName(), returnType, Modifier.isStatic(method.getModifiers()), parameters, method);
  }

  private static boolean isCalculatedField(Method method) {
    if (method.isSynthetic() || method.isBridge() || !hasAnnotationNamed(method, CALCULATED_FIELD_ANNOTATION)) {
      return false;
    }
    if (Modifier.isStatic(method.getModifiers())
        || method.getParameterCount() != 0
        || method.getReturnType() == void.class) {
      throw new IllegalArgumentException(
          "The calculated field " + method.getDeclaringClass().getSimpleName() + "." + method.getName()
              + " must be a non-static method without parameters that returns a value");
    }
    return !isRecordAccessor(method.getDeclaringClass(), method);
  }

  private static List<String> calculatedFieldDependencies(Method method) {
    for (Annotation annotation : method.getAnnotations()) {
      if (annotation.annotationType().getSimpleName().equals(CALCULATED_FIELD_ANNOTATION)) {
        try {
          Method dependsOn = annotation.annotationType().getMethod("dependsOn");
          Object value = dependsOn.invoke(annotation);
          if (value instanceof String[] names) {
            return List.of(names);
          }
        } catch (ReflectiveOperationException e) {
          return List.of();
        }
      }
    }
    return List.of();
  }

  private static boolean isListedMethod(Class<?> owner, Method method) {
    if (method.isSynthetic() || method.isBridge()) {
      return false;
    }
    if (hasAnnotationNamed(method, CALCULATED_FIELD_ANNOTATION) && isCalculatedField(method)) {
      return false;
    }
    if (owner.isRecord()) {
      if (isRecordAccessor(owner, method)) {
        return false;
      }
      String name = method.getName();
      if ((name.equals("toString") || name.equals("hashCode")) && method.getParameterCount() == 0) {
        return false;
      }
      if (name.equals("equals") && method.getParameterCount() == 1 && method.getParameterTypes()[0] == Object.class) {
        return false;
      }
    }
    if (owner.isEnum()) {
      String name = method.getName();
      if (name.equals("values") && method.getParameterCount() == 0) {
        return false;
      }
      if (name.equals("valueOf") && method.getParameterCount() == 1 && method.getParameterTypes()[0] == String.class) {
        return false;
      }
    }
    return true;
  }

  private static boolean isRecordAccessor(Class<?> owner, Method method) {
    if (!owner.isRecord() || method.getParameterCount() != 0 || Modifier.isStatic(method.getModifiers())) {
      return false;
    }
    for (RecordComponent component : owner.getRecordComponents()) {
      if (component.getName().equals(method.getName())) {
        return true;
      }
    }
    return false;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Type metadata
  // ---------------------------------------------------------------------------------------------------------------

  private static TypeMetadata describe(
      Type declared, AnnotatedType annotated, int depth, Declaration declaration, Map<TypeVariable<?>, Type> bindings) {
    Type type = resolve(declared, bindings);
    boolean typeNullable = depth > 0 && hasNullableTypeAnnotation(annotated);
    // Unwrap Optional<T>: the metadata describes T, nullable (TypeScript: `T | undefined`)
    while (rawClassOf(type) == Optional.class) {
      typeNullable = true;
      AnnotatedType inner = annotatedTypeArgument(annotated, 0);
      Type argument = typeArguments(type, Optional.class, bindings)[0];
      if (inner != null && hasNullableTypeAnnotation(inner)) {
        typeNullable = true;
      }
      type = argument;
      annotated = inner;
    }
    Class<?> raw = rawClassOf(type);
    if (raw == OptionalInt.class || raw == OptionalLong.class || raw == OptionalDouble.class) {
      typeNullable = true;
    }
    String name = tsName(type, bindings);
    boolean isNullable = typeNullable || declaration.nullable();
    boolean isGetAccessor = declaration.getAccessor();
    if (DEPTH_LIMIT < depth) {
      return new TypeMetadata(name, TypeGroup.OTHER, null, List.of(), isNullable, isGetAccessor, null, null, declared);
    }

    if (raw == null) {
      // Unresolved type variable, or wildcard without a usable bound
      return new TypeMetadata(name, TypeGroup.OTHER, null, List.of(), isNullable, isGetAccessor, null, null, declared);
    }
    if (isStringClass(raw)) {
      return basic(name, TypeGroup.STRING, raw, isNullable, isGetAccessor, declared);
    }
    if (isNumberClass(raw)) {
      return basic(name, TypeGroup.NUMBER, raw, isNullable, isGetAccessor, declared);
    }
    if (raw == boolean.class || raw == Boolean.class) {
      return basic(name, TypeGroup.BOOLEAN, raw, isNullable, isGetAccessor, declared);
    }
    if (isDateClass(raw)) {
      return new TypeMetadata(name, TypeGroup.INTERFACE, null, List.of(), isNullable, isGetAccessor, null, null, declared);
    }
    if (raw == void.class || raw == Void.class || raw == Object.class) {
      return new TypeMetadata(name, TypeGroup.OTHER, null, List.of(), isNullable, isGetAccessor, null, null, declared);
    }
    if (raw.isEnum() || (raw.getSuperclass() != null && raw.getSuperclass().isEnum())) {
      Class<?> enumClass = raw.isEnum() ? raw : raw.getSuperclass();
      List<TypeMetadata> constants = Arrays.stream(enumClass.getEnumConstants())
          .map(constant -> new TypeMetadata(
              ((Enum<?>) constant).name(), TypeGroup.OTHER, null, List.of(), false, false, null, null, null))
          .toList();
      String simpleName = enumClass.getSimpleName();
      return new TypeMetadata(
          name, TypeGroup.ENUM, simpleName, constants, isNullable, isGetAccessor, enumClass.getPackageName(), enumClass, declared);
    }
    if (Future.class.isAssignableFrom(raw) || CompletionStage.class.isAssignableFrom(raw)) {
      Class<?> target = CompletionStage.class.isAssignableFrom(raw) ? CompletionStage.class : Future.class;
      TypeMetadata result = describe(
          typeArguments(type, target, bindings)[0], annotatedTypeArgument(annotated, 0), depth + 1, declaration, bindings);
      return new TypeMetadata(
          name, TypeGroup.OBJECT, "Promise", List.of(result), isNullable, isGetAccessor, null, raw, declared);
    }
    if (raw.isArray() || Iterable.class.isAssignableFrom(raw)) {
      Type element = raw.isArray() ? arrayComponent(type) : typeArguments(type, Iterable.class, bindings)[0];
      AnnotatedType annotatedElement = annotated instanceof AnnotatedArrayType array
          ? array.getAnnotatedGenericComponentType()
          : annotatedTypeArgument(annotated, 0);
      TypeMetadata elementType = describe(element, annotatedElement, depth + 1, declaration, bindings);
      return new TypeMetadata(
          name, TypeGroup.ARRAY, "Array", List.of(elementType), isNullable, isGetAccessor, null, raw, declared);
    }
    if (Map.class.isAssignableFrom(raw)) {
      Type[] arguments = typeArguments(type, Map.class, bindings);
      List<TypeMetadata> parameters = List.of(
          describe(arguments[0], annotatedTypeArgument(annotated, 0), depth + 1, declaration, bindings),
          describe(arguments[1], annotatedTypeArgument(annotated, 1), depth + 1, declaration, bindings));
      return new TypeMetadata(name, TypeGroup.TYPE, null, parameters, isNullable, isGetAccessor, null, null, declared);
    }
    if (isFunctionClass(raw)) {
      return new TypeMetadata(
          name, TypeGroup.FUNCTION, null, typeArgumentMetadata(type, annotated, depth, declaration, bindings),
          isNullable, isGetAccessor, null, null, declared);
    }
    if (raw.isSealed()) {
      List<TypeMetadata> members = new ArrayList<>();
      for (Class<?> permitted : raw.getPermittedSubclasses()) {
        members.add(describe(permitted, null, depth + 1, declaration, bindings));
      }
      return new TypeMetadata(name, TypeGroup.UNION, null, members, isNullable, isGetAccessor, null, null, declared);
    }
    if (type instanceof ParameterizedType) {
      return new TypeMetadata(
          name, TypeGroup.OBJECT, raw.getSimpleName(), typeArgumentMetadata(type, annotated, depth, declaration, bindings),
          isNullable, isGetAccessor, raw.getPackageName(), raw, declared);
    }
    if (raw.isInterface()) {
      return new TypeMetadata(name, TypeGroup.INTERFACE, null, List.of(), isNullable, isGetAccessor, null, null, declared);
    }
    return new TypeMetadata(
        name, TypeGroup.CLASS, raw.getSimpleName(), List.of(), isNullable, isGetAccessor, raw.getPackageName(), raw, declared);
  }

  private static TypeMetadata basic(
      String name, TypeGroup group, Class<?> raw, boolean isNullable, boolean isGetAccessor, Type declared) {
    return new TypeMetadata(name, group, group.tsName(), List.of(), isNullable, isGetAccessor, null, raw, declared);
  }

  private static List<TypeMetadata> typeArgumentMetadata(
      Type type, AnnotatedType annotated, int depth, Declaration declaration, Map<TypeVariable<?>, Type> bindings) {
    if (!(type instanceof ParameterizedType parameterized)) {
      return List.of();
    }
    Type[] arguments = parameterized.getActualTypeArguments();
    List<TypeMetadata> result = new ArrayList<>();
    for (int i = 0; i < arguments.length; i++) {
      result.add(describe(arguments[i], annotatedTypeArgument(annotated, i), depth + 1, declaration, bindings));
    }
    return result;
  }

  private static boolean isStringClass(Class<?> raw) {
    return raw == String.class || raw == CharSequence.class || raw == char.class || raw == Character.class
        || raw == byte[].class;
  }

  private static boolean isNumberClass(Class<?> raw) {
    return raw == int.class || raw == long.class || raw == short.class || raw == byte.class || raw == float.class
        || raw == double.class || Number.class.isAssignableFrom(raw) || raw == OptionalInt.class
        || raw == OptionalLong.class || raw == OptionalDouble.class;
  }

  private static boolean isDateClass(Class<?> raw) {
    return raw == Instant.class || java.util.Date.class.isAssignableFrom(raw) || raw == OffsetDateTime.class
        || raw == ZonedDateTime.class;
  }

  private static boolean isFunctionClass(Class<?> raw) {
    if (!raw.isInterface()) {
      return false;
    }
    return raw.isAnnotationPresent(FunctionalInterface.class)
        || raw.getPackageName().equals("java.util.function")
        || raw == Runnable.class
        || raw == Callable.class;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // TypeScript-compatible names
  // ---------------------------------------------------------------------------------------------------------------

  /**
   * Computes the TypeScript-compatible text of a type, used as {@link TypeMetadata#name()}.
   *
   * @param type the type
   * @return its name, e.g. {@code string}, {@code Cart[]}, {@code Record<string, number>}
   */
  public static String tsName(Type type) {
    return tsName(type, Map.of());
  }

  private static String tsName(Type declared, Map<TypeVariable<?>, Type> bindings) {
    Type type = resolve(declared, bindings);
    if (type instanceof TypeVariable<?> variable) {
      return variable.getName();
    }
    if (type instanceof WildcardType) {
      return "unknown";
    }
    if (type instanceof GenericArrayType array) {
      return tsName(array.getGenericComponentType(), bindings) + "[]";
    }
    Class<?> raw = rawClassOf(type);
    if (raw == null) {
      return "unknown";
    }
    if (raw == Optional.class) {
      return tsName(typeArguments(type, Optional.class, bindings)[0], bindings);
    }
    if (isStringClass(raw)) {
      return "string";
    }
    if (isNumberClass(raw)) {
      return "number";
    }
    if (raw == boolean.class || raw == Boolean.class) {
      return "boolean";
    }
    if (raw == void.class || raw == Void.class) {
      return "void";
    }
    if (raw == Object.class) {
      return "unknown";
    }
    if (isDateClass(raw)) {
      return "Date";
    }
    if (raw.isEnum() || (raw.getSuperclass() != null && raw.getSuperclass().isEnum())) {
      return (raw.isEnum() ? raw : raw.getSuperclass()).getSimpleName();
    }
    if (Future.class.isAssignableFrom(raw) || CompletionStage.class.isAssignableFrom(raw)) {
      Class<?> target = CompletionStage.class.isAssignableFrom(raw) ? CompletionStage.class : Future.class;
      return "Promise<" + tsName(typeArguments(type, target, bindings)[0], bindings) + ">";
    }
    if (raw.isArray()) {
      return tsName(arrayComponent(type), bindings) + "[]";
    }
    if (Iterable.class.isAssignableFrom(raw)) {
      return tsName(typeArguments(type, Iterable.class, bindings)[0], bindings) + "[]";
    }
    if (Map.class.isAssignableFrom(raw)) {
      Type[] arguments = typeArguments(type, Map.class, bindings);
      return "Record<" + tsName(arguments[0], bindings) + ", " + tsName(arguments[1], bindings) + ">";
    }
    if (type instanceof ParameterizedType parameterized && !raw.isSealed()) {
      return raw.getSimpleName() + Arrays.stream(parameterized.getActualTypeArguments())
          .map(argument -> tsName(argument, bindings))
          .collect(Collectors.joining(", ", "<", ">"));
    }
    return raw.getSimpleName();
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Generic type resolution
  // ---------------------------------------------------------------------------------------------------------------

  /**
   * @param type a reflective type
   * @return its raw class, or {@code null} for type variables and wildcards without a class bound
   */
  public static Class<?> rawClassOf(Type type) {
    return switch (type) {
      case null -> null;
      case Class<?> c -> c;
      case ParameterizedType p -> (Class<?>) p.getRawType();
      case GenericArrayType a -> {
        Class<?> component = rawClassOf(a.getGenericComponentType());
        yield component == null ? Object[].class : component.arrayType();
      }
      case WildcardType w -> {
        Type[] upper = w.getUpperBounds();
        yield upper.length == 1 && upper[0] != Object.class && w.getLowerBounds().length == 0 ? rawClassOf(upper[0]) : null;
      }
      default -> null;
    };
  }

  static Type resolve(Type type, Map<TypeVariable<?>, Type> bindings) {
    Type current = type;
    int guard = 0;
    while (current instanceof TypeVariable<?> variable && bindings.containsKey(variable) && guard++ < 32) {
      current = bindings.get(variable);
    }
    if (current instanceof WildcardType wildcard) {
      Type[] upper = wildcard.getUpperBounds();
      if (wildcard.getLowerBounds().length == 0 && upper.length == 1 && upper[0] != Object.class) {
        return resolve(upper[0], bindings);
      }
    }
    if (current instanceof ParameterizedType || current instanceof GenericArrayType) {
      return substitute(current, bindings);
    }
    return current;
  }

  private static Type arrayComponent(Type type) {
    if (type instanceof GenericArrayType array) {
      return array.getGenericComponentType();
    }
    return ((Class<?>) type).getComponentType();
  }

  /**
   * Resolves the type arguments of {@code target} (a supertype of {@code type}) as seen from {@code type}; for
   * example the element type of {@code class Tags extends ArrayList<String>} seen as {@code Iterable}. Unknown
   * arguments are {@code Object.class}.
   */
  static Type[] typeArguments(Type type, Class<?> target, Map<TypeVariable<?>, Type> bindings) {
    Type[] found = findTypeArguments(resolve(type, bindings), target, new HashMap<>(bindings), 0);
    if (found == null) {
      Type[] unknown = new Type[target.getTypeParameters().length];
      Arrays.fill(unknown, Object.class);
      return unknown;
    }
    Type[] result = new Type[found.length];
    for (int i = 0; i < found.length; i++) {
      Type argument = resolve(found[i], bindings);
      // Unbounded or lower-bounded wildcards carry no usable type: describe them as `unknown`
      result[i] = argument instanceof WildcardType ? Object.class : argument;
    }
    return result;
  }

  private static Type[] findTypeArguments(Type type, Class<?> target, Map<TypeVariable<?>, Type> bindings, int depth) {
    Class<?> raw = rawClassOf(type);
    if (raw == null || depth > 32) {
      return null;
    }
    Map<TypeVariable<?>, Type> local = new HashMap<>(bindings);
    if (type instanceof ParameterizedType parameterized) {
      TypeVariable<?>[] variables = raw.getTypeParameters();
      Type[] arguments = parameterized.getActualTypeArguments();
      for (int i = 0; i < variables.length && i < arguments.length; i++) {
        local.put(variables[i], substitute(arguments[i], bindings));
      }
    } else if (type instanceof Class<?>) {
      // A raw use of a generic class (`List` without arguments): its variables erase to their bounds
      for (TypeVariable<?> variable : raw.getTypeParameters()) {
        Class<?> bound = rawClassOf(variable.getBounds()[0]);
        local.putIfAbsent(variable, bound == null ? Object.class : bound);
      }
    }
    if (raw == target) {
      TypeVariable<?>[] variables = raw.getTypeParameters();
      Type[] result = new Type[variables.length];
      for (int i = 0; i < variables.length; i++) {
        result[i] = local.containsKey(variables[i]) ? local.get(variables[i]) : Object.class;
      }
      return result;
    }
    List<Type> supertypes = new ArrayList<>();
    if (raw.getGenericSuperclass() != null) {
      supertypes.add(raw.getGenericSuperclass());
    }
    supertypes.addAll(Arrays.asList(raw.getGenericInterfaces()));
    for (Type supertype : supertypes) {
      Class<?> superRaw = rawClassOf(supertype);
      if (superRaw != null && target.isAssignableFrom(superRaw)) {
        Type[] found = findTypeArguments(substitute(supertype, local), target, local, depth + 1);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  /** Bindings of the type variables of every generic superclass of {@code type}, as bound by its subclasses. */
  static Map<TypeVariable<?>, Type> bindingsFor(Class<?> type) {
    Map<TypeVariable<?>, Type> bindings = new HashMap<>();
    List<Type> supertypes = new ArrayList<>();
    for (Class<?> current = type; current != null; current = current.getSuperclass()) {
      if (current.getGenericSuperclass() != null) {
        supertypes.add(current.getGenericSuperclass());
      }
    }
    // Resolve from the most derived class upwards, so that arguments referring to subclass variables are
    // substituted with what is already known.
    for (Type supertype : supertypes) {
      if (supertype instanceof ParameterizedType parameterized) {
        Class<?> raw = (Class<?>) parameterized.getRawType();
        TypeVariable<?>[] variables = raw.getTypeParameters();
        Type[] arguments = parameterized.getActualTypeArguments();
        for (int i = 0; i < variables.length && i < arguments.length; i++) {
          bindings.put(variables[i], substitute(arguments[i], bindings));
        }
      }
    }
    return bindings;
  }

  static Type substitute(Type type, Map<TypeVariable<?>, Type> bindings) {
    if (bindings.isEmpty()) {
      return type;
    }
    return switch (type) {
      case TypeVariable<?> variable -> bindings.containsKey(variable) ? bindings.get(variable) : variable;
      case ParameterizedType parameterized -> {
        Type[] arguments = parameterized.getActualTypeArguments();
        Type[] substituted = new Type[arguments.length];
        boolean changed = false;
        for (int i = 0; i < arguments.length; i++) {
          substituted[i] = substitute(arguments[i], bindings);
          changed |= substituted[i] != arguments[i];
        }
        yield changed
            ? new ResolvedParameterizedType(
                (Class<?>) parameterized.getRawType(), substituted, parameterized.getOwnerType())
            : parameterized;
      }
      case GenericArrayType array -> {
        Type component = substitute(array.getGenericComponentType(), bindings);
        if (component instanceof Class<?> componentClass) {
          yield componentClass.arrayType();
        }
        yield component == array.getGenericComponentType() ? array : new ResolvedGenericArrayType(component);
      }
      default -> type;
    };
  }

  /** A parameterized type built after substituting type variables. */
  record ResolvedParameterizedType(Class<?> rawType, Type[] actualTypeArguments, Type ownerType)
      implements ParameterizedType {
    @Override
    public Type[] getActualTypeArguments() {
      return actualTypeArguments.clone();
    }

    @Override
    public Type getRawType() {
      return rawType;
    }

    @Override
    public Type getOwnerType() {
      return ownerType;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof ParameterizedType that
          && rawType.equals(that.getRawType())
          && Objects.equals(ownerType, that.getOwnerType())
          && Arrays.equals(actualTypeArguments, that.getActualTypeArguments());
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(actualTypeArguments) ^ Objects.hashCode(ownerType) ^ rawType.hashCode();
    }

    @Override
    public String toString() {
      return rawType.getTypeName() + Arrays.stream(actualTypeArguments)
          .map(Type::getTypeName)
          .collect(Collectors.joining(", ", "<", ">"));
    }
  }

  /** A generic array type built after substituting type variables. */
  private record ResolvedGenericArrayType(Type genericComponentType) implements GenericArrayType {
    @Override
    public Type getGenericComponentType() {
      return genericComponentType;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof GenericArrayType that && genericComponentType.equals(that.getGenericComponentType());
    }

    @Override
    public int hashCode() {
      return genericComponentType.hashCode();
    }

    @Override
    public String toString() {
      return genericComponentType.getTypeName() + "[]";
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Annotations
  // ---------------------------------------------------------------------------------------------------------------

  /**
   * @param element an annotated element
   * @param simpleName an annotation simple name
   * @return whether the element carries an annotation (declaration or top-level type-use) with that simple name
   */
  public static boolean hasAnnotationNamed(AnnotatedElement element, String simpleName) {
    if (element == null) {
      return false;
    }
    for (Annotation annotation : element.getAnnotations()) {
      if (annotation.annotationType().getSimpleName().equals(simpleName)) {
        return true;
      }
    }
    AnnotatedType annotatedType = switch (element) {
      case Field field -> field.getAnnotatedType();
      case Method method -> method.getAnnotatedReturnType();
      case Parameter parameter -> parameter.getAnnotatedType();
      case RecordComponent component -> component.getAnnotatedType();
      default -> null;
    };
    return annotatedType != null && hasTypeAnnotationNamed(annotatedType, simpleName);
  }

  private static boolean hasNullableAnnotation(AnnotatedElement element) {
    return hasAnnotationNamed(element, NULLABLE_ANNOTATION);
  }

  private static boolean hasNullableTypeAnnotation(AnnotatedType annotated) {
    return annotated != null && hasTypeAnnotationNamed(annotated, NULLABLE_ANNOTATION);
  }

  private static boolean hasTypeAnnotationNamed(AnnotatedType annotated, String simpleName) {
    for (Annotation annotation : annotated.getAnnotations()) {
      if (annotation.annotationType().getSimpleName().equals(simpleName)) {
        return true;
      }
    }
    return false;
  }

  private static AnnotatedType annotatedTypeArgument(AnnotatedType annotated, int index) {
    if (annotated instanceof AnnotatedParameterizedType parameterized) {
      AnnotatedType[] arguments = parameterized.getAnnotatedActualTypeArguments();
      if (index < arguments.length) {
        AnnotatedType argument = arguments[index];
        if (argument instanceof AnnotatedWildcardType wildcard && wildcard.getAnnotatedUpperBounds().length == 1
            && wildcard.getAnnotations().length == 0) {
          return wildcard.getAnnotatedUpperBounds()[0];
        }
        return argument;
      }
    }
    return null;
  }

  /**
   * @param component a record component
   * @return its backing field, its accessor and the matching canonical constructor parameter (when they exist)
   */
  static List<AnnotatedElement> recordComponentRelatedElements(RecordComponent component) {
    List<AnnotatedElement> elements = new ArrayList<>();
    Class<?> record = component.getDeclaringRecord();
    try {
      elements.add(record.getDeclaredField(component.getName()));
    } catch (NoSuchFieldException ignored) {
      // A record always has a backing field; ignore defensively
    }
    elements.add(component.getAccessor());
    RecordComponent[] components = record.getRecordComponents();
    Class<?>[] parameterTypes = Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
    try {
      Constructor<?> canonical = record.getDeclaredConstructor(parameterTypes);
      for (int i = 0; i < components.length; i++) {
        if (components[i].getName().equals(component.getName())) {
          elements.add(canonical.getParameters()[i]);
        }
      }
    } catch (NoSuchMethodException ignored) {
      // Every record has a canonical constructor; ignore defensively
    }
    return elements;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Reflection helpers
  // ---------------------------------------------------------------------------------------------------------------

  static <T extends AccessibleObject> T accessible(T object) {
    object.trySetAccessible();
    return object;
  }

  @SuppressWarnings("unchecked")
  static <E extends Throwable> RuntimeException sneakyThrow(Throwable throwable) throws E {
    throw (E) throwable;
  }
}
