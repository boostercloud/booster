package com.boostercloud.framework.core.decorators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.BoosterTestSupport;
import com.boostercloud.framework.types.DataMigrationParameters;
import com.boostercloud.framework.types.concepts.DataMigrationMetadata;
import com.boostercloud.framework.types.concepts.Register;
import org.junit.jupiter.api.Test;

/** {@code @DataMigration} (no TypeScript test). */
class DataMigrationTest extends BoosterTestSupport {

  @DataMigration(order = 1)
  public static class CartIdDataMigrateV1 {
    public static void start(Register register) {}
  }

  @DataMigration(order = 2)
  public static class WithoutStart {}

  @Test
  void registersTheDataMigration() {
    Decorators.registerClass(CartIdDataMigrateV1.class);

    assertThat(Booster.config().dataMigrationHandlers.get("CartIdDataMigrateV1"))
        .isEqualTo(new DataMigrationMetadata(CartIdDataMigrateV1.class, new DataMigrationParameters(1)));
  }

  @Test
  void failsWhenRegisteredTwiceOrWithoutStart() {
    Decorators.registerClass(CartIdDataMigrateV1.class);

    assertThatThrownBy(() -> Decorators.registerClass(CartIdDataMigrateV1.class))
        .hasMessage("A data migration called CartIdDataMigrateV1 is already registered.\n"
            + "        If you think that this is an error, try performing a clean build.");
    assertThatThrownBy(() -> Decorators.registerClass(WithoutStart.class))
        .hasMessage("The data migration WithoutStart must declare a static method called \"start\"");
  }
}
