package com.boostercloud.framework.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.common.fixturerocket.Rocket;
import com.boostercloud.framework.types.rockets.RocketDescriptor;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Port of {@code packages/framework-common-helpers/test/rocket-loader.test.ts}. */
class RocketLoaderTest {

  private static final RocketDescriptor ROCKET_DESCRIPTOR =
      new RocketDescriptor("some-package-name", Map.of("some", "parameters"));

  /** An infrastructure rocket. */
  public static final class FakeRocket {
    /** Mounts the rocket. */
    public void mountStack() {}
  }

  @Test
  void throwsAnErrorWhenTheRocketInfrastructurePackageIsNotFound() {
    RocketLoader loader = new RocketLoader(packageName -> null);
    assertThatThrownBy(() -> loader.load(ROCKET_DESCRIPTOR))
        .hasMessageContaining("Could not load the rocket infrastructure package")
        .hasMessage("Could not load the rocket infrastructure package some-package-name. "
            + "Make sure you installed it in your project devDependencies.");
  }

  @Test
  void throwsAnErrorWhenThePackageDoesNotImplementABuilderMethod() {
    RocketLoader loader = new RocketLoader(packageName -> Map.of("whatever", true));
    assertThatThrownBy(() -> loader.load(ROCKET_DESCRIPTOR))
        .hasMessageContaining("Could not initialize rocket infrastructure package");
  }

  @Test
  void throwsAnErrorWhenThePackageDoesNotImplementTheInfrastructureRocketInterface() {
    RocketLoader loader = new RocketLoader(packageName -> (RocketBuilder) parameters -> Map.of("whatever", true));
    assertThatThrownBy(() -> loader.load(ROCKET_DESCRIPTOR)).hasMessageMatching("The package.*doesn't seem to be a rocket.*");
    RocketLoader nullRocket = new RocketLoader(packageName -> (RocketBuilder) parameters -> null);
    assertThatThrownBy(() -> nullRocket.load(ROCKET_DESCRIPTOR)).hasMessage("The package some-package-name doesn't seem to be a rocket.");
  }

  @Test
  void returnsTheLoadedRocketProperlyInitializedWhenItPassesAllChecks() {
    FakeRocket rocket = new FakeRocket();
    RocketLoader loader = new RocketLoader(packageName -> (RocketBuilder) parameters -> rocket);
    assertThat(loader.load(ROCKET_DESCRIPTOR)).isSameAs(rocket);
    assertThat(loader.load(ROCKET_DESCRIPTOR, FakeRocket.class)).isSameAs(rocket);
    assertThatThrownBy(() -> loader.load(ROCKET_DESCRIPTOR, String.class)).hasMessageContaining("doesn't seem to be a rocket");
  }

  @Test
  void loadsTheRocketClassOfAPackageAndPassesTheParameters() {
    Object loaded = RocketLoader.loadRocket(new RocketDescriptor("com.boostercloud.framework.common.fixturerocket", "params"));
    assertThat(loaded).isInstanceOf(Rocket.Infrastructure.class);
    assertThat(((Rocket.Infrastructure) loaded).parameters()).isEqualTo("params");
    assertThatThrownBy(() -> RocketLoader.loadRocket(new RocketDescriptor("com.example.nothing", null)))
        .hasMessageContaining("Could not load the rocket infrastructure package com.example.nothing");
    assertThatThrownBy(() -> RocketLoader.loadRocket(new RocketDescriptor("com.boostercloud.framework.common.notarocket", null)))
        .hasMessageContaining("Could not initialize rocket infrastructure package");
  }
}
