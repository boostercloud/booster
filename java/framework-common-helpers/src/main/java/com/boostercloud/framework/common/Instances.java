package com.boostercloud.framework.common;

import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.metadata.ClassMetadata;
import com.boostercloud.metadata.PropertyMetadata;
import com.boostercloud.metadata.Types;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Turns raw values (maps read from a database, GraphQL inputs) into instances of user classes. Port of
 * {@code instances.ts}.
 *
 * <p>TypeScript's {@code createInstance} is {@code Object.assign(new Class(), raw)}: a shallow copy onto an instance
 * built with no constructor arguments. Java classes cannot be filled like that, so the Java version <b>binds</b> the
 * raw value to the class, recursively:
 *
 * <ul>
 *   <li>records through their canonical constructor (components by name; {@code @JsonProperty} renames honoured);
 *   <li>classes through the constructor whose parameter names match their fields (compile with {@code -parameters}),
 *       else their no-argument constructor, then assigning the remaining non-final fields;
 *   <li>nested records/classes, {@code List}/{@code Set}/arrays, {@code Map}s, {@code Optional}s, enums, dates
 *       (ISO strings or epoch milliseconds), {@code UUID}s and numbers are converted to the declared (generic) types;
 *   <li>sealed interfaces pick the permitted class that has every raw key and the fewest properties left unset;
 *   <li>types with Jackson annotations ({@code @JsonCreator}, {@code @JsonValue}, {@code @JsonTypeInfo}) are bound
 *       with {@link BoosterJson#mapper()}.
 * </ul>
 *
 * <p>Missing properties become {@code null} (primitives their default, {@code Optional}s empty); unknown raw keys are
 * ignored. A raw value that already is an instance of the target class is returned as is.
 */
public final class Instances {

  private static final ClassValue<Binder> BINDERS =
      new ClassValue<>() {
        @Override
        protected Binder computeValue(Class<?> type) {
          return Binder.of(type);
        }
      };

  private Instances() {}

  /**
   * {@code createInstance(instanceClass, rawObject)}.
   *
   * @param instanceClass the class to create
   * @param rawObject a raw value (usually a {@code Map}), an instance, or {@code null} for an empty instance
   * @param <T> the class type
   * @return the instance
   * @throws IllegalArgumentException when the raw value cannot be bound to the class
   */
  @SuppressWarnings("unchecked")
  public static <T> T createInstance(Class<T> instanceClass, Object rawObject) {
    Objects.requireNonNull(instanceClass, "instanceClass");
    if (rawObject == null) {
      return (T) bindObject(instanceClass, instanceClass, Map.of());
    }
    return (T) convert(rawObject, instanceClass);
  }

  /**
   * {@code createInstances(instanceClass, rawObjects)}: {@link #createInstance(Class, Object)} on every element.
   *
   * @param instanceClass the class to create
   * @param rawObjects raw values
   * @param <T> the class type
   * @return the instances, in order
   */
  public static <T> List<T> createInstances(Class<T> instanceClass, Collection<?> rawObjects) {
    List<T> instances = new ArrayList<>(rawObjects.size());
    for (Object rawObject : rawObjects) {
      instances.add(createInstance(instanceClass, rawObject));
    }
    return instances;
  }

  /**
   * Converts a value to any reflective type (for example the generic type of a handler parameter).
   *
   * @param raw a raw value or an instance
   * @param targetType the target type
   * @return the converted value
   * @throws IllegalArgumentException when the value cannot be converted
   */
  public static Object convert(Object raw, Type targetType) {
    Objects.requireNonNull(targetType, "targetType");
    Class<?> target = Types.rawClass(targetType);
    if (target == null) {
      // Unresolved type variable: nothing to convert to
      return raw;
    }
    if (raw == null) {
      return absentValue(target);
    }
    if (target == Optional.class) {
      Object value = raw instanceof Optional<?> optional ? optional.orElse(null) : raw;
      return value == null ? Optional.empty() : Optional.ofNullable(convert(value, Types.typeArguments(targetType, Optional.class)[0]));
    }
    if (target == OptionalInt.class || target == OptionalLong.class || target == OptionalDouble.class) {
      return BoosterJson.convert(raw, target);
    }
    if (target.isArray() && target != byte[].class) {
      return convertArray(raw, targetType, target);
    }
    if (Collection.class.isAssignableFrom(target) || target == Iterable.class) {
      return convertCollection(raw, targetType, target);
    }
    if (Map.class.isAssignableFrom(target)) {
      return convertMap(raw, targetType, target);
    }
    if (target == Object.class) {
      return raw;
    }
    if (target.isInstance(raw) && !(raw instanceof Map<?, ?>)) {
      return raw;
    }
    if (isScalar(target) || isJacksonManaged(target)) {
      try {
        return BoosterJson.convert(raw, targetType);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(
            "Cannot convert " + describe(raw) + " to " + targetType.getTypeName() + ": " + e.getMessage(), e);
      }
    }
    if (target.isSealed()) {
      return convertSealed(raw, target);
    }
    if (target.isInterface() || Modifier.isAbstract(target.getModifiers())) {
      if (target.isInstance(raw)) {
        return raw;
      }
      throw new IllegalArgumentException("Cannot create an instance of the abstract type " + target.getName()
          + " from " + describe(raw));
    }
    return bindObject(target, targetType, asRawMap(raw, target));
  }

  /**
   * {@code createInstanceWithCalculatedProperties}: builds the instance, then returns a plain object (an ordered
   * map) with only the selected property paths, evaluating calculated fields (and waiting for them when they return
   * a {@link CompletionStage} or {@link Future}). Paths use dots for nested properties and {@code []} for arrays
   * ({@code friends[].id}). The rules of TypeScript apply: an absent leaf is omitted; an absent or {@code null}
   * nested object or array becomes {@code null}; a nested object with nothing selected becomes {@code null}; array
   * items with nothing selected are dropped; evaluation is sequential, in selection order.
   *
   * @param instanceClass the read model class
   * @param raw the raw read model
   * @param propertiesToInclude the selected paths ({@code ProjectionFor})
   * @param <T> the read model type
   * @return the projected plain object
   */
  public static <T> Map<String, Object> createInstanceWithCalculatedProperties(
      Class<T> instanceClass, Object raw, List<String> propertiesToInclude) {
    T instance = createInstance(instanceClass, raw);
    Map<String, Object> result = new LinkedHashMap<>();
    processProperties(instance, result, buildPropertiesMap(propertiesToInclude));
    return result;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Projection
  // ---------------------------------------------------------------------------------------------------------------

  /** One node of the selection tree ({@code {}}, a nested node, or {@code { __isArray, __children }}). */
  static final class PropertyNode {
    final boolean array;
    final Map<String, PropertyNode> children = new LinkedHashMap<>();
    final Map<String, PropertyNode> arrayChildren = new LinkedHashMap<>();

    PropertyNode(boolean array) {
      this.array = array;
    }

    boolean hasChildren() {
      return array || !children.isEmpty();
    }
  }

  static Map<String, PropertyNode> buildPropertiesMap(List<String> properties) {
    Map<String, PropertyNode> root = new LinkedHashMap<>();
    for (String property : properties) {
      Map<String, PropertyNode> current = root;
      for (String part : property.split("\\.", -1)) {
        boolean isArray = part.endsWith("[]");
        String key = isArray ? part.substring(0, part.length() - 2) : part;
        PropertyNode node = current.get(key);
        if (node == null) {
          node = new PropertyNode(isArray);
          current.put(key, node);
        } else if (isArray && !node.array) {
          throw new IllegalArgumentException("Invalid selection '" + property + "': '" + key
              + "' is selected both as an array and as a non-array property");
        }
        current = isArray ? node.arrayChildren : node.children;
      }
    }
    return root;
  }

  private static void processProperties(Object source, Map<String, Object> result, Map<String, PropertyNode> nodes) {
    for (Map.Entry<String, PropertyNode> entry : nodes.entrySet()) {
      String key = entry.getKey();
      PropertyNode node = entry.getValue();
      if (hasProperty(source, key)) {
        if (node.array) {
          List<Object> items = new ArrayList<>();
          result.put(key, items);
          Object value = await(readProperty(source, key));
          for (Object item : iterable(value, key)) {
            Map<String, Object> newItem = new LinkedHashMap<>();
            processProperties(item, newItem, node.arrayChildren);
            if (!newItem.isEmpty()) {
              items.add(newItem);
            }
          }
        } else if (!node.children.isEmpty()) {
          Object value = await(readProperty(source, key));
          if (value == null) {
            result.put(key, null);
          } else {
            Map<String, Object> nested = new LinkedHashMap<>();
            processProperties(value, nested, node.children);
            result.put(key, nested.isEmpty() ? null : nested);
          }
        } else {
          result.put(key, await(readProperty(source, key)));
        }
      } else if (node.hasChildren()) {
        result.put(key, null);
      }
    }
  }

  private static boolean hasProperty(Object source, String key) {
    return switch (source) {
      case null -> throw new IllegalArgumentException("Cannot read property '" + key + "' of null");
      case Map<?, ?> map -> map.containsKey(key);
      case String value -> throw new IllegalArgumentException("Cannot use 'in' operator to search for '" + key + "' in " + value);
      case Number value -> throw new IllegalArgumentException("Cannot use 'in' operator to search for '" + key + "' in " + value);
      case Boolean value -> throw new IllegalArgumentException("Cannot use 'in' operator to search for '" + key + "' in " + value);
      default -> ClassMetadata.of(source.getClass()).field(key).isPresent();
    };
  }

  private static Object readProperty(Object source, String key) {
    if (source instanceof Map<?, ?> map) {
      return map.get(key);
    }
    return ClassMetadata.of(source.getClass()).field(key).orElseThrow().readValue(source);
  }

  private static Iterable<?> iterable(Object value, String key) {
    if (value instanceof Iterable<?> iterable) {
      return iterable;
    }
    if (value != null && value.getClass().isArray()) {
      List<Object> items = new ArrayList<>();
      for (int i = 0; i < Array.getLength(value); i++) {
        items.add(Array.get(value, i));
      }
      return items;
    }
    throw new IllegalArgumentException("The property '" + key + "' is not iterable: " + describe(value));
  }

  /** TypeScript awaits any thenable; Java waits for futures. */
  private static Object await(Object value) {
    try {
      if (value instanceof CompletionStage<?> stage) {
        return stage.toCompletableFuture().join();
      }
      if (value instanceof Future<?> future) {
        return future.get();
      }
      return value;
    } catch (CompletionException e) {
      throw Exceptions.sneakyThrow(e.getCause() == null ? e : e.getCause());
    } catch (ExecutionException e) {
      throw Exceptions.sneakyThrow(e.getCause() == null ? e : e.getCause());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for a calculated property", e);
    }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Binding
  // ---------------------------------------------------------------------------------------------------------------

  private static Object absentValue(Class<?> target) {
    if (target == Optional.class) {
      return Optional.empty();
    }
    if (target == OptionalInt.class) {
      return OptionalInt.empty();
    }
    if (target == OptionalLong.class) {
      return OptionalLong.empty();
    }
    if (target == OptionalDouble.class) {
      return OptionalDouble.empty();
    }
    if (target.isPrimitive()) {
      return Array.get(Array.newInstance(target, 1), 0);
    }
    return null;
  }

  private static boolean isScalar(Class<?> target) {
    return target.isPrimitive()
        || target.isEnum()
        || Number.class.isAssignableFrom(target)
        || target == String.class
        || target == Boolean.class
        || target == Character.class
        || CharSequence.class.isAssignableFrom(target)
        || target == byte[].class
        || target.getPackageName().startsWith("java.time")
        || java.util.Date.class.isAssignableFrom(target)
        || target == java.util.UUID.class
        || target.getPackageName().startsWith("com.fasterxml.jackson")
        || target.getName().startsWith("java.") && !target.isInterface() && !Modifier.isAbstract(target.getModifiers())
            && target.getClassLoader() == null && !isPlainJdkCollectionLike(target);
  }

  private static boolean isPlainJdkCollectionLike(Class<?> target) {
    return Collection.class.isAssignableFrom(target) || Map.class.isAssignableFrom(target);
  }

  private static boolean isJacksonManaged(Class<?> target) {
    return BINDERS.get(target).jacksonManaged;
  }

  private static Object convertArray(Object raw, Type targetType, Class<?> target) {
    Type componentType = targetType instanceof java.lang.reflect.GenericArrayType generic
        ? generic.getGenericComponentType()
        : target.getComponentType();
    List<?> items = asList(raw, targetType);
    Object array = Array.newInstance(target.getComponentType(), items.size());
    for (int i = 0; i < items.size(); i++) {
      Array.set(array, i, convert(items.get(i), componentType));
    }
    return array;
  }

  private static Object convertCollection(Object raw, Type targetType, Class<?> target) {
    Type elementType = Types.typeArguments(targetType, Iterable.class)[0];
    List<?> items = asList(raw, targetType);
    Collection<Object> collection = newCollection(target);
    for (Object item : items) {
      collection.add(convert(item, elementType));
    }
    return collection;
  }

  @SuppressWarnings("unchecked")
  private static Collection<Object> newCollection(Class<?> target) {
    if (target.isInterface() || Modifier.isAbstract(target.getModifiers())) {
      if (NavigableSet.class.isAssignableFrom(target) || SortedSet.class.isAssignableFrom(target)) {
        return new TreeSet<>();
      }
      if (Set.class.isAssignableFrom(target)) {
        return new LinkedHashSet<>();
      }
      if (Deque.class.isAssignableFrom(target) || Queue.class.isAssignableFrom(target)) {
        return new ArrayDeque<>();
      }
      return new ArrayList<>();
    }
    try {
      return (Collection<Object>) target.getDeclaredConstructor().newInstance();
    } catch (ReflectiveOperationException e) {
      throw new IllegalArgumentException("Cannot create a collection of type " + target.getName(), e);
    }
  }

  private static List<?> asList(Object raw, Type targetType) {
    if (raw instanceof List<?> list) {
      return list;
    }
    if (raw instanceof Iterable<?> iterable) {
      List<Object> items = new ArrayList<>();
      iterable.forEach(items::add);
      return items;
    }
    if (raw.getClass().isArray()) {
      List<Object> items = new ArrayList<>();
      for (int i = 0; i < Array.getLength(raw); i++) {
        items.add(Array.get(raw, i));
      }
      return items;
    }
    throw new IllegalArgumentException("Cannot convert " + describe(raw) + " to " + targetType.getTypeName()
        + ": an array was expected");
  }

  @SuppressWarnings("unchecked")
  private static Object convertMap(Object raw, Type targetType, Class<?> target) {
    Type[] arguments = Types.typeArguments(targetType, Map.class);
    Map<?, ?> source = raw instanceof Map<?, ?> map ? map : BoosterJson.toRawMap(raw);
    Map<Object, Object> result;
    if (SortedMap.class.isAssignableFrom(target) || NavigableMap.class.isAssignableFrom(target)) {
      result = new TreeMap<>();
    } else if (target.isInterface() || Modifier.isAbstract(target.getModifiers()) || target == LinkedHashMap.class) {
      result = new LinkedHashMap<>();
    } else {
      try {
        result = (Map<Object, Object>) target.getDeclaredConstructor().newInstance();
      } catch (ReflectiveOperationException e) {
        result = new LinkedHashMap<>();
      }
    }
    boolean stringKeys = Types.rawClass(arguments[0]) == String.class || Types.rawClass(arguments[0]) == Object.class;
    for (Map.Entry<?, ?> entry : source.entrySet()) {
      Object key = stringKeys || entry.getKey() == null ? entry.getKey() : convert(entry.getKey(), arguments[0]);
      result.put(key, convert(entry.getValue(), arguments[1]));
    }
    return result;
  }

  private static Object convertSealed(Object raw, Class<?> target) {
    Map<String, Object> map = asRawMap(raw, target);
    List<Class<?>> candidates = new ArrayList<>();
    collectConcreteSubclasses(target, candidates);
    // The candidate must have every raw key; among those, the one with the fewest properties left unset wins
    Class<?> best = null;
    int bestUnset = Integer.MAX_VALUE;
    boolean ambiguous = false;
    for (Class<?> candidate : candidates) {
      Set<String> names = new HashSet<>(BINDERS.get(candidate).propertyNames());
      if (!names.containsAll(map.keySet())) {
        continue;
      }
      int unset = names.size() - map.size();
      if (unset < bestUnset) {
        best = candidate;
        bestUnset = unset;
        ambiguous = false;
      } else if (unset == bestUnset) {
        ambiguous = true;
      }
    }
    if (best == null || ambiguous) {
      throw new IllegalArgumentException("Cannot decide which implementation of " + target.getSimpleName()
          + " to create from " + describe(raw) + ". Candidates: "
          + candidates.stream().map(Class::getSimpleName).toList());
    }
    return convert(map, best);
  }

  private static void collectConcreteSubclasses(Class<?> type, List<Class<?>> result) {
    if (type.isSealed()) {
      for (Class<?> permitted : type.getPermittedSubclasses()) {
        collectConcreteSubclasses(permitted, result);
      }
    } else if (!type.isInterface() && !Modifier.isAbstract(type.getModifiers())) {
      result.add(type);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asRawMap(Object raw, Class<?> target) {
    if (raw instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    Object converted = BoosterJson.toRaw(raw);
    if (converted instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    throw new IllegalArgumentException("Cannot create an instance of " + target.getSimpleName() + " from "
        + describe(raw) + ": an object was expected");
  }

  private static Object bindObject(Class<?> target, Type targetType, Map<String, Object> raw) {
    Binder binder = BINDERS.get(target);
    if (binder.jacksonManaged) {
      return BoosterJson.convert(raw, targetType);
    }
    return binder.bind(raw, Types.bindings(targetType));
  }

  private static String describe(Object value) {
    if (value == null) {
      return "null";
    }
    String text;
    try {
      text = BoosterJson.toJson(value);
    } catch (RuntimeException e) {
      text = String.valueOf(value);
    }
    return text.length() > 200 ? text.substring(0, 200) + "..." : text;
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Per-class binding plans
  // ---------------------------------------------------------------------------------------------------------------

  /** How to bind raw maps to one class. */
  private static final class Binder {
    final Class<?> type;
    final boolean jacksonManaged;
    final Constructor<?> constructor;
    final List<Slot> constructorSlots;
    final List<FieldSlot> fieldSlots;

    private Binder(
        Class<?> type, boolean jacksonManaged, Constructor<?> constructor, List<Slot> constructorSlots,
        List<FieldSlot> fieldSlots) {
      this.type = type;
      this.jacksonManaged = jacksonManaged;
      this.constructor = constructor;
      this.constructorSlots = constructorSlots;
      this.fieldSlots = fieldSlots;
    }

    /** A constructor argument: the raw key it reads ({@code null} for "always absent") and its type. */
    record Slot(String key, Type type, Class<?> rawType) {}

    /** A field assigned after construction. */
    record FieldSlot(String key, Field field) {}

    static Binder of(Class<?> type) {
      if (hasJacksonAnnotations(type)) {
        return new Binder(type, true, null, List.of(), List.of());
      }
      if (type.isRecord()) {
        RecordComponent[] components = type.getRecordComponents();
        Class<?>[] parameterTypes = new Class<?>[components.length];
        List<Slot> slots = new ArrayList<>();
        for (int i = 0; i < components.length; i++) {
          parameterTypes[i] = components[i].getType();
          slots.add(new Slot(jsonName(components[i].getName(), components[i], componentField(type, components[i])),
              components[i].getGenericType(), components[i].getType()));
        }
        try {
          return new Binder(type, false, accessible(type.getDeclaredConstructor(parameterTypes)), slots, List.of());
        } catch (NoSuchMethodException e) {
          throw new IllegalStateException("The record " + type.getName() + " has no canonical constructor", e);
        }
      }
      if (type.isInterface() || Modifier.isAbstract(type.getModifiers()) || type.isEnum() || type.isPrimitive()
          || type.isArray()) {
        return new Binder(type, false, null, List.of(), List.of());
      }
      Map<String, Field> fields = new LinkedHashMap<>();
      Map<String, String> keysByField = new LinkedHashMap<>();
      for (PropertyMetadata property : ClassMetadata.of(type).dataFields()) {
        Field field = (Field) property.element();
        fields.put(field.getName(), field);
        keysByField.put(field.getName(), jsonName(field.getName(), field));
      }
      Constructor<?> chosen = null;
      List<Slot> slots = new ArrayList<>();
      List<Constructor<?>> constructors = new ArrayList<>();
      for (Constructor<?> candidate : type.getDeclaredConstructors()) {
        if (!candidate.isSynthetic()
            && List.of(candidate.getParameters()).stream().noneMatch(p -> p.isImplicit() || p.isSynthetic())) {
          constructors.add(candidate);
        }
      }
      if (constructors.isEmpty()) {
        throw new IllegalArgumentException("Cannot create instances of " + type.getName()
            + ": it has no usable constructor (inner classes that capture an enclosing instance cannot be created)");
      }
      constructors.sort(Comparator.comparingInt((Constructor<?> c) -> c.getParameterCount()).reversed());
      for (Constructor<?> candidate : constructors) {
        if (candidate.getParameterCount() > 0 && !candidate.isSynthetic()
            && List.of(candidate.getParameters()).stream()
                .allMatch(p -> p.isNamePresent() && fields.containsKey(p.getName()))) {
          chosen = candidate;
          for (Parameter parameter : candidate.getParameters()) {
            slots.add(new Slot(keysByField.get(parameter.getName()), parameter.getParameterizedType(), parameter.getType()));
          }
          break;
        }
      }
      if (chosen == null) {
        try {
          chosen = type.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
          // TypeScript `new Class()`: call the smallest constructor with absent arguments
          chosen = constructors.get(constructors.size() - 1);
          for (Parameter parameter : chosen.getParameters()) {
            slots.add(new Slot(null, parameter.getParameterizedType(), parameter.getType()));
          }
        }
      }
      Set<String> fromConstructor = new HashSet<>();
      if (!slots.isEmpty()) {
        for (Parameter parameter : chosen.getParameters()) {
          if (parameter.isNamePresent()) {
            fromConstructor.add(parameter.getName());
          }
        }
      }
      List<FieldSlot> fieldSlots = new ArrayList<>();
      for (Map.Entry<String, Field> entry : fields.entrySet()) {
        if (!fromConstructor.contains(entry.getKey())) {
          fieldSlots.add(new FieldSlot(keysByField.get(entry.getKey()), accessible(entry.getValue())));
        }
      }
      return new Binder(type, false, accessible(chosen), slots, fieldSlots);
    }

    Set<String> propertyNames() {
      Set<String> names = new LinkedHashSet<>();
      constructorSlots.stream().map(Slot::key).filter(Objects::nonNull).forEach(names::add);
      fieldSlots.stream().map(FieldSlot::key).forEach(names::add);
      return names;
    }

    Object bind(Map<String, Object> raw, Map<TypeVariable<?>, Type> bindings) {
      if (constructor == null) {
        throw new IllegalArgumentException("Cannot create an instance of " + type.getName());
      }
      Object[] arguments = new Object[constructorSlots.size()];
      for (int i = 0; i < arguments.length; i++) {
        Slot slot = constructorSlots.get(i);
        Object value = slot.key() == null ? null : raw.get(slot.key());
        arguments[i] = value == null ? absentValue(slot.rawType()) : convertProperty(value, slot.type(), bindings, slot.key());
      }
      Object instance;
      try {
        instance = constructor.newInstance(arguments);
      } catch (InvocationTargetException e) {
        throw Exceptions.sneakyThrow(e.getCause());
      } catch (ReflectiveOperationException e) {
        throw new IllegalArgumentException("Cannot create an instance of " + type.getName(), e);
      }
      for (FieldSlot slot : fieldSlots) {
        if (!raw.containsKey(slot.key())) {
          continue;
        }
        Field field = slot.field();
        if (Modifier.isFinal(field.getModifiers())) {
          throw new IllegalArgumentException("Cannot set the final field " + type.getSimpleName() + "." + field.getName()
              + ": declare a constructor whose parameter names match the fields (compile with -parameters), "
              + "make the field non-final, or use a record");
        }
        Object value = raw.get(slot.key());
        try {
          field.set(instance, value == null ? absentValue(field.getType())
              : convertProperty(value, field.getGenericType(), bindings, slot.key()));
        } catch (IllegalAccessException e) {
          throw new IllegalArgumentException("Cannot set the field " + type.getSimpleName() + "." + field.getName(), e);
        }
      }
      return instance;
    }

    private Object convertProperty(Object value, Type declared, Map<TypeVariable<?>, Type> bindings, String key) {
      try {
        return convert(value, Types.resolve(declared, bindings));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Invalid value for " + type.getSimpleName() + "." + key + ": " + e.getMessage(), e);
      }
    }

    private static Field componentField(Class<?> record, RecordComponent component) {
      try {
        return record.getDeclaredField(component.getName());
      } catch (NoSuchFieldException e) {
        return null;
      }
    }

    private static String jsonName(String name, java.lang.reflect.AnnotatedElement... elements) {
      for (java.lang.reflect.AnnotatedElement element : elements) {
        if (element == null) {
          continue;
        }
        JsonProperty property = element.getAnnotation(JsonProperty.class);
        if (property != null && !property.value().isEmpty()) {
          return property.value();
        }
      }
      return name;
    }

    private static boolean hasJacksonAnnotations(Class<?> type) {
      if (type.isAnnotationPresent(JsonTypeInfo.class) || type.isAnnotationPresent(JsonDeserialize.class)) {
        return true;
      }
      for (Constructor<?> constructor : type.getDeclaredConstructors()) {
        if (constructor.isAnnotationPresent(JsonCreator.class)) {
          return true;
        }
      }
      for (Method method : type.getDeclaredMethods()) {
        if (method.isAnnotationPresent(JsonCreator.class) || method.isAnnotationPresent(JsonValue.class)) {
          return true;
        }
      }
      for (Field field : type.getDeclaredFields()) {
        if (field.isAnnotationPresent(JsonValue.class)) {
          return true;
        }
      }
      if (type.isRecord()) {
        for (RecordComponent component : type.getRecordComponents()) {
          if (component.isAnnotationPresent(JsonValue.class)) {
            return true;
          }
        }
      }
      return false;
    }

    private static <A extends AccessibleObject> A accessible(A object) {
      object.trySetAccessible();
      return object;
    }
  }
}
