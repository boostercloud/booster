package com.boostercloud.framework.core.decorators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.BoosterAuthorizer;
import com.boostercloud.framework.core.BoosterTestSupport;
import com.boostercloud.framework.types.concepts.EntityMetadata;
import com.boostercloud.framework.types.concepts.EventStreamAuthorizer;
import com.boostercloud.framework.types.concepts.ReducerMetadata;
import com.boostercloud.framework.types.concepts.UUID;
import com.boostercloud.framework.types.envelope.EventSearchRequest;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import com.boostercloud.framework.types.errors.NotAuthorizedError;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Port of {@code test/decorators/entity.test.ts}. */
class EntityTest extends BoosterTestSupport {

  @Event
  public record CommentPosted(UUID postID) {
    public UUID entityID() {
      return postID;
    }
  }

  @Event
  public record CommentDeleted(UUID postID) {
    public UUID entityID() {
      return postID;
    }
  }

  @Entity
  public record Comment(UUID id, String content) {
    @Reduces(CommentPosted.class)
    public static Comment react(CommentPosted event, Comment current) {
      return current;
    }
  }

  @Entity(authorizeReadEvents = All.class)
  public record OpenComment(UUID id) {}

  @Role
  public static class Manager {}

  @Entity(authorizeReadEvents = Manager.class)
  public record ManagedComment(UUID id) {}

  public static class OnlyAsdf implements EventStreamAuthorizer {
    @Override
    public void authorize(UserEnvelope currentUser, EventSearchRequest eventSearchRequest) {
      if (!"asdf".equals(currentUser.username())) {
        throw new NotAuthorizedError("nope");
      }
    }
  }

  @Entity(authorizeReadEvents = OnlyAsdf.class)
  public record CustomComment(UUID id) {}

  @Entity
  public record MultiReducer(UUID id) {
    @Reduces(CommentPosted.class)
    @Reduces(CommentDeleted.class)
    public static MultiReducer react(Object event, MultiReducer current) {
      return current;
    }
  }

  public record OtherEntity(UUID id) {
    @Reduces(CommentPosted.class)
    public static OtherEntity react(CommentPosted event, OtherEntity current) {
      return current;
    }
  }

  public record InstanceReducer(UUID id) {
    @Reduces(CommentPosted.class)
    public InstanceReducer react(CommentPosted event) {
      return this;
    }
  }

  @Test
  void addsTheEntityAndItsReducers() {
    Decorators.registerClass(CommentPosted.class);
    Decorators.registerClass(Comment.class);

    EntityMetadata entity = Booster.config().entities.get("Comment");
    assertThat(entity.type()).isEqualTo(Comment.class);
    assertThat(entity.eventStreamAuthorizer()).isSameAs(BoosterAuthorizer.DENY_ACCESS);
    assertThat(Booster.config().reducers.get("CommentPosted")).isEqualTo(new ReducerMetadata(Comment.class, "react"));
  }

  @Test
  void setsTheAllowAuthorizerWithAll() {
    Decorators.registerClass(OpenComment.class);

    assertThat(Booster.config().entities.get("OpenComment"))
        .isEqualTo(new EntityMetadata(OpenComment.class, BoosterAuthorizer.ALLOW_ACCESS));
  }

  @Test
  void setsARolesAuthorizer() {
    Decorators.registerClass(ManagedComment.class);
    EventStreamAuthorizer authorizer = Booster.config().entities.get("ManagedComment").eventStreamAuthorizer();

    assertThat(authorizer).isEqualTo(BoosterAuthorizer.authorizeRoles(List.of(Manager.class)));
    assertThatCode(() -> authorizer.authorize(UserEnvelope.builder().roles(List.of("Manager")).build(), null))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> authorizer.authorize(UserEnvelope.builder().roles(List.of("User")).build(), null))
        .isInstanceOf(NotAuthorizedError.class);
  }

  @Test
  void setsACustomAuthorizer() {
    Decorators.registerClass(CustomComment.class);
    EventStreamAuthorizer authorizer = Booster.config().entities.get("CustomComment").eventStreamAuthorizer();

    assertThatCode(() -> authorizer.authorize(UserEnvelope.builder().username("asdf").build(), null))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> authorizer.authorize(UserEnvelope.builder().username("qwer").build(), null))
        .isInstanceOf(NotAuthorizedError.class);
  }

  @Test
  void aMethodCanReduceSeveralEvents() {
    Decorators.registerClass(MultiReducer.class);

    assertThat(Booster.config().reducers).containsOnlyKeys("CommentPosted", "CommentDeleted");
  }

  @Test
  void reducersRegisterWithoutTheEntityAnnotation() {
    Decorators.registerClass(OtherEntity.class);

    assertThat(Booster.config().reducers.get("CommentPosted").type()).isEqualTo(OtherEntity.class);
    assertThat(Booster.config().entities).isEmpty();
  }

  @Test
  void failsWhenAnEventIsReducedTwice() {
    Decorators.registerClass(Comment.class);

    assertThatThrownBy(() -> Decorators.registerClass(OtherEntity.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Error registering reducer: The event CommentPosted was already registered to be reduced by method"
            + " react in the entity Comment.\n        If you think that this is an error, try performing a clean build.");
  }

  @Test
  void failsWhenTheEntityIsRegisteredTwice() {
    Decorators.registerClass(OpenComment.class);

    assertThatThrownBy(() -> Decorators.registerEntity(OpenComment.class, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("An entity called OpenComment is already registered\n"
            + "        If you think that this is an error, try performing a clean build..");
  }

  @Test
  void failsWhenAReducerIsNotStatic() {
    assertThatThrownBy(() -> Decorators.registerClass(InstanceReducer.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("@Reduces methods must be static: InstanceReducer.react");
  }
}
