package com.boostercloud.metadata;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One property of a class: a record component, a declared instance field, a calculated field (a no-argument
 * method annotated with an annotation whose simple name is {@code CalculatedField}), or a method parameter (inside
 * {@link MethodMetadata#parameters()}). Java counterpart of the TypeScript {@code PropertyMetadata}.
 *
 * @param name the property name (record component, field, method or parameter name)
 * @param typeInfo the type of the property; {@link TypeMetadata#isGetAccessor()} is true for calculated fields
 * @param dependencies names of the properties a calculated field depends on (from the annotation's
 *     {@code dependsOn} attribute when present). Empty for every other property. In TypeScript this list is only
 *     filled by the {@code @ReadModel} decorator; core may replace it with {@link #withDependencies(List)}
 * @param kind where the property comes from
 * @param element the reflective element: a {@link RecordComponent}, a {@link Field}, a {@link Method} (calculated
 *     field) or a {@link Parameter}
 */
public record PropertyMetadata(
    String name, TypeMetadata typeInfo, List<String> dependencies, Kind kind, AnnotatedElement element)
    implements MemberMetadata {

  /** Where a property comes from. */
  public enum Kind {
    /** A record component, read through its accessor. */
    RECORD_COMPONENT,
    /** A declared, non-static, non-transient instance field of a class or of one of its superclasses. */
    FIELD,
    /** A calculated field: a non-static, no-argument method annotated as {@code CalculatedField}. */
    CALCULATED,
    /** A method parameter (only inside {@link MethodMetadata#parameters()}). */
    PARAMETER
  }

  /** Canonical constructor; copies {@code dependencies} into an immutable list. */
  public PropertyMetadata {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(typeInfo, "typeInfo");
    Objects.requireNonNull(kind, "kind");
    dependencies = List.copyOf(dependencies);
  }

  /**
   * @param newDependencies the dependency list
   * @return a copy with the given dependencies
   */
  public PropertyMetadata withDependencies(List<String> newDependencies) {
    return new PropertyMetadata(name, typeInfo, newDependencies, kind, element);
  }

  /**
   * @return whether this is a calculated field (same as {@code typeInfo().isGetAccessor()})
   */
  public boolean isCalculated() {
    return kind == Kind.CALCULATED;
  }

  /**
   * Reads the value of this property from an instance, without any conversion. Calculated fields are evaluated
   * (their method is invoked). Exceptions thrown by an accessor or calculated method propagate unchanged.
   *
   * @param instance an instance of the declaring class
   * @return the current value
   * @throws IllegalStateException for method parameters, which have no value
   */
  public Object readValue(Object instance) {
    Objects.requireNonNull(instance, "instance");
    try {
      return switch (element) {
        case RecordComponent component -> MetadataExtractors.accessible(component.getAccessor()).invoke(instance);
        case Field field -> MetadataExtractors.accessible(field).get(instance);
        case Method method -> MetadataExtractors.accessible(method).invoke(instance);
        default -> throw new IllegalStateException("The property '" + name + "' has no readable value");
      };
    } catch (InvocationTargetException e) {
      throw MetadataExtractors.sneakyThrow(e.getCause());
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("Could not read property '" + name + "' of " + instance.getClass().getName(), e);
    }
  }

  /**
   * Finds an annotation on any element that represents this property: for record components that is the
   * component, its backing field, its accessor method and the matching canonical-constructor parameter (Java
   * propagates a component annotation to some of them depending on its {@code @Target}).
   *
   * @param annotationType the annotation class
   * @param <A> the annotation type
   * @return the annotation, or {@code null} when absent
   */
  public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
    for (AnnotatedElement candidate : relatedElements()) {
      A annotation = candidate.getAnnotation(annotationType);
      if (annotation != null) {
        return annotation;
      }
    }
    return null;
  }

  /**
   * Same as {@link #getAnnotation(Class)} but matches the annotation by simple name, so modules that cannot depend
   * on the annotation class (e.g. this one, for {@code @Nullable}) can still detect it.
   *
   * @param simpleName the simple name of the annotation type, e.g. {@code "NonExposed"}
   * @return whether an annotation with that simple name is present
   */
  public boolean hasAnnotationNamed(String simpleName) {
    for (AnnotatedElement candidate : relatedElements()) {
      if (MetadataExtractors.hasAnnotationNamed(candidate, simpleName)) {
        return true;
      }
    }
    return false;
  }

  /**
   * @return every reflective element that represents this property (see {@link #getAnnotation(Class)})
   */
  public List<AnnotatedElement> relatedElements() {
    List<AnnotatedElement> elements = new ArrayList<>();
    if (element == null) {
      return elements;
    }
    elements.add(element);
    if (element instanceof RecordComponent component) {
      elements.addAll(MetadataExtractors.recordComponentRelatedElements(component));
    }
    return elements;
  }

  @Override
  public String toString() {
    return "PropertyMetadata{name=" + name + ", kind=" + kind + ", typeInfo=" + typeInfo
        + (dependencies.isEmpty() ? "" : ", dependencies=" + dependencies) + "}";
  }
}
