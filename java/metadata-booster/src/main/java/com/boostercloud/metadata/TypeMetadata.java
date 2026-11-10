package com.boostercloud.metadata;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;

/**
 * Description of one type (of a property, a method return value or a type argument). Java counterpart of the
 * TypeScript {@code TypeMetadata} interface, with the same field names and meaning.
 *
 * <p>The values follow the TypeScript transformer so that code ported from {@code framework-core} (GraphQL schema
 * generation, filters, sorting) can keep its rules unchanged:
 *
 * <table>
 *   <caption>Java type to metadata</caption>
 *   <tr><th>Java type</th><th>{@code name}</th><th>{@code typeGroup}</th><th>{@code typeName}</th></tr>
 *   <tr><td>{@code String}, {@code char}, {@code byte[]}</td><td>{@code string}</td><td>String</td><td>{@code String}</td></tr>
 *   <tr><td>any numeric type</td><td>{@code number}</td><td>Number</td><td>{@code Number}</td></tr>
 *   <tr><td>{@code boolean}</td><td>{@code boolean}</td><td>Boolean</td><td>{@code Boolean}</td></tr>
 *   <tr><td>{@code Instant}, {@code java.util.Date}, {@code OffsetDateTime}, {@code ZonedDateTime}</td>
 *       <td>{@code Date}</td><td>Interface</td><td>absent</td></tr>
 *   <tr><td>record / class {@code Cart}</td><td>{@code Cart}</td><td>Class</td><td>{@code Cart}</td></tr>
 *   <tr><td>enum {@code Size}</td><td>{@code Size}</td><td>Enum</td><td>{@code Size}</td></tr>
 *   <tr><td>sealed interface {@code Vehicle}</td><td>{@code Vehicle}</td><td>Union</td><td>absent</td></tr>
 *   <tr><td>{@code List<String>}, {@code Set<T>}, {@code T[]}</td><td>{@code string[]}</td><td>Array</td><td>{@code Array}</td></tr>
 *   <tr><td>{@code Map<String, Cart>}</td><td>{@code Record<string, Cart>}</td><td>Type</td><td>absent</td></tr>
 *   <tr><td>{@code Page<Cart>} (generic user class)</td><td>{@code Page<Cart>}</td><td>Object</td><td>{@code Page}</td></tr>
 *   <tr><td>{@code CompletableFuture<Boolean>}</td><td>{@code Promise<boolean>}</td><td>Object</td><td>{@code Promise}</td></tr>
 *   <tr><td>{@code Object}</td><td>{@code unknown}</td><td>Other</td><td>absent</td></tr>
 *   <tr><td>{@code void}</td><td>{@code void}</td><td>Other</td><td>absent</td></tr>
 *   <tr><td>unresolved type variable {@code T}</td><td>{@code T}</td><td>Other</td><td>absent</td></tr>
 * </table>
 *
 * <p>{@code Optional<T>} is unwrapped: the metadata describes {@code T} with {@code isNullable = true}.
 *
 * @param name the TypeScript-compatible text of the type ({@code string}, {@code Cart[]}, {@code Page<Cart>}); the
 *     GraphQL generator derives type names from it
 * @param typeGroup the classification of the type
 * @param typeName {@code String}/{@code Number}/{@code Boolean} for the basic groups, the simple class name for
 *     Enum/Class/Object, {@code Array} for arrays, {@code null} (TypeScript: absent) for Union, Interface, Type,
 *     Function and Other
 * @param parameters type arguments (array element type, map key and value, generic arguments), enum constants for
 *     {@link TypeGroup#ENUM}, permitted subclasses for {@link TypeGroup#UNION}
 * @param isNullable whether the value may be absent ({@code Optional} or {@code @Nullable}, see the module README)
 * @param isGetAccessor whether the property is a calculated field (an annotated method instead of stored data)
 * @param importPath Java package of the described class for class-like groups, {@code null} otherwise. Used with
 *     {@link #isExternal()}, which replaces the TypeScript "imported from a package" rule
 * @param type the runtime class, present exactly when {@code typeName} is present (as in TypeScript)
 * @param javaType the precise reflective type the metadata was computed from (Java-only addition, never null
 *     except for enum constants, which have no type of their own)
 */
public record TypeMetadata(
    String name,
    TypeGroup typeGroup,
    String typeName,
    List<TypeMetadata> parameters,
    boolean isNullable,
    boolean isGetAccessor,
    String importPath,
    Class<?> type,
    Type javaType) {

  /** Canonical constructor; copies {@code parameters} into an immutable list. */
  public TypeMetadata {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(typeGroup, "typeGroup");
    parameters = List.copyOf(parameters);
  }

  /**
   * Describes an arbitrary reflective type (a field type, a return type, a type argument) with no declaration
   * context: it is non-nullable unless it is an {@code Optional} or carries a type-use {@code @Nullable}.
   *
   * @param type the type to describe
   * @return its metadata
   */
  public static TypeMetadata of(Type type) {
    return MetadataExtractors.typeMetadataOf(type);
  }

  /**
   * Returns whether the type comes from outside the application: the JDK, Booster itself
   * ({@code com.boostercloud.framework.*}, {@code com.boostercloud.rocket.*}), Jackson or graphql-java. This is the
   * Java replacement for TypeScript's {@code isExternalType} ("{@code importPath} does not start with {@code .}").
   * External classes are not expanded into GraphQL object types.
   *
   * @return true when the described class is external
   */
  public boolean isExternal() {
    return importPath != null && ExternalTypes.isExternalPackage(importPath, type);
  }

  /**
   * @return the raw class of {@link #javaType()}, or {@code null} for unresolved type variables and enum constants
   */
  public Class<?> rawClass() {
    return javaType == null ? null : MetadataExtractors.rawClassOf(javaType);
  }

  /**
   * @param index position of the parameter
   * @return the type argument (or enum constant / union member) at {@code index}
   */
  public TypeMetadata parameter(int index) {
    return parameters.get(index);
  }

  /**
   * @return whether this describes a future ({@code typeName == "Promise"}), whose result type is parameter 0
   */
  public boolean isPromise() {
    return "Promise".equals(typeName) && typeGroup == TypeGroup.OBJECT;
  }

  /**
   * @param nullable the new nullability
   * @return a copy with the given {@link #isNullable()}
   */
  public TypeMetadata withNullable(boolean nullable) {
    return new TypeMetadata(name, typeGroup, typeName, parameters, nullable, isGetAccessor, importPath, type, javaType);
  }

  /**
   * @param getAccessor the new flag
   * @return a copy with the given {@link #isGetAccessor()}
   */
  public TypeMetadata withGetAccessor(boolean getAccessor) {
    return new TypeMetadata(name, typeGroup, typeName, parameters, isNullable, getAccessor, importPath, type, javaType);
  }

  @Override
  public String toString() {
    return "TypeMetadata{name=" + name + ", typeGroup=" + typeGroup + ", typeName=" + typeName
        + ", isNullable=" + isNullable + ", isGetAccessor=" + isGetAccessor
        + (importPath == null ? "" : ", importPath=" + importPath)
        + (parameters.isEmpty() ? "" : ", parameters=" + parameters) + "}";
  }
}
