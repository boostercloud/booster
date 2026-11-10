package com.boostercloud.framework.core.sensor.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.core.sensor.health.healthindicators.BoosterHealthIndicator;
import com.boostercloud.framework.types.sensor.HealthIndicatorConfiguration;
import com.boostercloud.framework.types.sensor.HealthIndicatorMetadata;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Port of {@code test/sensor/health/health-utils.test.ts}. */
class HealthUtilsTest {

  private HealthIndicatorMetadata root;
  private HealthIndicatorMetadata rootChildren1;
  private HealthIndicatorMetadata rootChildren2;
  private HealthIndicatorMetadata rootChildren1Children1;
  private HealthIndicatorMetadata rootChildren1Children2;
  private Map<String, HealthIndicatorMetadata> healthProviders;

  private static HealthIndicatorMetadata indicator(String id) {
    return new HealthIndicatorMetadata(BoosterHealthIndicator.class, new HealthIndicatorConfiguration(id, id, true, true, true));
  }

  @BeforeEach
  void setUp() {
    root = indicator("root");
    rootChildren1 = indicator("root/rootChildren1");
    rootChildren2 = indicator("root/rootChildren2");
    rootChildren1Children1 = indicator("root/rootChildren1/rootChildren1Children1");
    rootChildren1Children2 = indicator("root/rootChildren1/rootChildren1Children2");
    healthProviders = new LinkedHashMap<>();
    for (HealthIndicatorMetadata metadata : List.of(root, rootChildren1, rootChildren2, rootChildren1Children1,
        rootChildren1Children2)) {
      healthProviders.put(metadata.healthIndicatorConfiguration().id, metadata);
    }
  }

  private List<HealthIndicatorMetadata> all() {
    return List.of(root, rootChildren1, rootChildren2, rootChildren1Children1, rootChildren1Children2);
  }

  @Nested
  class IsEnabled {
    @Test
    void allEnabled() {
      for (HealthIndicatorMetadata metadata : all()) {
        assertThat(HealthUtils.isEnabled(metadata, healthProviders)).isTrue();
      }
    }

    @Test
    void aDisabledLeafOnlyDisablesItself() {
      rootChildren1Children1.healthIndicatorConfiguration().enabled = false;

      assertThat(HealthUtils.isEnabled(root, healthProviders)).isTrue();
      assertThat(HealthUtils.isEnabled(rootChildren1, healthProviders)).isTrue();
      assertThat(HealthUtils.isEnabled(rootChildren2, healthProviders)).isTrue();
      assertThat(HealthUtils.isEnabled(rootChildren1Children1, healthProviders)).isFalse();
      assertThat(HealthUtils.isEnabled(rootChildren1Children2, healthProviders)).isTrue();
    }

    @Test
    void aDisabledParentDisablesItsChildren() {
      rootChildren1.healthIndicatorConfiguration().enabled = false;

      assertThat(HealthUtils.isEnabled(root, healthProviders)).isTrue();
      assertThat(HealthUtils.isEnabled(rootChildren1, healthProviders)).isFalse();
      assertThat(HealthUtils.isEnabled(rootChildren2, healthProviders)).isTrue();
      assertThat(HealthUtils.isEnabled(rootChildren1Children1, healthProviders)).isFalse();
      assertThat(HealthUtils.isEnabled(rootChildren1Children2, healthProviders)).isFalse();
    }

    @Test
    void aMissingIntermediateAncestorStopsTheWalk() {
      HealthIndicatorMetadata orphan = indicator("root/missing/orphan");
      root.healthIndicatorConfiguration().enabled = false;
      healthProviders.put("root/missing/orphan", orphan);

      assertThat(HealthUtils.isEnabled(orphan, healthProviders)).isTrue();
    }
  }

  @Nested
  class ShowChildren {
    @Test
    void allShowChildren() {
      for (HealthIndicatorMetadata metadata : all()) {
        assertThat(HealthUtils.showChildren(metadata, healthProviders)).isTrue();
      }
    }

    @Test
    void aLeafWithoutShowChildrenOnlyAffectsItself() {
      rootChildren1Children1.healthIndicatorConfiguration().showChildren = false;

      assertThat(HealthUtils.showChildren(root, healthProviders)).isTrue();
      assertThat(HealthUtils.showChildren(rootChildren1, healthProviders)).isTrue();
      assertThat(HealthUtils.showChildren(rootChildren2, healthProviders)).isTrue();
      assertThat(HealthUtils.showChildren(rootChildren1Children1, healthProviders)).isFalse();
      assertThat(HealthUtils.showChildren(rootChildren1Children2, healthProviders)).isTrue();
    }

    @Test
    void aParentWithoutShowChildrenHidesTheChildrenOfItsDescendants() {
      rootChildren1.healthIndicatorConfiguration().showChildren = false;

      assertThat(HealthUtils.showChildren(root, healthProviders)).isTrue();
      assertThat(HealthUtils.showChildren(rootChildren1, healthProviders)).isFalse();
      assertThat(HealthUtils.showChildren(rootChildren2, healthProviders)).isTrue();
      assertThat(HealthUtils.showChildren(rootChildren1Children1, healthProviders)).isFalse();
      assertThat(HealthUtils.showChildren(rootChildren1Children2, healthProviders)).isFalse();
    }

    @Test
    void anAbsentFlagIsFalse() {
      root.healthIndicatorConfiguration().showChildren = null;

      assertThat(HealthUtils.showChildren(root, healthProviders)).isFalse();
      assertThat(HealthUtils.childHealthProviders(root, healthProviders)).isEmpty();
    }
  }

  @Test
  void metadataFromId() {
    assertThatThrownBy(() -> HealthUtils.metadataFromId(healthProviders, ""))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Unexpected HealthProvider id ");
    assertThatThrownBy(() -> HealthUtils.metadataFromId(healthProviders, "xxx"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("Unexpected HealthProvider id ")
        .hasMessage("Unexpected HealthProvider id xxx");
    for (HealthIndicatorMetadata metadata : all()) {
      assertThat(HealthUtils.metadataFromId(healthProviders, metadata.healthIndicatorConfiguration().id)).isSameAs(metadata);
    }
  }

  @Test
  void parentId() {
    assertThat(HealthUtils.parentId(root)).isEmpty();
    assertThat(HealthUtils.parentId(rootChildren1)).isEqualTo("root");
    assertThat(HealthUtils.parentId(rootChildren2)).isEqualTo("root");
    assertThat(HealthUtils.parentId(rootChildren1Children1)).isEqualTo("root/rootChildren1");
    assertThat(HealthUtils.parentId(rootChildren1Children2)).isEqualTo("root/rootChildren1");
  }

  @Test
  void rootHealthProviders() {
    assertThat(HealthUtils.rootHealthProviders(healthProviders)).containsExactly(root);
  }

  @Test
  void childHealthProviders() {
    assertThat(HealthUtils.childHealthProviders(root, healthProviders)).containsExactly(rootChildren1, rootChildren2);
    assertThat(HealthUtils.childHealthProviders(rootChildren1, healthProviders))
        .containsExactly(rootChildren1Children1, rootChildren1Children2);
    assertThat(HealthUtils.childHealthProviders(rootChildren2, healthProviders)).isEmpty();
    assertThat(HealthUtils.childHealthProviders(rootChildren1Children1, healthProviders)).isEmpty();
    assertThat(HealthUtils.childHealthProviders(rootChildren1Children2, healthProviders)).isEmpty();
  }
}
