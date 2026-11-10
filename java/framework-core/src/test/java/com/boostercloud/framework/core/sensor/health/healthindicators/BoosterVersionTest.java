package com.boostercloud.framework.core.sensor.health.healthindicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.boostercloud.framework.types.BoosterConfig;
import com.boostercloud.framework.types.Level;
import com.boostercloud.framework.types.Logger;
import org.junit.jupiter.api.Test;

/** {@code boosterVersion(config)} (spec 09 §3.9.1; no TypeScript test of its own). */
class BoosterVersionTest {

  @Test
  void isEmptyAndWarnsWhenTheFrameworkVersionIsUnknown() {
    // The tests run from target/classes: no manifest version and no Maven descriptor
    BoosterConfig config = new BoosterConfig("test");
    config.logLevel = Level.DEBUG;
    config.logger = mock(Logger.class);

    assertThat(BoosterVersion.boosterVersion(config)).isEmpty();
    verify(config.logger).warn("[Booster]|boosterVersion: ", "Could not get Booster Version");
  }

  @Test
  void transformsNpmDependencySpecificationsLikeTypeScript() {
    assertThat(BoosterVersion.fromDependencySpecification("^3.4.1")).isEqualTo("3.4.1");
    assertThat(BoosterVersion.fromDependencySpecification("workspace:^4.1.0")).isEqualTo("4.1.0");
    assertThat(BoosterVersion.fromDependencySpecification("file:../boostercloud-framework-core-3.4.1.tgz")).isEqualTo("3.4.1");
    assertThat(BoosterVersion.fromDependencySpecification("3.4.1-beta.2")).isEqualTo("beta.2");
    assertThat(BoosterVersion.fromDependencySpecification("~3.4.1")).isEqualTo("~3.4.1");
  }
}
