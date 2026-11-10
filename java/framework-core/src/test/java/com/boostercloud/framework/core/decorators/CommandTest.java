package com.boostercloud.framework.core.decorators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.BoosterAuthorizer;
import com.boostercloud.framework.core.BoosterTestSupport;
import com.boostercloud.framework.types.concepts.CommandAuthorizer;
import com.boostercloud.framework.types.concepts.CommandBeforeFunction;
import com.boostercloud.framework.types.concepts.CommandMetadata;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.envelope.CommandEnvelope;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Port of {@code test/decorators/command.test.ts}. */
class CommandTest extends BoosterTestSupport {

  public static class FakeAuthorizer implements CommandAuthorizer {
    @Override
    public void authorize(UserEnvelope currentUser, CommandEnvelope commandEnvelope) {}
  }

  public static class Hook implements CommandBeforeFunction {
    @Override
    public Map<String, Object> apply(Map<String, Object> input, UserEnvelope currentUser) {
      return input;
    }
  }

  @Command(authorize = FakeAuthorizer.class)
  public record PostComment(String comment) {
    public static void handle(PostComment command, Register register) {}
  }

  @Command
  public record PostCommentDenied(String comment) {
    public static void handle(PostCommentDenied command, Register register) {}
  }

  @Command(before = Hook.class)
  public record PostCommentWithHook(String comment) {
    public static void handle(PostCommentWithHook command, Register register) {}
  }

  @Command(authorize = All.class)
  public record WithoutHandle(String comment) {}

  @Test
  void addsTheCommandClassAsACommand() {
    Decorators.registerClass(PostComment.class);

    CommandMetadata metadata = Booster.config().commandHandlers.get("PostComment");
    assertThat(metadata.type()).isEqualTo(PostComment.class);
    assertThat(metadata.properties()).hasSize(1);
    assertThat(metadata.properties().getFirst().name()).isEqualTo("comment");
    assertThat(metadata.properties().getFirst().typeInfo().name()).isEqualTo("string");
    assertThat(metadata.methods().getFirst().name()).isEqualTo("handle");
    assertThat(metadata.methods().getFirst().typeInfo().name()).isEqualTo("void");
    assertThat(metadata.authorizer()).isInstanceOf(FakeAuthorizer.class);
    assertThat(metadata.before()).isEmpty();
  }

  @Test
  void setsTheDenyAuthorizerByDefault() {
    Decorators.registerClass(PostCommentDenied.class);

    CommandMetadata metadata = Booster.config().commandHandlers.get("PostCommentDenied");
    assertThat(metadata.authorizer()).isSameAs(BoosterAuthorizer.DENY_ACCESS);
    assertThat(metadata.before()).isEmpty();
  }

  @Test
  void storesTheBeforeHooks() {
    Decorators.registerClass(PostCommentWithHook.class);

    CommandMetadata metadata = Booster.config().commandHandlers.get("PostCommentWithHook");
    assertThat(metadata.authorizer()).isSameAs(BoosterAuthorizer.DENY_ACCESS);
    assertThat(metadata.before()).singleElement().isInstanceOf(Hook.class);
  }

  @Test
  void allowsEverybodyWithAll() {
    Decorators.registerCommand(PostComment.class, annotation(All.class));

    assertThat(Booster.config().commandHandlers.get("PostComment").authorizer()).isSameAs(BoosterAuthorizer.ALLOW_ACCESS);
  }

  @Test
  void failsWhenTheCommandIsRegisteredTwice() {
    Decorators.registerClass(PostComment.class);

    assertThatThrownBy(() -> Decorators.registerClass(PostComment.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("A command called PostComment is already registered.\n"
            + "        If you think that this is an error, try performing a clean build.");
  }

  @Test
  void failsWhenTheCommandHasNoStaticHandle() {
    assertThatThrownBy(() -> Decorators.registerClass(WithoutHandle.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("The command WithoutHandle must declare a static method called \"handle\"");
  }

  private static Command annotation(Class<?> authorize) {
    return new Command() {
      @Override
      public Class<? extends java.lang.annotation.Annotation> annotationType() {
        return Command.class;
      }

      @Override
      public Class<?>[] authorize() {
        return new Class<?>[] {authorize};
      }

      @Override
      @SuppressWarnings("unchecked")
      public Class<? extends CommandBeforeFunction>[] before() {
        return new Class[0];
      }
    };
  }
}
