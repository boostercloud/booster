package com.boostercloud.framework.core.services.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.GraphQLOperation;
import com.boostercloud.metadata.ClassMetadata;
import com.boostercloud.metadata.PropertyMetadata;
import com.boostercloud.metadata.TypeMetadata;
import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.ArrayValue;
import graphql.language.BooleanValue;
import graphql.language.EnumValue;
import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.StringValue;
import graphql.language.VariableReference;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The functions of {@code services/graphql/common.ts} and the {@code JSON} scalar. */
class CommonTest {

  private static final GraphQLContext CONTEXT = GraphQLContext.getDefault();

  @Nested
  class DateScalar {

    @Test
    void isNamedDate() {
      assertThat(Common.dateScalar().getName()).isEqualTo("Date");
      assertThat(Common.dateScalar().getDescription()).isEqualTo("Date custom scalar type");
    }

    @Test
    void serializesDatesAsIsoStringsWithMilliseconds() {
      Instant instant = Instant.parse("2024-01-30T10:15:30Z");
      var coercing = Common.dateScalar().getCoercing();

      assertThat(coercing.serialize(instant, CONTEXT, Locale.ENGLISH)).isEqualTo("2024-01-30T10:15:30.000Z");
      assertThat(coercing.serialize(Date.from(instant), CONTEXT, Locale.ENGLISH))
          .isEqualTo("2024-01-30T10:15:30.000Z");
      assertThat(coercing.serialize(OffsetDateTime.parse("2024-01-30T11:15:30+01:00"), CONTEXT, Locale.ENGLISH))
          .isEqualTo("2024-01-30T10:15:30.000Z");
      assertThat(coercing.serialize(ZonedDateTime.parse("2024-01-30T10:15:30.5Z"), CONTEXT, Locale.ENGLISH))
          .isEqualTo("2024-01-30T10:15:30.500Z");
      assertThat(coercing.serialize("2024-01-30T10:15:30Z", CONTEXT, Locale.ENGLISH))
          .isEqualTo("2024-01-30T10:15:30.000Z");
    }

    @Test
    void failsToSerializeOtherValues() {
      assertThatThrownBy(() -> Common.dateScalar().getCoercing().serialize(42, CONTEXT, Locale.ENGLISH))
          .isInstanceOf(CoercingSerializeException.class);
    }

    @Test
    void parsesVariables() {
      var coercing = Common.dateScalar().getCoercing();

      assertThat(coercing.parseValue("2024-01-30T10:15:30.123Z", CONTEXT, Locale.ENGLISH))
          .isEqualTo("2024-01-30T10:15:30.123Z");
      assertThat(coercing.parseValue("2024-01-30", CONTEXT, Locale.ENGLISH)).isEqualTo("2024-01-30T00:00:00.000Z");
    }

    @Test
    void rejectsVariablesThatAreNotStrings() {
      assertThatThrownBy(() -> Common.dateScalar().getCoercing().parseValue(42, CONTEXT, Locale.ENGLISH))
          .isInstanceOf(CoercingParseValueException.class)
          .hasMessage("Invalid date, not a string: 42");
    }

    @Test
    void rejectsInvalidDates() {
      assertThatThrownBy(() -> Common.dateScalar().getCoercing().parseValue("tomorrow", CONTEXT, Locale.ENGLISH))
          .isInstanceOf(CoercingParseValueException.class)
          .hasMessage("Invalid date: tomorrow");
      assertThatThrownBy(() -> Common.dateScalar().getCoercing().parseLiteral(StringValue.of("nope"),
          CoercedVariables.emptyVariables(), CONTEXT, Locale.ENGLISH))
          .isInstanceOf(CoercingParseLiteralException.class)
          .hasMessage("Invalid date: nope");
    }

    @Test
    void parsesStringLiteralsAndIgnoresTheOtherOnes() {
      var coercing = Common.dateScalar().getCoercing();

      assertThat(coercing.parseLiteral(StringValue.of("2024-01-30T10:15:30Z"), CoercedVariables.emptyVariables(),
          CONTEXT, Locale.ENGLISH)).isEqualTo("2024-01-30T10:15:30.000Z");
      assertThat(coercing.parseLiteral(IntValue.of(1), CoercedVariables.emptyVariables(), CONTEXT, Locale.ENGLISH))
          .isNull();
    }
  }

  @Nested
  class JsonScalar {

    @Test
    void isNamedJson() {
      assertThat(Common.jsonScalar().getName()).isEqualTo("JSON");
    }

    @Test
    void serializesValuesAsRawJson() {
      var coercing = Common.jsonScalar().getCoercing();

      assertThat(coercing.serialize("text", CONTEXT, Locale.ENGLISH)).isEqualTo("text");
      assertThat(coercing.serialize(2.5, CONTEXT, Locale.ENGLISH)).isEqualTo(2.5);
      assertThat(coercing.serialize(new Point(1, UUID.of("p")), CONTEXT, Locale.ENGLISH))
          .isEqualTo(Map.of("x", 1, "id", "p"));
      assertThat(coercing.serialize(List.of(new Point(2, UUID.of("q"))), CONTEXT, Locale.ENGLISH))
          .isEqualTo(List.of(Map.of("x", 2, "id", "q")));
    }

    @Test
    void takesVariablesAsTheyAre() {
      Map<String, Object> value = Map.of("a", List.of(1, 2));

      assertThat(Common.jsonScalar().getCoercing().parseValue(value, CONTEXT, Locale.ENGLISH)).isSameAs(value);
    }

    @Test
    void turnsLiteralsIntoRawValues() {
      ObjectValue literal = ObjectValue.newObjectValue()
          .objectField(new ObjectField("text", StringValue.of("a")))
          .objectField(new ObjectField("int", IntValue.of(3)))
          .objectField(new ObjectField("float", FloatValue.of(1.5)))
          .objectField(new ObjectField("bool", BooleanValue.of(true)))
          .objectField(new ObjectField("none", NullValue.of()))
          .objectField(new ObjectField("enum", EnumValue.of("ASC")))
          .objectField(new ObjectField("list", ArrayValue.newArrayValue().value(IntValue.of(1)).build()))
          .objectField(new ObjectField("variable", VariableReference.of("v")))
          .build();
      Map<String, Object> expected = new LinkedHashMap<>();
      expected.put("text", "a");
      expected.put("int", 3);
      expected.put("float", 1.5);
      expected.put("bool", true);
      expected.put("none", null);
      expected.put("enum", "ASC");
      expected.put("list", List.of(1));
      expected.put("variable", "from variable");

      Object raw = Common.jsonScalar().getCoercing().parseLiteral(literal,
          CoercedVariables.of(Map.of("v", "from variable")), CONTEXT, Locale.ENGLISH);

      assertThat(raw).isEqualTo(expected);
    }

    @Test
    void writesRawValuesAsLiterals() {
      var coercing = Common.jsonScalar().getCoercing();
      Map<String, Object> value = new LinkedHashMap<>();
      value.put("a", List.of(1, 2.5, "x", true));
      value.put("b", null);
      value.put("c", 10_000_000_000L);
      value.put("d", BigInteger.TEN);

      ObjectValue literal = (ObjectValue) coercing.valueToLiteral(value, CONTEXT, Locale.ENGLISH);

      assertThat(literal.getObjectFields()).extracting(ObjectField::getName).containsExactly("a", "b", "c", "d");
      ArrayValue array = (ArrayValue) literal.getObjectFields().getFirst().getValue();
      assertThat(((IntValue) array.getValues().get(0)).getValue()).isEqualTo(BigInteger.ONE);
      assertThat(((FloatValue) array.getValues().get(1)).getValue()).isEqualTo(new BigDecimal("2.5"));
      assertThat(literal.getObjectFields().get(1).getValue()).isInstanceOf(NullValue.class);
      assertThat(((IntValue) literal.getObjectFields().get(2).getValue()).getValue())
          .isEqualTo(BigInteger.valueOf(10_000_000_000L));
    }

    record Point(int x, UUID id) {}
  }

  @Test
  void buildsSimpleEnumsWhoseValuesAreTheirNames() {
    GraphQLEnumType enumType = Common.buildGraphqlSimpleEnumFor("orderProperty", List.of("ASC", "DESC"));

    assertThat(enumType.getName()).isEqualTo("orderProperty");
    assertThat(enumType.getValues()).extracting(GraphQLEnumValueDefinition::getName).containsExactly("ASC", "DESC");
    assertThat(enumType.getValues()).extracting(GraphQLEnumValueDefinition::getValue).containsExactly("ASC", "DESC");
  }

  record WithFields(String a, String b, String c) {}

  @Test
  void removesTheExcludedFields() {
    List<PropertyMetadata> fields = ClassMetadata.of(WithFields.class).fields();

    assertThat(Common.nonExcludedFields(fields, List.of("b"))).extracting(PropertyMetadata::name)
        .containsExactly("a", "c");
    assertThat(Common.nonExcludedFields(fields, null)).isSameAs(fields);
  }

  record WithExternal(UUID id, WithFields local, java.time.Duration duration) {}

  @Test
  void tellsExternalTypes() {
    ClassMetadata metadata = ClassMetadata.of(WithExternal.class);
    TypeMetadata id = metadata.field("id").orElseThrow().typeInfo();
    TypeMetadata local = metadata.field("local").orElseThrow().typeInfo();
    TypeMetadata duration = metadata.field("duration").orElseThrow().typeInfo();

    assertThat(Common.isExternalType(id)).isTrue();
    assertThat(Common.isExternalType(local)).isFalse();
    assertThat(Common.isExternalType(duration)).isTrue();
  }

  @Nested
  class ResolverContextLookup {

    private final GraphQLResolverContext context = new GraphQLResolverContext(null, new LinkedHashMap<>(),
        GraphQLOperation.builder().query("{ a }").build(), UUID.of("r"), null, true, null, null);

    @Test
    void readsTheGraphQLContextEntry() {
      DataFetchingEnvironment environment = DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
          .graphQLContext(GraphQLContext.of(Common.graphQLContextOf(context))).build();

      assertThat(Common.resolverContext(environment)).isSameAs(context);
    }

    @Test
    void readsAnyGraphQLContextEntryOfTheRightType() {
      DataFetchingEnvironment environment = DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
          .graphQLContext(GraphQLContext.of(Map.of("booster", context))).build();

      assertThat(Common.resolverContext(environment)).isSameAs(context);
    }

    @Test
    void readsTheLocalContext() {
      DataFetchingEnvironment environment = DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
          .localContext(context).build();

      assertThat(Common.resolverContext(environment)).isSameAs(context);
    }

    @Test
    void hasNoContextOtherwise() {
      DataFetchingEnvironment environment = DataFetchingEnvironmentImpl.newDataFetchingEnvironment().build();

      assertThat(Common.resolverContext(environment)).isNull();
      assertThat(Common.resolverContext(null)).isNull();
    }
  }
}
