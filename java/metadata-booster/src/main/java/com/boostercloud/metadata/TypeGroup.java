package com.boostercloud.metadata;

/**
 * Classification of a type, with exactly the same taxonomy as the TypeScript {@code TypeGroup} union of
 * {@code @boostercloud/metadata-booster} ({@code 'String' | 'Number' | ... | 'Other'}).
 *
 * <p>The TypeScript literal of every group is available through {@link #tsName()} (and {@link #toString()}), so
 * code ported from TypeScript that compares {@code typeGroup === 'Class'} can compare
 * {@code typeGroup == TypeGroup.CLASS} or {@code typeGroup.tsName().equals("Class")}.
 *
 * <p>How Java types are classified is documented on {@link TypeMetadata}. {@link #INTERSECTION} and
 * {@link #READONLY_ARRAY} exist for parity with TypeScript but are never produced by the Java reflection reader:
 * Java has no intersection types for declarations and cannot tell read-only lists from mutable ones.
 */
public enum TypeGroup {
  /** {@code String}, {@code char}, {@code Character}, {@code CharSequence} and {@code byte[]} (base64 in JSON). */
  STRING("String"),
  /** Every Java numeric type (primitives, boxes, {@link java.math.BigDecimal}, any {@link Number}). */
  NUMBER("Number"),
  /** {@code boolean} and {@link Boolean}. */
  BOOLEAN("Boolean"),
  /** Java enums. {@link TypeMetadata#parameters()} lists the constants. */
  ENUM("Enum"),
  /** Sealed interfaces and sealed classes. {@link TypeMetadata#parameters()} lists the permitted subclasses. */
  UNION("Union"),
  /** Never produced in Java (kept for parity with TypeScript). */
  INTERSECTION("Intersection"),
  /** Functional interfaces ({@code java.util.function.*}, {@link Runnable}, {@code @FunctionalInterface} types). */
  FUNCTION("Function"),
  /** Records and (non-sealed, non-generic or raw) classes. */
  CLASS("Class"),
  /** Non-sealed, non-functional interfaces, and date types ({@code Date} is an interface in TypeScript). */
  INTERFACE("Interface"),
  /** {@link java.util.Map} and its subtypes, described as the TypeScript alias {@code Record<K, V>}. */
  TYPE("Type"),
  /** {@link java.util.List}, {@link java.util.Set}, any {@link java.util.Collection}/{@link Iterable}, and arrays. */
  ARRAY("Array"),
  /** Instantiated generic classes ({@code Page<Cart>}) and futures, described as {@code Promise<T>}. */
  OBJECT("Object"),
  /** Never produced in Java (kept for parity with TypeScript). */
  READONLY_ARRAY("ReadonlyArray"),
  /** {@code Object}, {@code void}, unresolved type variables and wildcards, and types deeper than the depth limit. */
  OTHER("Other");

  private final String tsName;

  TypeGroup(String tsName) {
    this.tsName = tsName;
  }

  /**
   * @return the TypeScript literal of this group, e.g. {@code "ReadonlyArray"}
   */
  public String tsName() {
    return tsName;
  }

  /**
   * Finds a group by its TypeScript literal.
   *
   * @param tsName a TypeScript literal such as {@code "Class"}
   * @return the matching group
   * @throws IllegalArgumentException when the literal is unknown
   */
  public static TypeGroup fromTsName(String tsName) {
    for (TypeGroup group : values()) {
      if (group.tsName.equals(tsName)) {
        return group;
      }
    }
    throw new IllegalArgumentException("Unknown type group '" + tsName + "'");
  }

  @Override
  public String toString() {
    return tsName;
  }
}
