package com.boostercloud.metadata;

/**
 * Common view of {@link PropertyMetadata} and {@link MethodMetadata}: in TypeScript both are
 * {@code PropertyMetadata} objects ({@code { name, typeInfo }}), so code that only needs the name and the type can
 * treat them uniformly.
 */
public sealed interface MemberMetadata permits PropertyMetadata, MethodMetadata {
  /**
   * @return the member name
   */
  String name();

  /**
   * @return the type of the property, or the return type of the method
   */
  TypeMetadata typeInfo();
}
