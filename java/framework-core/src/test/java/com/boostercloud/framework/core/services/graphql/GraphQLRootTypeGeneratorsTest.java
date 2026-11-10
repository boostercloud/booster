package com.boostercloud.framework.core.services.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Logger;
import com.boostercloud.metadata.ClassMetadata;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLInputObjectType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** {@code graphql-mutation-generator.ts} and {@code graphql-subcriptions-generator.ts} (no TypeScript unit tests). */
class GraphQLRootTypeGeneratorsTest {

  private final BoosterConfig config = new BoosterConfig("test");
  private final GraphQLTypeInformer typeInformer = new GraphQLTypeInformer(Logger.NOOP);

  @Test
  void generatesNoMutationWithoutCommands() {
    assertThat(new GraphQLMutationGenerator(Map.of(), typeInformer, type -> null, config).generate()).isEmpty();
  }

  @Test
  void generatesOneMutationPerCommandWithItsResolver() {
    Map<String, TargetTypeMetadata> commands = new LinkedHashMap<>();
    for (Class<?> command : List.of(GraphQLTestApp.CreateProduct.class, GraphQLTestApp.DeleteProduct.class)) {
      ClassMetadata metadata = ClassMetadata.of(command);
      commands.put(command.getSimpleName(), new TargetTypeMetadata(command, metadata.fields(), metadata.methods()));
    }
    GraphQLFieldResolver resolver = (parent, args, context, info) -> true;

    Optional<GeneratedObjectType> mutation = new GraphQLMutationGenerator(commands, typeInformer, type -> resolver,
        config).generate();

    assertThat(mutation).isPresent();
    assertThat(mutation.get().name()).isEqualTo("Mutation");
    assertThat(mutation.get().fields().keySet()).containsExactly("CreateProduct", "DeleteProduct");
    assertThat(Common.printType(mutation.get().fields().get("CreateProduct").type())).isEqualTo("ID!");
    assertThat(Common.printType(mutation.get().fields().get("DeleteProduct").type())).isEqualTo("JSON!");
    GraphQLCodeRegistry.Builder registry = GraphQLCodeRegistry.newCodeRegistry();
    mutation.get().registerResolvers(registry);
    assertThat(registry.hasDataFetcher(graphql.schema.FieldCoordinates.coordinates("Mutation", "CreateProduct")))
        .isTrue();
  }

  @Test
  void generatesNoSubscriptionWithoutReadModels() {
    assertThat(new GraphQLSubscriptionGenerator(List.of(), typeInformer, type -> null, type -> null, null, config)
        .generate()).isEmpty();
  }

  @Test
  void generatesTheByIdAndFilteredSubscriptionsOfEachReadModel() {
    GraphQLFieldResolver byId = (parent, args, context, info) -> "by id";
    GraphQLFieldResolver filtered = (parent, args, context, info) -> "filtered";
    Map<String, GraphQLInputObjectType> filters = new LinkedHashMap<>();

    GeneratedObjectType subscription = new GraphQLSubscriptionGenerator(
        List.of(GraphQLTestApp.TimelineReadModel.class), typeInformer, type -> byId, type -> filtered, filters,
        config).generate().orElseThrow();

    assertThat(subscription.name()).isEqualTo("Subscription");
    assertThat(subscription.fields().keySet()).containsExactly("TimelineReadModel", "TimelineReadModels");
    GraphQLFieldConfig byIdField = subscription.fields().get("TimelineReadModel");
    assertThat(Common.printType(byIdField.type())).isEqualTo("TimelineReadModel");
    assertThat(byIdField.args().keySet()).containsExactly("id");
    assertThat(byIdField.subscribe()).isSameAs(byId);
    assertThat(byIdField.dataFetcher()).isSameAs(byId);
    assertThat(((GraphQLFieldResolver) byIdField.resolve()).resolve("the event", Map.of(), null, null))
        .isEqualTo("the event");
    GraphQLFieldConfig filteredField = subscription.fields().get("TimelineReadModels");
    assertThat(Common.printType(filteredField.type())).isEqualTo("TimelineReadModel");
    assertThat(Common.printType(filteredField.args().get("filter"))).isEqualTo("TimelineReadModelSubscriptionFilter");
    assertThat(filteredField.subscribe()).isSameAs(filtered);
    assertThat(filters).containsKeys("UUIDPropertyFilter", "StringPropertyFilter");
  }
}
