package com.boostercloud.integration.common;

import com.boostercloud.framework.types.BoosterJson;
import com.boostercloud.framework.types.concepts.CommandMetadata;
import com.boostercloud.framework.types.concepts.JoinKey;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.metadata.MethodMetadata;
import com.boostercloud.metadata.PropertyMetadata;
import com.boostercloud.metadata.TypeMetadata;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JavaScript's {@code JSON.stringify} for the values the application writes into messages and logs, above all the
 * framework metadata the global error handler appends to its errors ({@code JSON.stringify(commandMetadata)}).
 *
 * <p>The TypeScript metadata objects hold functions (the concept class, the authorizer, the before hooks), which
 * {@code JSON.stringify} omits from objects and writes as {@code null} inside arrays. The Java metadata records hold
 * their counterparts: {@link Class}es, reflective members and types, and instances of functional interfaces
 * (authorizers, before hooks, join-key functions). This class treats those as functions, renders the metadata records
 * with the TypeScript shapes and orders (a command's {@code before}, {@code properties}, {@code methods}; properties and
 * methods as {@code {name, typeInfo}}, type information as
 * {@code {name, typeGroup, isNullable, isGetAccessor, parameters, importPath?, typeName?}}, an entity-property join
 * key as its name) and leaves out the Java-only parts (the reflective element, the precise Java type). Records are
 * written from their components, {@code null} components omitted (TypeScript's {@code undefined}); anything else goes
 * through {@link BoosterJson}.
 *
 * <p>For {@code ChangeCartItem} this gives
 * {@code {"before":[null,null],"properties":[{"name":"cartId","typeInfo":{"name":"UUID","typeGroup":"Class",...}},...],
 * "methods":[{"name":"beforeFn",...},{"name":"beforeFnV2",...},{"name":"handle",...}]}} (pinned by
 * {@code JsonStringifyTest}); the type names are those of the Java metadata ({@code Record<string, unknown>} for the
 * hooks' {@code Map}s, {@code void} for {@code handle}, the Java package as {@code importPath}).
 */
public final class JsonStringify {

  /** What {@code JSON.stringify} skips: functions (and {@code undefined}). */
  private static final Object UNDEFINED = new Object();

  private JsonStringify() {}

  /**
   * @param value the value
   * @return its JSON, or {@code undefined} for {@code null} (Java's stand-in for {@code undefined}) and functions
   */
  public static String stringify(Object value) {
    Object js = toJs(value);
    if (js == UNDEFINED || value == null) {
      return "undefined";
    }
    return BoosterJson.toJson(js);
  }

  private static Object toJs(Object value) {
    if (value == null) {
      return null;
    }
    if (isFunction(value)) {
      return UNDEFINED;
    }
    if (value instanceof String || value instanceof Number || value instanceof Boolean) {
      return value;
    }
    if (value instanceof Enum<?> constant) {
      return constant.toString();
    }
    if (value instanceof UUID uuid) {
      return uuid.value();
    }
    if (value instanceof JoinKey.EntityProperty property) {
      return property.propertyName();
    }
    if (value instanceof CommandMetadata command) {
      // the key order of the object the TypeScript @Command decorator builds: class, authorizer, before, properties,
      // methods (class and authorizer are functions)
      Map<String, Object> result = new LinkedHashMap<>();
      putDefined(result, "before", toJs(command.before()));
      putDefined(result, "properties", toJs(command.properties()));
      putDefined(result, "methods", toJs(command.methods()));
      return result;
    }
    if (value instanceof TypeMetadata type) {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("name", type.name());
      result.put("typeGroup", type.typeGroup().tsName());
      result.put("isNullable", type.isNullable());
      result.put("isGetAccessor", type.isGetAccessor());
      result.put("parameters", toJs(type.parameters()));
      putDefined(result, "importPath", type.importPath());
      putDefined(result, "typeName", type.typeName());
      return result;
    }
    if (value instanceof PropertyMetadata property) {
      return nameAndType(property.name(), property.typeInfo());
    }
    if (value instanceof MethodMetadata method) {
      return nameAndType(method.name(), method.typeInfo());
    }
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> result = new LinkedHashMap<>();
      map.forEach((key, entry) -> putDefined(result, String.valueOf(key), toJs(entry)));
      return result;
    }
    if (value instanceof Collection<?> collection) {
      return elements(collection);
    }
    if (value.getClass().isArray()) {
      List<Object> items = new ArrayList<>();
      for (int i = 0; i < Array.getLength(value); i++) {
        items.add(Array.get(value, i));
      }
      return elements(items);
    }
    if (value instanceof Record) {
      Map<String, Object> result = new LinkedHashMap<>();
      for (RecordComponent component : value.getClass().getRecordComponents()) {
        try {
          java.lang.reflect.Method accessor = component.getAccessor();
          accessor.setAccessible(true);
          putDefined(result, component.getName(), toJs(accessor.invoke(value)));
        } catch (ReflectiveOperationException | RuntimeException e) {
          // an inaccessible component is left out, like a non-enumerable property
        }
      }
      return result;
    }
    try {
      return BoosterJson.toRaw(value);
    } catch (RuntimeException e) {
      return String.valueOf(value);
    }
  }

  private static Map<String, Object> nameAndType(String name, TypeMetadata typeInfo) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("name", name);
    putDefined(result, "typeInfo", toJs(typeInfo));
    return result;
  }

  private static List<Object> elements(Collection<?> values) {
    List<Object> result = new ArrayList<>();
    for (Object element : values) {
      Object js = toJs(element);
      result.add(js == UNDEFINED ? null : js);
    }
    return result;
  }

  private static void putDefined(Map<String, Object> target, String key, Object value) {
    if (value != null && value != UNDEFINED) {
      target.put(key, value);
    }
  }

  /**
   * Classes, reflective members and types, lambdas and instances of functional interfaces are the Java counterparts
   * of JavaScript functions.
   */
  private static boolean isFunction(Object value) {
    if (value instanceof Class<?> || value instanceof AnnotatedElement || value instanceof Type) {
      return true;
    }
    Class<?> type = value.getClass();
    if (type.isSynthetic() || type.isHidden()) {
      return true;
    }
    for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
      for (Class<?> implemented : current.getInterfaces()) {
        if (isFunctionalInterface(implemented)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isFunctionalInterface(Class<?> type) {
    if (type.isAnnotationPresent(FunctionalInterface.class)) {
      return true;
    }
    for (Class<?> parent : type.getInterfaces()) {
      if (isFunctionalInterface(parent)) {
        return true;
      }
    }
    return false;
  }
}
