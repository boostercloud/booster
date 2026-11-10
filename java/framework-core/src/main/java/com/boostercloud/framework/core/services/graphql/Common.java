package com.boostercloud.framework.core.services.graphql;

import com.boostercloud.metadata.PropertyMetadata;
import com.boostercloud.metadata.TypeMetadata;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLScalarType;
import java.util.List;

/**
 * Port of the functions and constants of {@code services/graphql/common.ts} ({@code DateScalar},
 * {@code isExternalType}, {@code buildGraphqlSimpleEnumFor}, {@code nonExcludedFields}). The types of that file are
 * {@link GraphQLResolverContext}, {@link TargetTypeMetadata} and {@link ResolverBuilder}.
 *
 * <p>Skeleton: implemented by stage <b>core-3</b> (spec 08 §3.10).
 */
public final class Common {

  private Common() {}

  /**
   * {@code DateScalar}: the {@code Date} scalar (ISO-8601 strings). Implemented by stage core-3.
   *
   * @return the scalar
   */
  public static GraphQLScalarType dateScalar() {
    throw new UnsupportedOperationException("not implemented yet: Common#dateScalar");
  }

  /**
   * {@code isExternalType(typeMetadata)}. Implemented by stage core-3.
   *
   * @param typeMetadata a type
   * @return whether it is exposed as {@code JSON} instead of being expanded
   */
  public static boolean isExternalType(TypeMetadata typeMetadata) {
    throw new UnsupportedOperationException("not implemented yet: Common#isExternalType");
  }

  /**
   * {@code buildGraphqlSimpleEnumFor(enumName, values)}. Implemented by stage core-3.
   *
   * @param enumName the enum name
   * @param values its values
   * @return the enum type
   */
  public static GraphQLEnumType buildGraphqlSimpleEnumFor(String enumName, List<String> values) {
    throw new UnsupportedOperationException("not implemented yet: Common#buildGraphqlSimpleEnumFor");
  }

  /**
   * {@code nonExcludedFields(fields, excludeProps)}. Implemented by stage core-3.
   *
   * @param fields the properties
   * @param excludeProps names to drop, or {@code null}
   * @return the remaining properties
   */
  public static List<PropertyMetadata> nonExcludedFields(List<PropertyMetadata> fields, List<String> excludeProps) {
    throw new UnsupportedOperationException("not implemented yet: Common#nonExcludedFields");
  }
}
