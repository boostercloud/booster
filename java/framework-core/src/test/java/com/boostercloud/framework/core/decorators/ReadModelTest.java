package com.boostercloud.framework.core.decorators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.BoosterAuthorizer;
import com.boostercloud.framework.core.BoosterTestSupport;
import com.boostercloud.framework.types.concepts.JoinKey;
import com.boostercloud.framework.types.concepts.ProjectionMetadata;
import com.boostercloud.framework.types.concepts.ProjectionResult;
import com.boostercloud.framework.types.concepts.ReadModelAuthorizer;
import com.boostercloud.framework.types.concepts.ReadModelBeforeFunction;
import com.boostercloud.framework.types.concepts.ReadModelJoinKeyFunction;
import com.boostercloud.framework.types.concepts.ReadModelMetadata;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.ReadModelRequestEnvelope;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.framework.types.errors.NotAuthorizedError;
import com.boostercloud.metadata.PropertyMetadata;
import com.boostercloud.metadata.TypeGroup;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Port of {@code test/decorators/read-model.test.ts}. */
class ReadModelTest extends BoosterTestSupport {

  @ReadModel
  public record Post(UUID id, String title) {}

  public static class Filter implements ReadModelBeforeFunction {
    @Override
    public ReadModelRequestEnvelope apply(ReadModelRequestEnvelope envelope, UserEnvelope currentUser) {
      return envelope;
    }
  }

  @ReadModel(before = Filter.class)
  public record FilteredPost(UUID id) {}

  @ReadModel(authorize = All.class)
  public record SomeReadModel(UUID id, String aStringProp, double aNumberProp, List<String> aReadonlyArray) {
    @Projects(entity = SomeEntity.class, joinKey = "id")
    public static ProjectionResult<SomeReadModel> observeSomeEntity(SomeEntity entity, SomeReadModel current) {
      return ProjectionResult.nothing();
    }
  }

  @Entity
  public record SomeEntity(UUID id, List<UUID> readModelIds) {}

  @Role
  public static class Admin {}

  @ReadModel(authorize = Admin.class)
  public record AdminReadModel(UUID id) {}

  public static class RockAuthorizer implements ReadModelAuthorizer {
    @Override
    public void authorize(UserEnvelope currentUser, ReadModelRequestEnvelope readModelRequestEnvelope) {
      Object permissions = currentUser.claims().get("permissions");
      if (!(permissions instanceof List<?> list) || !list.contains("Rock")) {
        throw new NotAuthorizedError("No rock");
      }
    }
  }

  @ReadModel(authorize = RockAuthorizer.class)
  public record RockReadModel(UUID id) {}

  @ReadModel(authorize = All.class)
  public record SequencedReadModel(UUID id, @SequencedBy String timestamp) {}

  @ReadModel(authorize = All.class)
  public record PersonReadModel(UUID id, String firstName, String lastName) {
    @CalculatedField(dependsOn = {"firstName", "lastName"})
    public String fullName() {
      return firstName + " " + lastName;
    }
  }

  public static class ByIds implements ReadModelJoinKeyFunction<SomeEntity> {
    @Override
    public Map<String, Object> joinKey(SomeEntity entity) {
      return Map.of("id", Map.of("in", entity.readModelIds()));
    }
  }

  @ReadModel(authorize = All.class)
  public record ProductReadModel(UUID id) {
    @Projects(entity = SomeEntity.class, joinKey = "id", unProject = "unProjectWithEntity")
    @Projects(entity = Post.class, joinKey = "id", unProject = "updateWithPost")
    public static ProjectionResult<ProductReadModel> updateWithEntity(SomeEntity entity, ProductReadModel current) {
      return ProjectionResult.nothing();
    }

    @Projects(entity = SomeEntity.class, joinKeyFunction = ByIds.class)
    public static ProjectionResult<ProductReadModel> updateWithFunction(SomeEntity entity, UUID id, ProductReadModel current) {
      return ProjectionResult.nothing();
    }

    public static ProjectionResult<ProductReadModel> unProjectWithEntity(SomeEntity entity, ProductReadModel current) {
      return ProjectionResult.delete();
    }

    public static ProjectionResult<ProductReadModel> updateWithPost(Post post, ProductReadModel current) {
      return ProjectionResult.nothing();
    }
  }

  public record NoJoinKey(UUID id) {
    @Projects(entity = SomeEntity.class)
    public static ProjectionResult<NoJoinKey> project(SomeEntity entity) {
      return ProjectionResult.nothing();
    }
  }

  public record BothJoinKeys(UUID id) {
    @Projects(entity = SomeEntity.class, joinKey = "id", joinKeyFunction = ByIds.class)
    public static ProjectionResult<BothJoinKeys> project(SomeEntity entity) {
      return ProjectionResult.nothing();
    }
  }

  public record MissingUnProjection(UUID id) {
    @Projects(entity = SomeEntity.class, joinKey = "id", unProject = "nope")
    public static ProjectionResult<MissingUnProjection> project(SomeEntity entity) {
      return ProjectionResult.nothing();
    }
  }

  @Test
  void registersTheReadModelWithItsProperties() {
    Decorators.registerClass(Post.class);

    ReadModelMetadata metadata = Booster.config().readModels.get("Post");
    assertThat(metadata.type()).isEqualTo(Post.class);
    assertThat(metadata.authorizer()).isSameAs(BoosterAuthorizer.DENY_ACCESS);
    assertThat(metadata.before()).isEmpty();
    assertThat(metadata.properties()).extracting(PropertyMetadata::name).containsExactly("id", "title");
    PropertyMetadata id = metadata.properties().get(0);
    assertThat(id.typeInfo().name()).isEqualTo("UUID");
    assertThat(id.typeInfo().typeGroup()).isEqualTo(TypeGroup.CLASS);
    assertThat(id.typeInfo().typeName()).isEqualTo("UUID");
    assertThat(id.typeInfo().type()).isEqualTo(UUID.class);
    assertThat(id.typeInfo().isNullable()).isFalse();
    assertThat(id.typeInfo().isGetAccessor()).isFalse();
    PropertyMetadata title = metadata.properties().get(1);
    assertThat(title.typeInfo().name()).isEqualTo("string");
    assertThat(title.typeInfo().typeGroup()).isEqualTo(TypeGroup.STRING);
    assertThat(title.typeInfo().typeName()).isEqualTo("String");
    assertThat(metadata.properties()).allSatisfy(property -> assertThat(property.dependencies()).isEmpty());
  }

  @Test
  void storesTheBeforeHooks() {
    Decorators.registerClass(FilteredPost.class);

    ReadModelMetadata metadata = Booster.config().readModels.get("FilteredPost");
    assertThat(metadata.authorizer()).isSameAs(BoosterAuthorizer.DENY_ACCESS);
    assertThat(metadata.before()).singleElement().isInstanceOf(Filter.class);
  }

  @Test
  void describesNumbersAndArrays() {
    Decorators.registerClass(SomeReadModel.class);

    ReadModelMetadata metadata = Booster.config().readModels.get("SomeReadModel");
    assertThat(metadata.authorizer()).isSameAs(BoosterAuthorizer.ALLOW_ACCESS);
    PropertyMetadata number = metadata.properties().get(2);
    assertThat(number.typeInfo().name()).isEqualTo("number");
    assertThat(number.typeInfo().typeGroup()).isEqualTo(TypeGroup.NUMBER);
    assertThat(number.typeInfo().typeName()).isEqualTo("Number");
    PropertyMetadata array = metadata.properties().get(3);
    assertThat(array.typeInfo().name()).isEqualTo("string[]");
    assertThat(array.typeInfo().typeGroup()).isEqualTo(TypeGroup.ARRAY);
    assertThat(array.typeInfo().parameters()).singleElement()
        .satisfies(parameter -> assertThat(parameter.name()).isEqualTo("string"));
    assertThat(metadata.properties()).allSatisfy(property -> assertThat(property.dependencies()).isEmpty());
  }

  @Test
  void authorizesByRole() {
    Decorators.registerClass(Admin.class);
    Decorators.registerClass(AdminReadModel.class);
    ReadModelAuthorizer authorizer = Booster.config().readModels.get("AdminReadModel").authorizer();

    assertThatThrownBy(() -> authorizer.authorize(UserEnvelope.builder().roles(List.of("User")).build(), null))
        .isInstanceOf(NotAuthorizedError.class);
    assertThatCode(() -> authorizer.authorize(UserEnvelope.builder().roles(List.of("Admin")).build(), null))
        .doesNotThrowAnyException();
  }

  @Test
  void usesACustomAuthorizer() {
    Decorators.registerClass(RockReadModel.class);
    ReadModelAuthorizer authorizer = Booster.config().readModels.get("RockReadModel").authorizer();

    assertThatCode(() -> authorizer.authorize(
        UserEnvelope.builder().claims(Map.of("permissions", List.of("Rock"))).build(), null)).doesNotThrowAnyException();
    assertThatThrownBy(() -> authorizer.authorize(
        UserEnvelope.builder().claims(Map.of("permissions", List.of("Paper"))).build(), null))
        .isInstanceOf(NotAuthorizedError.class);
  }

  @Test
  void registersProjections() {
    Decorators.registerClass(SomeEntity.class);
    Decorators.registerClass(SomeReadModel.class);

    assertThat(Booster.config().projections.get("SomeEntity"))
        .containsExactly(new ProjectionMetadata(SomeReadModel.class, "observeSomeEntity", JoinKey.property("id")));
    assertThat(Booster.config().readModels).containsKey("SomeReadModel");
  }

  @Test
  void registersUnProjectionsAndJoinKeyFunctionsInDeclarationOrder() {
    Decorators.registerClass(ProductReadModel.class);

    List<ProjectionMetadata> projections = Booster.config().projections.get("SomeEntity");
    assertThat(projections).extracting(ProjectionMetadata::methodName).containsExactly("updateWithEntity", "updateWithFunction");
    assertThat(projections.get(1).joinKey()).isInstanceOf(JoinKey.Query.class);
    assertThat(((JoinKey.Query) projections.get(1).joinKey()).function()).isInstanceOf(ByIds.class);
    assertThat(Booster.config().projections.get("Post"))
        .containsExactly(new ProjectionMetadata(ProductReadModel.class, "updateWithEntity", JoinKey.property("id")));
    assertThat(Booster.config().unProjections.get("SomeEntity"))
        .containsExactly(new ProjectionMetadata(ProductReadModel.class, "unProjectWithEntity", JoinKey.property("id")));
    assertThat(Booster.config().unProjections.get("Post"))
        .containsExactly(new ProjectionMetadata(ProductReadModel.class, "updateWithPost", JoinKey.property("id")));
  }

  @Test
  void validatesTheJoinKeys() {
    assertThatThrownBy(() -> Decorators.registerClass(NoJoinKey.class))
        .hasMessage("@Projects on NoJoinKey.project must define either joinKey or joinKeyFunction");
    assertThatThrownBy(() -> Decorators.registerClass(BothJoinKeys.class))
        .hasMessage("@Projects on BothJoinKeys.project defines both joinKey and joinKeyFunction; use only one");
    assertThatThrownBy(() -> Decorators.registerClass(MissingUnProjection.class))
        .hasMessage("@Projects on MissingUnProjection.project names the un-projection method \"nope\", but"
            + " MissingUnProjection has no static method with that name");
  }

  @Test
  void registersTheSequenceKey() {
    Decorators.registerClass(SequencedReadModel.class);

    assertThat(Booster.config().readModelSequenceKeys.get("SequencedReadModel")).isEqualTo("timestamp");
  }

  @Test
  void allowsOnlyOneSequenceKey() {
    Decorators.registerClass(SequencedReadModel.class);

    assertThatThrownBy(() -> Decorators.registerSequenceKey(SequencedReadModel.class, "id"))
        .hasMessage("Error trying to register a sort key named `id` for class `SequencedReadModel`. It already had the"
            + " sort key `timestamp` defined and only one sort key is allowed for each read model.");
  }

  @Test
  void addsTheDependenciesOfCalculatedFields() {
    Decorators.registerClass(PersonReadModel.class);

    List<PropertyMetadata> properties = Booster.config().readModels.get("PersonReadModel").properties();
    assertThat(properties).extracting(PropertyMetadata::name).containsExactly("id", "firstName", "lastName", "fullName");
    assertThat(properties.subList(0, 3)).allSatisfy(property -> {
      assertThat(property.dependencies()).isEmpty();
      assertThat(property.typeInfo().isGetAccessor()).isFalse();
    });
    assertThat(properties.get(3).typeInfo().isGetAccessor()).isTrue();
    assertThat(properties.get(3).dependencies()).containsExactly("firstName", "lastName");
  }

  @Test
  void failsWhenRegisteredTwice() {
    Decorators.registerClass(Post.class);

    assertThatThrownBy(() -> Decorators.registerClass(Post.class))
        .hasMessage("A read model called Post is already registered.\n"
            + "        If you think that this is an error, try performing a clean build.");
  }
}
