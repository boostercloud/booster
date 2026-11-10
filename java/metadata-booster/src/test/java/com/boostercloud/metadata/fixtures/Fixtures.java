package com.boostercloud.metadata.fixtures;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Java port of {@code packages/metadata-booster/test/test.ts}: the same set of shapes (a class with a set of
 * itself, an enum, a class with a map, an enum field and a promise method, a generic class, an inheritance chain and
 * a class with arrays, unions, functions, unknowns, records and optional variants), expressed with their closest
 * Java equivalents.
 */
public final class Fixtures {
  private Fixtures() {}

  /** Any annotation called {@code Nullable} is honoured; this one is used by the tests. */
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
  public @interface Nullable {}

  /** Stand-in for core's {@code @CalculatedField}. */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  public @interface CalculatedField {
    String[] dependsOn() default {};
  }

  /** Another annotation used to test annotation lookup on record components. */
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.FIELD})
  public @interface FieldOnly {}

  /** TS: {@code class User { name: string; friends: Set<User> }}. */
  public static class User {
    public String name;
    public Set<User> friends;
  }

  /** TS: {@code enum Size { Small, Medium, Big }}. */
  public enum Size {
    SMALL,
    MEDIUM,
    BIG;

    public String lower() {
      return name().toLowerCase();
    }
  }

  /** TS: {@code class Car { driversMap: Map<string, User>; size: Size; engageAutoPilot(): Promise<boolean> }}. */
  public static class Car {
    public Map<String, User> driversMap;
    public Size size;

    public CompletableFuture<Boolean> engageAutoPilot() {
      return CompletableFuture.completedFuture(true);
    }
  }

  /** TS: {@code class GenericClass<T> { genericValue: T }}. */
  public static class GenericClass<T> {
    public T genericValue;
  }

  /** A subclass that binds the type variable. */
  public static class StringBox extends GenericClass<String> {
    public int size;
  }

  /** TS: {@code class Animal { animalProp: string }}. */
  public static class Animal {
    protected String animalProp;
  }

  /** TS: {@code class Dog extends Animal { dogProp: string }}. */
  public static class Dog extends Animal {
    private String dogProp;
  }

  /** TS: {@code class Chihuahua extends Dog {}}. */
  public static class Chihuahua extends Dog {}

  /** A sealed hierarchy: the Java counterpart of an aliased TypeScript union of classes. */
  public sealed interface Vehicle permits Bike, Truck {}

  /** Union member. */
  public record Bike(int wheels) implements Vehicle {}

  /** Union member. */
  public record Truck(double load) implements Vehicle {}

  /** A non-sealed interface. */
  public interface Shape {}

  /** TS: the {@code Test} class with every shape. */
  public static class Test {
    public String[] array0;
    public List<String> array1;
    public Vehicle union;
    public Function<String, Integer> func0;
    public Object any0;
    public Map<String, Object> record;
    public GenericClass<String> generic;
    public Chihuahua inheritedProps;
    public Optional<String> optionalString;
    @Nullable public List<String> nullableList;
    public Optional<List<String>> optionalList;
    public List<@TypeUse.Nullable String> listOfNullable;
    public @TypeUse.Nullable String typeUseNullable;
    public Instant createdAt;
    public LocalDate day;
    public byte[] bytes;
    public int[][] matrix;
    public List<? extends User> covariantUsers;
    public List<?> unknownList;
    public Shape shape;
    public static String ignoredStatic;
    public transient String ignoredTransient;
  }

  /** A record with a calculated field and a static handler. */
  public record PersonReadModel(String id, String firstName, String lastName, @Nullable List<String> tags) {
    @CalculatedField(dependsOn = {"firstName", "lastName"})
    public String fullName() {
      return firstName + " " + lastName;
    }

    @CalculatedField
    public List<String> initials() {
      return List.of(firstName.substring(0, 1), lastName.substring(0, 1));
    }

    public static CompletableFuture<Boolean> handle(PersonReadModel model, String reason) {
      return CompletableFuture.completedFuture(true);
    }

    public static void noResult() {}

    public String greeting() {
      return "Hello " + firstName;
    }
  }

  /** A record whose accessor throws, to test exception propagation. */
  public record Exploding(String value) {
    @Override
    public String value() {
      throw new IllegalStateException("boom");
    }
  }

  /** A record with an annotation that only applies to fields. */
  public record Annotated(@FieldOnly String marked, String plain) {}

  /** Fields declared in a deliberately non-alphabetical order. */
  public static class Ordered {
    public String zeta;
    public String alpha;
    public String mike;
    public String bravo;

    public void third() {}

    public void first() {}

    public void second() {}
  }

  /** Nested lists, deeper than the depth limit. */
  public static class Deep {
    public List<List<List<List<List<List<List<List<List<List<String>>>>>>>>>> deep;
  }

  /** A class with an invalid calculated field. */
  public static class InvalidCalculated {
    @CalculatedField
    public static String nope() {
      return "";
    }
  }

  /** A class with a calculated field that throws. */
  public static class ThrowingCalculated {
    private final String value = "v";

    @CalculatedField
    public String broken() {
      throw new UnsupportedOperationException("calculated failure " + value);
    }
  }
}
