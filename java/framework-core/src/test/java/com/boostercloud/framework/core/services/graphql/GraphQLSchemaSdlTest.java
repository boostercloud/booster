package com.boostercloud.framework.core.services.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.BoosterTestSupport;
import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.Decorators;
import com.boostercloud.framework.core.decorators.ReadModel;
import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.concepts.EntityMetadata;
import com.boostercloud.framework.types.concepts.ReducerMetadata;
import com.boostercloud.framework.types.concepts.UUID;
import graphql.schema.GraphQLSchema;
import graphql.schema.validation.InvalidSchemaException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Prints the generated schema of representative applications and compares it with the expected SDL (the Java
 * counterpart of the TypeScript {@code schema.*.integration.ts} introspection checks).
 */
class GraphQLSchemaSdlTest extends BoosterTestSupport {

  @BeforeEach
  void resetSchema() {
    GraphQLGenerator.reset();
  }

  @AfterEach
  void forgetSchema() {
    GraphQLGenerator.reset();
  }

  @Test
  void generatesTheSchemaOfAnApplication() throws IOException {
    GraphQLTestApp.CLASSES.forEach(Decorators::registerClass);

    GraphQLSchema schema = GraphQLGenerator.generateSchema(Booster.config());

    assertThat(SchemaSdl.print(schema)).isEqualTo(resource("/graphql/test-app.graphql"));
  }

  @Test
  void returnsTheSameSchemaOnLaterCalls() {
    GraphQLTestApp.CLASSES.forEach(Decorators::registerClass);
    GraphQLSchema schema = GraphQLGenerator.generateSchema(Booster.config());

    assertThat(GraphQLGenerator.generateSchema(new BoosterConfig("other"))).isSameAs(schema);
  }

  @Test
  void generatesOnlyTheEventQueriesWithoutReadModelsCommandsOrQueries() {
    BoosterConfig config = new BoosterConfig("test");
    config.entities.put("Cart", new EntityMetadata(Object.class, null));
    config.reducers.put("CartCreated", new ReducerMetadata(Object.class, "created"));

    String sdl = SchemaSdl.print(GraphQLGenerator.generateSchema(config));

    assertThat(sdl).isEqualTo("""
        enum EntityType {
          Cart
        }

        type EventQueryResponse {
          type: String!
          entity: String!
          entityID: ID!
          requestID: ID!
          user: User
          createdAt: String!
          value: JSON!
          deletedAt: String
        }

        enum EventType {
          CartCreated
        }

        "The `JSON` scalar type represents JSON values as specified by \
        [ECMA-404](http://www.ecma-international.org/publications/files/ECMA-ST/ECMA-404_1st_edition_october_2009.pdf)."
        scalar JSON

        type Query {
          eventsByEntity(entity: EntityType!, entityID: ID, from: String, to: String, limit: Int): [EventQueryResponse]
          eventsByType(type: EventType!, from: String, to: String, limit: Int): [EventQueryResponse]
        }

        type User {
          id: String
          username: String!
          roles: [String!]
        }
        """);
  }

  @Test
  void reportsAnInvalidSchemaOnEveryCall() {
    BoosterConfig config = new BoosterConfig("test");

    assertThatThrownBy(() -> GraphQLGenerator.generateSchema(config))
        .isInstanceOf(InvalidSchemaException.class)
        .hasMessageContaining("EntityType");
    InvalidSchemaException failure = catchFailure(config);
    assertThat(catchFailure(config)).isSameAs(failure);
  }

  @Test
  void replacesTheByIdQueryWhenThePluralEqualsTheName() {
    BoosterConfig config = Booster.config();
    Decorators.registerClass(ProductUpdates.class);
    config.entities.put("Cart", new EntityMetadata(Object.class, null));
    config.reducers.put("CartCreated", new ReducerMetadata(Object.class, "created"));

    GraphQLSchema schema = GraphQLGenerator.generateSchema(config);

    assertThat(schema.getQueryType().getFieldDefinition("ProductUpdates").getArguments())
        .extracting(argument -> argument.getName())
        .containsExactly("filter");
    assertThat(schema.getQueryType().getFieldDefinitions())
        .extracting(field -> field.getName())
        .containsExactly("ProductUpdates", "ListProductUpdates", "eventsByEntity", "eventsByType");
    assertThat(schema.getSubscriptionType().getFieldDefinitions())
        .extracting(field -> field.getName())
        .containsExactly("ProductUpdates");
  }

  @ReadModel(authorize = All.class)
  public record ProductUpdates(UUID id) {}

  private static InvalidSchemaException catchFailure(BoosterConfig config) {
    try {
      GraphQLGenerator.generateSchema(config);
      throw new AssertionError("The schema was expected to be invalid");
    } catch (InvalidSchemaException e) {
      return e;
    }
  }

  private static String resource(String name) throws IOException {
    try (InputStream stream = GraphQLSchemaSdlTest.class.getResourceAsStream(name)) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
