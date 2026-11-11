package com.boostercloud.framework.core.services.graphql.querygenerators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.boostercloud.framework.core.services.graphql.GraphQLFieldConfig;
import com.boostercloud.framework.core.services.graphql.GraphQLFieldResolver;
import com.boostercloud.framework.core.services.graphql.GraphQLTypeInformer;
import com.boostercloud.framework.core.services.graphql.ResolverBuilder;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.framework.types.concepts.TimeKey;
import com.boostercloud.framework.types.concepts.UUID;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNamedType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Port of {@code test/services/graphql/query-generators/graphql-query-by-keys-generator.test.ts}. */
class GraphqlQueryByKeysGeneratorTest {

  public record AnotherReadModel(UUID id, String otherField) {}

  public record ASequencedReadModel(UUID id, TimeKey timestamp) {}

  public record ARegularReadModel(String id) {}

  public record AnotherSequencedReadModel(String id, String timestamp) {}

  private BoosterConfig mockConfig;
  private GraphQLTypeInformer typeInformer;

  @BeforeEach
  void setUp() {
    mockConfig = new BoosterConfig("test");
    typeInformer = new GraphQLTypeInformer(Logger.NOOP);
  }

  @Test
  void generatesByIdAndSequencedQueries() {
    mockConfig.readModelSequenceKeys.put("ASequencedReadModel", "timestamp");
    GraphqlQueryByKeysGenerator generator = spy(new GraphqlQueryByKeysGenerator(mockConfig,
        List.of(AnotherReadModel.class, ASequencedReadModel.class), typeInformer, readModel -> null));

    Map<String, GraphQLFieldConfig> queries = generator.generateByKeysQueries();

    verify(generator, times(1)).generateByIdQuery(AnotherReadModel.class);
    verify(generator, times(1)).generateByIdAndSequenceKeyQuery(ASequencedReadModel.class, "timestamp");
    assertThat(queries.keySet()).containsExactly("AnotherReadModel", "ASequencedReadModel");
  }

  @Test
  void generatesAQueryNamedAfterTheReadModelThatAcceptsAUniqueId() {
    ResolverBuilder byIdResolverBuilder = mock(ResolverBuilder.class);
    GraphQLFieldResolver resolver = mock(GraphQLFieldResolver.class);
    when(byIdResolverBuilder.build(ARegularReadModel.class)).thenReturn(resolver);
    GraphqlQueryByKeysGenerator generator = new GraphqlQueryByKeysGenerator(mockConfig,
        List.of(ARegularReadModel.class), typeInformer, byIdResolverBuilder);

    GraphQLFieldConfig query = generator.generateByIdQuery(ARegularReadModel.class);

    assertThat(((GraphQLNamedType) query.type()).getName()).isEqualTo("ARegularReadModel");
    assertThat(query.args().keySet()).containsExactly("id");
    assertThat(query.resolve()).isSameAs(resolver);
    verify(byIdResolverBuilder).build(ARegularReadModel.class);
  }

  @Test
  void generatesAQueryNamedAfterTheReadModelThatAcceptsAnIdAndASequenceKey() {
    ResolverBuilder byIdResolverBuilder = mock(ResolverBuilder.class);
    GraphQLFieldResolver resolver = mock(GraphQLFieldResolver.class);
    when(byIdResolverBuilder.build(AnotherSequencedReadModel.class)).thenReturn(resolver);
    GraphqlQueryByKeysGenerator generator = new GraphqlQueryByKeysGenerator(mockConfig,
        List.of(AnotherSequencedReadModel.class), typeInformer, byIdResolverBuilder);

    GraphQLFieldConfig query = generator.generateByIdAndSequenceKeyQuery(AnotherSequencedReadModel.class,
        "timestamp");

    assertThat(query.type()).isInstanceOf(GraphQLList.class);
    assertThat(((GraphQLNamedType) ((GraphQLList) query.type()).getWrappedType()).getName())
        .isEqualTo("AnotherSequencedReadModel");
    assertThat(query.args().keySet()).containsExactly("id", "timestamp");
    assertThat(query.resolve()).isSameAs(resolver);
    verify(byIdResolverBuilder).build(AnotherSequencedReadModel.class);
  }

  @Test
  void leavesOutTheNonExposedPropertiesOfTheReadModel() {
    mockConfig.nonExposedGraphQLMetadataKey.put("AnotherReadModel", List.of("otherField"));
    GraphqlQueryByKeysGenerator generator = new GraphqlQueryByKeysGenerator(mockConfig,
        List.of(AnotherReadModel.class), typeInformer, readModel -> null);

    GraphQLFieldConfig query = generator.generateByKeysQueries().get("AnotherReadModel");

    assertThat(((graphql.schema.GraphQLObjectType) query.type()).getFieldDefinitions())
        .extracting(field -> field.getName()).containsExactly("id");
  }
}
