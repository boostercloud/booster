package com.boostercloud.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.metadata.fixtures.Fixtures;
import com.boostercloud.metadata.fixtures.Fixtures.Annotated;
import com.boostercloud.metadata.fixtures.Fixtures.Bike;
import com.boostercloud.metadata.fixtures.Fixtures.Car;
import com.boostercloud.metadata.fixtures.Fixtures.Chihuahua;
import com.boostercloud.metadata.fixtures.Fixtures.Deep;
import com.boostercloud.metadata.fixtures.Fixtures.Exploding;
import com.boostercloud.metadata.fixtures.Fixtures.GenericClass;
import com.boostercloud.metadata.fixtures.Fixtures.Ordered;
import com.boostercloud.metadata.fixtures.Fixtures.PersonReadModel;
import com.boostercloud.metadata.fixtures.Fixtures.Size;
import com.boostercloud.metadata.fixtures.Fixtures.StringBox;
import com.boostercloud.metadata.fixtures.Fixtures.Truck;
import com.boostercloud.metadata.fixtures.Fixtures.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Port of the expectations written as comments in {@code packages/metadata-booster/test/test.ts} and of the
 * transformer output pinned by {@code framework-core/test/decorators/read-model.test.ts}, plus Java-specific rules.
 */
class MetadataExtractorsTest {

  private static TypeMetadata fieldType(Class<?> type, String field) {
    return ClassMetadata.of(type).field(field).orElseThrow().typeInfo();
  }

  @AfterEach
  void resetExternalPrefixes() {
    ExternalTypes.resetExternalPackagePrefixes();
  }

  @Nested
  class BasicTypes {
    @Test
    void describesStringsLikeTheTypeScriptTransformer() {
      TypeMetadata name = fieldType(User.class, "name");
      assertThat(name.name()).isEqualTo("string");
      assertThat(name.typeGroup()).isEqualTo(TypeGroup.STRING);
      assertThat(name.typeName()).isEqualTo("String");
      assertThat(name.type()).isEqualTo(String.class);
      assertThat(name.isNullable()).isFalse();
      assertThat(name.isGetAccessor()).isFalse();
      assertThat(name.parameters()).isEmpty();
      assertThat(name.importPath()).isNull();
      assertThat(name.isExternal()).isFalse();
    }

    @Test
    void describesEveryJavaNumberAsNumber() {
      for (Class<?> numeric : List.of(int.class, Integer.class, long.class, double.class, Double.class,
          float.class, short.class, byte.class, java.math.BigDecimal.class, java.math.BigInteger.class)) {
        TypeMetadata metadata = TypeMetadata.of(numeric);
        assertThat(metadata.name()).as(numeric.getName()).isEqualTo("number");
        assertThat(metadata.typeGroup()).isEqualTo(TypeGroup.NUMBER);
        assertThat(metadata.typeName()).isEqualTo("Number");
      }
    }

    @Test
    void describesBooleansAndCharactersAndBytes() {
      assertThat(TypeMetadata.of(boolean.class).typeGroup()).isEqualTo(TypeGroup.BOOLEAN);
      assertThat(TypeMetadata.of(Boolean.class).name()).isEqualTo("boolean");
      assertThat(TypeMetadata.of(char.class).typeGroup()).isEqualTo(TypeGroup.STRING);
      TypeMetadata bytes = fieldType(Fixtures.Test.class, "bytes");
      assertThat(bytes.name()).isEqualTo("string");
      assertThat(bytes.typeGroup()).isEqualTo(TypeGroup.STRING);
    }

    @Test
    void describesObjectAsUnknownAndVoidAsVoid() {
      TypeMetadata any = fieldType(Fixtures.Test.class, "any0");
      assertThat(any.name()).isEqualTo("unknown");
      assertThat(any.typeGroup()).isEqualTo(TypeGroup.OTHER);
      assertThat(any.typeName()).isNull();
      assertThat(any.type()).isNull();
      MethodMetadata noResult = ClassMetadata.of(PersonReadModel.class).method("noResult").orElseThrow();
      assertThat(noResult.typeInfo().name()).isEqualTo("void");
      assertThat(noResult.typeInfo().typeGroup()).isEqualTo(TypeGroup.OTHER);
      assertThat(noResult.returnsVoid()).isTrue();
    }

    @Test
    void describesDatesLikeTheTypeScriptDateInterface() {
      TypeMetadata createdAt = fieldType(Fixtures.Test.class, "createdAt");
      assertThat(createdAt.name()).isEqualTo("Date");
      assertThat(createdAt.typeGroup()).isEqualTo(TypeGroup.INTERFACE);
      assertThat(createdAt.typeName()).isNull();
      assertThat(createdAt.type()).isNull();
      assertThat(TypeMetadata.of(java.util.Date.class).name()).isEqualTo("Date");
      assertThat(TypeMetadata.of(java.time.OffsetDateTime.class).name()).isEqualTo("Date");
    }
  }

  @Nested
  class ComplexTypes {
    @Test
    void describesClassesWithTheirSimpleName() {
      TypeMetadata inherited = fieldType(Fixtures.Test.class, "inheritedProps");
      assertThat(inherited.name()).isEqualTo("Chihuahua");
      assertThat(inherited.typeGroup()).isEqualTo(TypeGroup.CLASS);
      assertThat(inherited.typeName()).isEqualTo("Chihuahua");
      assertThat(inherited.type()).isEqualTo(Chihuahua.class);
      assertThat(inherited.importPath()).isEqualTo("com.boostercloud.metadata.fixtures");
      assertThat(inherited.isExternal()).isFalse();
    }

    @Test
    void describesEnumsWithTheirConstantsAsParameters() {
      TypeMetadata size = fieldType(Car.class, "size");
      assertThat(size.name()).isEqualTo("Size");
      assertThat(size.typeGroup()).isEqualTo(TypeGroup.ENUM);
      assertThat(size.typeName()).isEqualTo("Size");
      assertThat(size.type()).isEqualTo(Size.class);
      assertThat(size.parameters()).extracting(TypeMetadata::name).containsExactly("SMALL", "MEDIUM", "BIG");
      assertThat(size.parameters()).allSatisfy(constant -> {
        assertThat(constant.typeGroup()).isEqualTo(TypeGroup.OTHER);
        assertThat(constant.isNullable()).isFalse();
        assertThat(constant.typeName()).isNull();
      });
    }

    @Test
    void describesArraysAndListsAsArrayOfTheirElement() {
      for (String field : List.of("array0", "array1")) {
        TypeMetadata array = fieldType(Fixtures.Test.class, field);
        assertThat(array.name()).isEqualTo("string[]");
        assertThat(array.typeGroup()).isEqualTo(TypeGroup.ARRAY);
        assertThat(array.typeName()).isEqualTo("Array");
        assertThat(array.parameters()).hasSize(1);
        assertThat(array.parameter(0).typeGroup()).isEqualTo(TypeGroup.STRING);
        assertThat(array.parameter(0).isNullable()).isFalse();
      }
      TypeMetadata matrix = fieldType(Fixtures.Test.class, "matrix");
      assertThat(matrix.name()).isEqualTo("number[][]");
      assertThat(matrix.parameter(0).name()).isEqualTo("number[]");
      assertThat(matrix.parameter(0).parameter(0).typeGroup()).isEqualTo(TypeGroup.NUMBER);
    }

    @Test
    void describesSetsAsArrays() {
      TypeMetadata friends = fieldType(User.class, "friends");
      assertThat(friends.name()).isEqualTo("User[]");
      assertThat(friends.typeGroup()).isEqualTo(TypeGroup.ARRAY);
      assertThat(friends.parameter(0).typeGroup()).isEqualTo(TypeGroup.CLASS);
      assertThat(friends.parameter(0).type()).isEqualTo(User.class);
    }

    @Test
    void describesWildcardsWithTheirUpperBound() {
      assertThat(fieldType(Fixtures.Test.class, "covariantUsers").name()).isEqualTo("User[]");
      TypeMetadata unknown = fieldType(Fixtures.Test.class, "unknownList");
      assertThat(unknown.name()).isEqualTo("unknown[]");
      assertThat(unknown.parameter(0).typeGroup()).isEqualTo(TypeGroup.OTHER);
    }

    @Test
    void describesMapsAsTheRecordTypeAlias() {
      TypeMetadata drivers = fieldType(Car.class, "driversMap");
      assertThat(drivers.name()).isEqualTo("Record<string, User>");
      assertThat(drivers.typeGroup()).isEqualTo(TypeGroup.TYPE);
      assertThat(drivers.typeName()).isNull();
      assertThat(drivers.type()).isNull();
      assertThat(drivers.parameters()).extracting(TypeMetadata::typeGroup)
          .containsExactly(TypeGroup.STRING, TypeGroup.CLASS);
      assertThat(fieldType(Fixtures.Test.class, "record").name()).isEqualTo("Record<string, unknown>");
    }

    @Test
    void describesGenericInstantiationsAsObject() {
      TypeMetadata generic = fieldType(Fixtures.Test.class, "generic");
      assertThat(generic.name()).isEqualTo("GenericClass<string>");
      assertThat(generic.typeGroup()).isEqualTo(TypeGroup.OBJECT);
      assertThat(generic.typeName()).isEqualTo("GenericClass");
      assertThat(generic.type()).isEqualTo(GenericClass.class);
      assertThat(generic.parameters()).extracting(TypeMetadata::typeGroup).containsExactly(TypeGroup.STRING);
    }

    @Test
    void describesUnresolvedTypeVariablesAsOther() {
      TypeMetadata value = fieldType(GenericClass.class, "genericValue");
      assertThat(value.name()).isEqualTo("T");
      assertThat(value.typeGroup()).isEqualTo(TypeGroup.OTHER);
      assertThat(value.typeName()).isNull();
    }

    @Test
    void resolvesTypeVariablesBoundBySubclasses() {
      ClassMetadata box = ClassMetadata.of(StringBox.class);
      assertThat(box.fields()).extracting(PropertyMetadata::name).containsExactly("genericValue", "size");
      assertThat(box.field("genericValue").orElseThrow().typeInfo().name()).isEqualTo("string");
      assertThat(box.field("genericValue").orElseThrow().typeInfo().typeGroup()).isEqualTo(TypeGroup.STRING);
    }

    @Test
    void describesSealedHierarchiesAsUnions() {
      TypeMetadata union = fieldType(Fixtures.Test.class, "union");
      assertThat(union.name()).isEqualTo("Vehicle");
      assertThat(union.typeGroup()).isEqualTo(TypeGroup.UNION);
      assertThat(union.typeName()).isNull();
      assertThat(union.importPath()).isNull();
      assertThat(union.parameters()).extracting(p -> (Object) p.type()).containsExactly(Bike.class, Truck.class);
      assertThat(union.parameters()).extracting(TypeMetadata::typeGroup).containsOnly(TypeGroup.CLASS);
    }

    @Test
    void describesFunctionalInterfacesAsFunctions() {
      TypeMetadata function = fieldType(Fixtures.Test.class, "func0");
      assertThat(function.typeGroup()).isEqualTo(TypeGroup.FUNCTION);
      assertThat(function.name()).isEqualTo("Function<string, number>");
      assertThat(function.typeName()).isNull();
    }

    @Test
    void describesOtherInterfacesAsInterface() {
      TypeMetadata shape = fieldType(Fixtures.Test.class, "shape");
      assertThat(shape.typeGroup()).isEqualTo(TypeGroup.INTERFACE);
      assertThat(shape.name()).isEqualTo("Shape");
      assertThat(shape.typeName()).isNull();
      assertThat(shape.type()).isNull();
    }

    @Test
    void describesFuturesAsPromises() {
      MethodMetadata autoPilot = ClassMetadata.of(Car.class).method("engageAutoPilot").orElseThrow();
      assertThat(autoPilot.typeInfo().name()).isEqualTo("Promise<boolean>");
      assertThat(autoPilot.typeInfo().typeGroup()).isEqualTo(TypeGroup.OBJECT);
      assertThat(autoPilot.typeInfo().typeName()).isEqualTo("Promise");
      assertThat(autoPilot.typeInfo().isPromise()).isTrue();
      assertThat(autoPilot.typeInfo().parameter(0).typeGroup()).isEqualTo(TypeGroup.BOOLEAN);
      assertThat(autoPilot.isStatic()).isFalse();
    }

    @Test
    void stopsDescribingTypeArgumentsAfterTheDepthLimit() {
      TypeMetadata current = fieldType(Deep.class, "deep");
      int depth = 0;
      while (current.typeGroup() == TypeGroup.ARRAY) {
        current = current.parameter(0);
        depth++;
      }
      assertThat(depth).isEqualTo(MetadataExtractors.DEPTH_LIMIT + 1);
      assertThat(current.typeGroup()).isEqualTo(TypeGroup.OTHER);
      assertThat(current.name()).isEqualTo("string[]");
      assertThat(current.parameters()).isEmpty();
    }

    @Test
    void marksJdkAndRegisteredPackagesAsExternal() {
      TypeMetadata day = fieldType(Fixtures.Test.class, "day");
      assertThat(day.typeGroup()).isEqualTo(TypeGroup.CLASS);
      assertThat(day.importPath()).isEqualTo("java.time");
      assertThat(day.isExternal()).isTrue();
      assertThat(ExternalTypes.isExternal(LocalDate.class)).isTrue();
      TypeMetadata inherited = fieldType(Fixtures.Test.class, "inheritedProps");
      assertThat(inherited.isExternal()).isFalse();
      ExternalTypes.addExternalPackagePrefix("com.boostercloud.metadata.fixtures");
      assertThat(inherited.isExternal()).isTrue();
    }
  }

  @Nested
  class Nullability {
    @Test
    void unwrapsOptionalIntoANullableType() {
      TypeMetadata optional = fieldType(Fixtures.Test.class, "optionalString");
      assertThat(optional.name()).isEqualTo("string");
      assertThat(optional.typeGroup()).isEqualTo(TypeGroup.STRING);
      assertThat(optional.isNullable()).isTrue();
      assertThat(optional.javaType().getTypeName()).isEqualTo("java.util.Optional<java.lang.String>");
    }

    @Test
    void optionalDoesNotPropagateNullabilityToTypeArguments() {
      TypeMetadata optionalList = fieldType(Fixtures.Test.class, "optionalList");
      assertThat(optionalList.name()).isEqualTo("string[]");
      assertThat(optionalList.isNullable()).isTrue();
      assertThat(optionalList.parameter(0).isNullable()).isFalse();
    }

    @Test
    void nullableDeclarationsPropagateToTypeArgumentsLikeTheTypeScriptQuestionMark() {
      TypeMetadata nullableList = fieldType(Fixtures.Test.class, "nullableList");
      assertThat(nullableList.isNullable()).isTrue();
      assertThat(nullableList.parameter(0).isNullable()).isTrue();
    }

    @Test
    void typeUseNullableAnnotationsOnTypeArgumentsOnlyAffectThatArgument() {
      TypeMetadata list = fieldType(Fixtures.Test.class, "listOfNullable");
      assertThat(list.isNullable()).isFalse();
      assertThat(list.parameter(0).isNullable()).isTrue();
      assertThat(fieldType(Fixtures.Test.class, "typeUseNullable").isNullable()).isTrue();
    }

    @Test
    void recordComponentsHonourNullableAnnotations() {
      TypeMetadata tags = fieldType(PersonReadModel.class, "tags");
      assertThat(tags.isNullable()).isTrue();
      assertThat(tags.parameter(0).isNullable()).isTrue();
      assertThat(fieldType(PersonReadModel.class, "id").isNullable()).isFalse();
    }
  }

  @Nested
  class ClassLevel {
    @Test
    void listsRecordComponentsInOrderFollowedByCalculatedFields() {
      ClassMetadata person = ClassMetadata.of(PersonReadModel.class);
      assertThat(person.name()).isEqualTo("PersonReadModel");
      assertThat(person.type()).isEqualTo(PersonReadModel.class);
      assertThat(person.isRecord()).isTrue();
      assertThat(person.fields()).extracting(PropertyMetadata::name)
          .containsExactly("id", "firstName", "lastName", "tags", "fullName", "initials");
      assertThat(person.dataFields()).extracting(PropertyMetadata::name)
          .containsExactly("id", "firstName", "lastName", "tags");
      assertThat(person.calculatedFields()).extracting(PropertyMetadata::name).containsExactly("fullName", "initials");
    }

    @Test
    void marksCalculatedFieldsAsGetAccessorsWithTheirDependencies() {
      ClassMetadata person = ClassMetadata.of(PersonReadModel.class);
      PropertyMetadata fullName = person.field("fullName").orElseThrow();
      assertThat(fullName.typeInfo().isGetAccessor()).isTrue();
      assertThat(fullName.typeInfo().name()).isEqualTo("string");
      assertThat(fullName.dependencies()).containsExactly("firstName", "lastName");
      assertThat(fullName.kind()).isEqualTo(PropertyMetadata.Kind.CALCULATED);
      PropertyMetadata initials = person.field("initials").orElseThrow();
      assertThat(initials.dependencies()).isEmpty();
      assertThat(initials.typeInfo().parameter(0).isGetAccessor()).isTrue();
      assertThat(person.field("id").orElseThrow().typeInfo().isGetAccessor()).isFalse();
      assertThat(person.field("id").orElseThrow().dependencies()).isEmpty();
    }

    @Test
    void listsOwnStaticAndInstanceMethodsButNotRecordAccessorsNorCalculatedFields() {
      ClassMetadata person = ClassMetadata.of(PersonReadModel.class);
      assertThat(person.methods()).extracting(MethodMetadata::name).containsExactly("handle", "noResult", "greeting");
      MethodMetadata handle = person.method("handle").orElseThrow();
      assertThat(handle.isStatic()).isTrue();
      assertThat(handle.typeInfo().name()).isEqualTo("Promise<boolean>");
      assertThat(handle.parameters()).extracting(PropertyMetadata::name).containsExactly("model", "reason");
      assertThat(handle.parameters().get(0).typeInfo().type()).isEqualTo(PersonReadModel.class);
      assertThat(handle.parameters().get(0).kind()).isEqualTo(PropertyMetadata.Kind.PARAMETER);
    }

    @Test
    void listsInheritedFieldsRootFirstIncludingPrivateOnes() {
      ClassMetadata chihuahua = ClassMetadata.of(Chihuahua.class);
      assertThat(chihuahua.fields()).extracting(PropertyMetadata::name).containsExactly("animalProp", "dogProp");
      assertThat(chihuahua.methods()).isEmpty();
    }

    @Test
    void skipsStaticAndTransientFields() {
      assertThat(ClassMetadata.of(Fixtures.Test.class).fields()).extracting(PropertyMetadata::name)
          .doesNotContain("ignoredStatic", "ignoredTransient")
          .startsWith("array0", "array1", "union");
    }

    @Test
    void keepsTheSourceDeclarationOrderOfFieldsAndMethods() {
      ClassMetadata ordered = ClassMetadata.of(Ordered.class);
      assertThat(ordered.fields()).extracting(PropertyMetadata::name).containsExactly("zeta", "alpha", "mike", "bravo");
      assertThat(ordered.methods()).extracting(MethodMetadata::name).containsExactly("third", "first", "second");
    }

    @Test
    void doesNotListTheImplicitEnumMethods() {
      assertThat(ClassMetadata.of(Size.class).methods()).extracting(MethodMetadata::name).containsExactly("lower");
    }

    @Test
    void cachesTheMetadataOfEachClass() {
      assertThat(ClassMetadata.of(Car.class)).isSameAs(ClassMetadata.of(Car.class));
    }

    @Test
    void producesTheSameMetadataForTheSameClassEveryTime() {
      assertThat(MetadataExtractors.classMetadataOf(Fixtures.Test.class).toString())
          .isEqualTo(ClassMetadata.of(Fixtures.Test.class).toString());
    }

    @Test
    void rejectsTypesWithoutAName() {
      Object anonymous = new Object() {};
      assertThatThrownBy(() -> ClassMetadata.of(anonymous.getClass()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Could not extract typeName");
      assertThatThrownBy(() -> ClassMetadata.of(int.class)).isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> ClassMetadata.of(String[].class)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidCalculatedFields() {
      assertThatThrownBy(() -> ClassMetadata.of(Fixtures.InvalidCalculated.class))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("InvalidCalculated.nope");
    }
  }

  @Nested
  class Values {
    @Test
    void readsRecordComponentsFieldsAndCalculatedFields() {
      PersonReadModel person = new PersonReadModel("1", "Ada", "Lovelace", List.of("x"));
      ClassMetadata metadata = ClassMetadata.of(PersonReadModel.class);
      assertThat(metadata.field("firstName").orElseThrow().readValue(person)).isEqualTo("Ada");
      assertThat(metadata.field("fullName").orElseThrow().readValue(person)).isEqualTo("Ada Lovelace");
      Chihuahua dog = new Chihuahua();
      assertThat(ClassMetadata.of(Chihuahua.class).field("dogProp").orElseThrow().readValue(dog)).isNull();
    }

    @Test
    void propagatesExceptionsThrownByAccessorsUnchanged() {
      PropertyMetadata value = ClassMetadata.of(Exploding.class).field("value").orElseThrow();
      assertThatThrownBy(() -> value.readValue(new Exploding("x")))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("boom");
      PropertyMetadata broken = ClassMetadata.of(Fixtures.ThrowingCalculated.class).field("broken").orElseThrow();
      assertThatThrownBy(() -> broken.readValue(new Fixtures.ThrowingCalculated()))
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessage("calculated failure v");
    }

    @Test
    void findsAnnotationsPropagatedFromRecordComponentsToTheirFields() {
      ClassMetadata annotated = ClassMetadata.of(Annotated.class);
      assertThat(annotated.field("marked").orElseThrow().getAnnotation(Fixtures.FieldOnly.class)).isNotNull();
      assertThat(annotated.field("marked").orElseThrow().hasAnnotationNamed("FieldOnly")).isTrue();
      assertThat(annotated.field("plain").orElseThrow().getAnnotation(Fixtures.FieldOnly.class)).isNull();
    }
  }

  @Nested
  class Names {
    @Test
    void computesTypeScriptCompatibleNames() throws NoSuchFieldException {
      record Holder(
          List<Map<String, List<Integer>>> nested,
          Optional<List<Optional<String>>> optionals,
          CompletableFuture<Void> nothing,
          GenericClass<Car> genericCar) {}
      assertThat(MetadataExtractors.tsName(Holder.class.getDeclaredField("nested").getGenericType()))
          .isEqualTo("Record<string, number[]>[]");
      assertThat(MetadataExtractors.tsName(Holder.class.getDeclaredField("optionals").getGenericType()))
          .isEqualTo("string[]");
      assertThat(MetadataExtractors.tsName(Holder.class.getDeclaredField("nothing").getGenericType()))
          .isEqualTo("Promise<void>");
      assertThat(MetadataExtractors.tsName(Holder.class.getDeclaredField("genericCar").getGenericType()))
          .isEqualTo("GenericClass<Car>");
    }

    @Test
    void exposesTheTypeScriptLiteralOfEachTypeGroup() {
      assertThat(TypeGroup.READONLY_ARRAY.tsName()).isEqualTo("ReadonlyArray");
      assertThat(TypeGroup.CLASS.toString()).isEqualTo("Class");
      assertThat(TypeGroup.fromTsName("Union")).isEqualTo(TypeGroup.UNION);
      assertThatThrownBy(() -> TypeGroup.fromTsName("Nope")).isInstanceOf(IllegalArgumentException.class);
      assertThat(TypeGroup.values()).hasSize(14);
    }
  }
}
