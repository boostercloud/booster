package com.boostercloud.metadata;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Description of a class: its name, its properties and its methods. Java counterpart of the TypeScript
 * {@code ClassMetadata} that the {@code @boostercloud/metadata-booster} compiler transformer attaches to every class
 * under the {@code booster:typeinfo} key; here it is computed by reflection on first use and cached.
 *
 * <p>Field order is deterministic:
 *
 * <ul>
 *   <li>records: the record components in declaration order;
 *   <li>classes: the declared instance fields of the root-most superclass first, then each subclass, each in
 *       declaration order (read from the class file; static, synthetic and {@code transient} fields are skipped);
 *   <li>then, in both cases, the calculated fields (methods annotated {@code @CalculatedField}), superclass first,
 *       in declaration order.
 * </ul>
 *
 * <p>Methods are the class's own declared methods (static and instance, like TypeScript's {@code getMethods()}),
 * in declaration order, excluding synthetic/bridge methods, record accessors, the record's generated
 * {@code equals}/{@code hashCode}/{@code toString}, the enum's {@code values}/{@code valueOf} and calculated fields.
 *
 * @param name the simple class name ({@link Class#getSimpleName()}), used by Booster as registry key and GraphQL
 *     type name
 * @param type the described class
 * @param fields the properties, in the order described above
 * @param methods the declared methods
 */
public record ClassMetadata(String name, Class<?> type, List<PropertyMetadata> fields, List<MethodMetadata> methods) {

  /** Canonical constructor; copies the lists into immutable lists. */
  public ClassMetadata {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(type, "type");
    fields = List.copyOf(fields);
    methods = List.copyOf(methods);
  }

  /**
   * Returns the (cached) metadata of a class. This is the replacement of TypeScript's
   * {@code Reflect.getMetadata('booster:typeinfo', classType)}; unlike TypeScript it is available for every class.
   *
   * @param type a record, class, enum or interface
   * @return its metadata
   * @throws IllegalArgumentException for primitives, arrays and anonymous classes (which have no name)
   */
  public static ClassMetadata of(Class<?> type) {
    return MetadataExtractors.classMetadataOf(type);
  }

  /**
   * @param fieldName a property name
   * @return the property with that name
   */
  public Optional<PropertyMetadata> field(String fieldName) {
    return fields.stream().filter(field -> field.name().equals(fieldName)).findFirst();
  }

  /**
   * @param methodName a method name
   * @return the first declared method with that name (Java allows overloads, TypeScript does not)
   */
  public Optional<MethodMetadata> method(String methodName) {
    return methods.stream().filter(method -> method.name().equals(methodName)).findFirst();
  }

  /**
   * @param methodName a method name
   * @return every declared method with that name, in declaration order
   */
  public List<MethodMetadata> methods(String methodName) {
    return methods.stream().filter(method -> method.name().equals(methodName)).toList();
  }

  /**
   * @return the stored properties (record components and fields), without calculated fields
   */
  public List<PropertyMetadata> dataFields() {
    return fields.stream().filter(field -> !field.isCalculated()).toList();
  }

  /**
   * @return the calculated fields only
   */
  public List<PropertyMetadata> calculatedFields() {
    return fields.stream().filter(PropertyMetadata::isCalculated).toList();
  }

  /**
   * @return whether the described class is a record
   */
  public boolean isRecord() {
    return type.isRecord();
  }
}
