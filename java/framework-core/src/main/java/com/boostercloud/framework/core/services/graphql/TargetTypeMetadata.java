package com.boostercloud.framework.core.services.graphql;

import com.boostercloud.framework.types.concepts.CommandMetadata;
import com.boostercloud.framework.types.concepts.QueryMetadata;
import com.boostercloud.metadata.MethodMetadata;
import com.boostercloud.metadata.PropertyMetadata;
import java.util.List;

/**
 * Port of {@code TargetTypeMetadata} ({@code services/graphql/common.ts}): the class, properties and methods of a
 * command or query, from which mutation/query fields are generated ({@code TargetTypesMap} is a
 * {@code Map<String, TargetTypeMetadata>}).
 *
 * <p>Owned by stage <b>core-3</b> (spec 08 §2.3).
 *
 * @param type the command or query class
 * @param properties its properties
 * @param methods its methods
 */
public record TargetTypeMetadata(Class<?> type, List<PropertyMetadata> properties, List<MethodMetadata> methods) {

  /**
   * @param metadata a command registration
   * @return its target type
   */
  public static TargetTypeMetadata of(CommandMetadata metadata) {
    return new TargetTypeMetadata(metadata.type(), metadata.properties(), metadata.methods());
  }

  /**
   * @param metadata a query registration
   * @return its target type
   */
  public static TargetTypeMetadata of(QueryMetadata metadata) {
    return new TargetTypeMetadata(metadata.type(), metadata.properties(), metadata.methods());
  }
}
