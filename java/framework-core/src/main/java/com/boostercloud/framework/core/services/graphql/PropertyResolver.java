package com.boostercloud.framework.core.services.graphql;

import com.boostercloud.metadata.ClassMetadata;
import com.boostercloud.metadata.PropertyMetadata;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetcherFactory;
import graphql.schema.DataFetcherFactoryEnvironment;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.PropertyDataFetcher;
import java.util.Map;
import java.util.Optional;

/**
 * The default resolver of the fields that have no resolver of their own (graphql-js {@code defaultFieldResolver},
 * {@code source[fieldName]}): a raw map's entry, or the property of an object as the metadata module reads it (record
 * components, fields of any visibility, {@code @CalculatedField} methods), falling back to graphql-java's
 * {@link PropertyDataFetcher} (getters). {@code Optional} values are unwrapped by graphql-java.
 */
public final class PropertyResolver implements DataFetcher<Object> {

  /** The factory installed as the schema's default data fetcher. */
  @SuppressWarnings("deprecation")
  public static final DataFetcherFactory<Object> FACTORY = new DataFetcherFactory<>() {
    @Override
    public DataFetcher<Object> get(DataFetcherFactoryEnvironment environment) {
      return new PropertyResolver(environment.getFieldDefinition().getName());
    }

    @Override
    public DataFetcher<Object> get(GraphQLFieldDefinition fieldDefinition) {
      return new PropertyResolver(fieldDefinition.getName());
    }
  };

  private final String propertyName;
  private final PropertyDataFetcher<Object> fallback;

  /**
   * @param propertyName the property to read
   */
  public PropertyResolver(String propertyName) {
    this.propertyName = propertyName;
    this.fallback = PropertyDataFetcher.fetching(propertyName);
  }

  @Override
  public Object get(DataFetchingEnvironment environment) throws Exception {
    Object source = environment.getSource();
    Object value = read(source, propertyName);
    if (value != NOT_FOUND) {
      return value;
    }
    return fallback.get(environment);
  }

  private static final Object NOT_FOUND = new Object();

  private static Object read(Object source, String propertyName) {
    if (source == null) {
      return null;
    }
    if (source instanceof Map<?, ?> map) {
      return map.get(propertyName);
    }
    Optional<PropertyMetadata> property;
    try {
      property = ClassMetadata.of(source.getClass()).field(propertyName);
    } catch (RuntimeException e) {
      return NOT_FOUND;
    }
    return property.isPresent() ? property.get().readValue(source) : NOT_FOUND;
  }
}
