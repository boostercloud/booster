package com.boostercloud.cli.services.generator.target;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.cli.common.CliException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Port of {@code test/services/generator/target/parsing.test.ts}. */
class ParsingTest {
  @Nested
  class ParseFields {
    @Test
    void oneCorrectField() {
      HasFields fields = Parsing.parseFields(List.of("title:string"));
      assertThat(fields.fields()).containsExactly(new Field("title", "string"));
    }

    @Test
    void manyCorrectFields() {
      HasFields fields = Parsing.parseFields(List.of("name:string", "age:number", "id:UUID"));
      assertThat(fields.fields())
          .containsExactly(new Field("name", "string"), new Field("age", "number"), new Field("id", "UUID"));
    }

    @Test
    void oneFieldWithoutType() {
      assertThatThrownBy(() -> Parsing.parseFields(List.of("title")))
          .isInstanceOf(CliException.class)
          .hasMessage("Error parsing field title. Fields must be in the form of <field name>:<field type>");
    }

    @Test
    void manyFieldsWithoutType() {
      assertThatThrownBy(() -> Parsing.parseFields(List.of("title:string", "content")))
          .hasMessageContaining("Error parsing field content");
    }

    @Test
    void oneFieldWithEmptyType() {
      assertThatThrownBy(() -> Parsing.parseFields(List.of("title:"))).hasMessageContaining("Error parsing field title");
    }

    @Test
    void manyFieldsWithEmptyType() {
      assertThatThrownBy(() -> Parsing.parseFields(List.of("title:string", "content:")))
          .hasMessageContaining("Error parsing field content");
    }

    @Test
    void oneFieldWithEmptyName() {
      assertThatThrownBy(() -> Parsing.parseFields(List.of(":string")))
          .hasMessageContaining("Error parsing field :string");
    }

    @Test
    void manyFieldsWithEmptyName() {
      assertThatThrownBy(() -> Parsing.parseFields(List.of("title:string", ":string")))
          .hasMessageContaining("Error parsing field :string");
    }

    @Test
    void typesWithColonsAreRejected() {
      assertThatThrownBy(() -> Parsing.parseFields(List.of("a:b:c"))).hasMessageContaining("Error parsing field a:b:c");
    }

    @Test
    void duplicatedFields() {
      assertThatThrownBy(() -> Parsing.parseFields(List.of("title:string", "content:string", "title:number")))
          .hasMessage("Error parsing field title. Fields cannot be duplicated");
    }

    @Test
    void manyDuplicatedFields() {
      assertThatThrownBy(
              () -> Parsing.parseFields(List.of("title:string", "content:string", "title:number", "content:string")))
          .hasMessage("Error parsing field title, content. Fields cannot be duplicated");
    }

    @Test
    void everyRepeatedOccurrenceIsReported() {
      assertThatThrownBy(() -> Parsing.parseFields(List.of("a:string", "a:string", "a:number")))
          .hasMessage("Error parsing field a, a. Fields cannot be duplicated");
    }

    @Test
    void fieldWithoutTypeAndDuplicatedFields() {
      assertThatThrownBy(() -> Parsing.parseFields(List.of("title:string", "content", "title:number")))
          .hasMessageContaining("Error parsing field content");
    }

    @Test
    void duplicatedFieldsAndAFieldWithoutType() {
      assertThatThrownBy(() -> Parsing.parseFields(List.of("title:string", "title:number", "content")))
          .hasMessageContaining("Error parsing field content");
    }
  }

  @Nested
  class ParseProjections {
    @Test
    void oneCorrectEntityAndId() {
      assertThat(Parsing.parseProjections(List.of("Post:id")).projections()).containsExactly(new Projection("Post", "id"));
    }

    @Test
    void manyCorrectEntitiesAndIds() {
      assertThat(Parsing.parseProjections(List.of("Post:id", "Comment:postId")).projections())
          .containsExactly(new Projection("Post", "id"), new Projection("Comment", "postId"));
    }

    @Test
    void oneEntityWithoutId() {
      assertThatThrownBy(() -> Parsing.parseProjections(List.of("Post")))
          .hasMessage("Error parsing projection Post. Projections must be in the form of <entity name>:<entity id>");
    }

    @Test
    void manyEntitiesWithoutId() {
      assertThatThrownBy(() -> Parsing.parseProjections(List.of("Post:id", "Comment")))
          .hasMessageContaining("Error parsing projection Comment");
    }

    @Test
    void oneEntityWithEmptyId() {
      assertThatThrownBy(() -> Parsing.parseProjections(List.of("Post:"))).hasMessageContaining("Error parsing projection Post:");
    }

    @Test
    void manyEntitiesWithEmptyId() {
      assertThatThrownBy(() -> Parsing.parseProjections(List.of("Post:id", "Comment:")))
          .hasMessageContaining("Error parsing projection Comment:");
    }

    @Test
    void oneEntityWithEmptyName() {
      assertThatThrownBy(() -> Parsing.parseProjections(List.of(":id"))).hasMessageContaining("Error parsing projection :id");
    }

    @Test
    void manyEntitiesWithEmptyName() {
      assertThatThrownBy(() -> Parsing.parseProjections(List.of("Post:id", ":id")))
          .hasMessageContaining("Error parsing projection :id");
    }
  }

  @Nested
  class ParseProjectionField {
    @Test
    void correctEntityWithId() {
      assertThat(Parsing.parseProjectionField("Post:id").projection()).isEqualTo(new Projection("Post", "id"));
    }

    @Test
    void entityWithoutId() {
      assertThatThrownBy(() -> Parsing.parseProjectionField("Post"))
          .hasMessage("Error parsing projection Post. Projections must be in the form of <entity name>:<entity id>");
    }

    @Test
    void entityWithEmptyName() {
      assertThatThrownBy(() -> Parsing.parseProjectionField(":id")).hasMessageContaining("Error parsing projection :id");
    }
  }

  @Nested
  class OtherParsers {
    @Test
    void parsesNamesEventsAndReactionsWithoutValidation() {
      assertThat(Parsing.parseName("whatever name")).isEqualTo(new HasName("whatever name"));
      assertThat(Parsing.parseEvent("PostCreated")).isEqualTo(new HasEvent("PostCreated"));
      assertThat(Parsing.parseReaction(List.of("A", "A", "B")).events())
          .containsExactly(new ReactionEvent("A"), new ReactionEvent("A"), new ReactionEvent("B"));
    }

    @Test
    void joinParsersMergesThePropertiesLeftToRight() {
      Map<String, Object> joined =
          Parsing.joinParsers(
              Parsing.parseName("Post"),
              Parsing.parseFields(List.of("title:string")),
              Parsing.parseReaction(List.of("PostCreated")),
              new HasName("Override"));
      assertThat(joined)
          .containsEntry("name", "Override")
          .containsEntry("fields", List.of(Map.of("name", "title", "type", "string")))
          .containsEntry("events", List.of(Map.of("eventName", "PostCreated")));
      assertThat(joined.keySet()).containsExactly("name", "fields", "events");
    }

    @Test
    void importDeclarationsExpandToQualifiedNames() {
      assertThat(new ImportDeclaration("a.b", "C, D").qualifiedNames()).containsExactly("a.b.C", "a.b.D");
      assertThat(new ImportDeclaration("", "C").qualifiedNames()).containsExactly("C");
    }
  }
}
