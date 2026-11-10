package com.boostercloud.metadata;

import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Returns the declared fields and methods of a class in source declaration order.
 *
 * <p>{@link Class#getDeclaredFields()} and {@link Class#getDeclaredMethods()} do not guarantee any order, but the
 * GraphQL schema (field order of types, filters and sort inputs) depends on it. javac writes fields and methods to
 * the class file in declaration order, so the order is read from the class file with the {@code java.lang.classfile}
 * API. When the class file is not available (generated or hidden classes) fields keep the reflection order and
 * methods are sorted by name and descriptor, which is deterministic.
 */
final class DeclarationOrder {

  private static final ClassValue<Order> ORDERS =
      new ClassValue<>() {
        @Override
        protected Order computeValue(Class<?> type) {
          return readOrder(type);
        }
      };

  private DeclarationOrder() {}

  private record Order(Map<String, Integer> fields, Map<String, Integer> methods) {}

  static List<Field> fields(Class<?> type) {
    Field[] declared = type.getDeclaredFields();
    Order order = ORDERS.get(type);
    List<Field> result = new ArrayList<>(Arrays.asList(declared));
    if (order != null) {
      result.sort(Comparator.comparingInt(field -> order.fields().getOrDefault(field.getName(), Integer.MAX_VALUE)));
    }
    return result;
  }

  static List<Method> methods(Class<?> type) {
    Method[] declared = type.getDeclaredMethods();
    Order order = ORDERS.get(type);
    List<Method> result = new ArrayList<>(Arrays.asList(declared));
    Comparator<Method> byNameAndDescriptor =
        Comparator.comparing(Method::getName).thenComparing(DeclarationOrder::descriptor);
    if (order == null) {
      result.sort(byNameAndDescriptor);
    } else {
      result.sort(
          Comparator.<Method>comparingInt(
                  method -> order.methods().getOrDefault(method.getName() + descriptor(method), Integer.MAX_VALUE))
              .thenComparing(byNameAndDescriptor));
    }
    return result;
  }

  static String descriptor(Method method) {
    return MethodType.methodType(method.getReturnType(), method.getParameterTypes()).toMethodDescriptorString();
  }

  private static Order readOrder(Class<?> type) {
    if (type.isHidden() || type.isPrimitive() || type.isArray()) {
      return null;
    }
    String binaryName = type.getName();
    String resource = binaryName.substring(binaryName.lastIndexOf('.') + 1) + ".class";
    try (InputStream input = type.getResourceAsStream(resource)) {
      if (input == null) {
        return null;
      }
      ClassModel model = ClassFile.of().parse(input.readAllBytes());
      Map<String, Integer> fields = new HashMap<>();
      int index = 0;
      for (FieldModel field : model.fields()) {
        fields.putIfAbsent(field.fieldName().stringValue(), index++);
      }
      Map<String, Integer> methods = new HashMap<>();
      index = 0;
      for (MethodModel method : model.methods()) {
        methods.putIfAbsent(method.methodName().stringValue() + method.methodType().stringValue(), index++);
      }
      return new Order(fields, methods);
    } catch (IOException | IllegalArgumentException e) {
      return null;
    }
  }
}
