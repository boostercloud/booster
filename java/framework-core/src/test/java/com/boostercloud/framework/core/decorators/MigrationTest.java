package com.boostercloud.framework.core.decorators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.BoosterTestSupport;
import com.boostercloud.framework.types.concepts.SchemaMigrationMetadata;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Port of {@code test/decorators/migration.test.ts}. */
class MigrationTest extends BoosterTestSupport {

  public record Product(String name) {}

  public record ProductV1(String name) {}

  public record ProductV2(String name, String field2) {}

  public record ProductV3(String name, String field2, String field3) {}

  public record ProductV4(String name) {}

  public record ProductV5(String name) {}

  public static class WrongVersion {
    @ToVersion(toVersion = 1, fromSchema = ProductV1.class, toSchema = ProductV2.class)
    public ProductV2 change(ProductV1 old) {
      return null;
    }
  }

  public static class MigrateProduct {
    @ToVersion(toVersion = 2, fromSchema = ProductV1.class, toSchema = ProductV2.class)
    public ProductV2 changeField2(ProductV1 old) {
      return null;
    }

    @ToVersion(toVersion = 3, fromSchema = ProductV2.class, toSchema = ProductV3.class)
    public ProductV3 changeField3(ProductV2 old) {
      return null;
    }
  }

  @SchemaMigration(Product.class)
  public static class MigrateProductFrom1To3 {
    @ToVersion(toVersion = 2, fromSchema = ProductV1.class, toSchema = ProductV2.class)
    public ProductV2 changeField2(ProductV1 old) {
      return null;
    }

    @ToVersion(toVersion = 3, fromSchema = ProductV2.class, toSchema = ProductV3.class)
    public ProductV3 changeField3(ProductV2 old) {
      return null;
    }
  }

  @SchemaMigration(Product.class)
  public static class MigrateProductFrom3To5 {
    @ToVersion(toVersion = 5, fromSchema = ProductV4.class, toSchema = ProductV5.class)
    public ProductV5 changeField5(ProductV4 old) {
      return null;
    }

    @ToVersion(toVersion = 4, fromSchema = ProductV3.class, toSchema = ProductV4.class)
    public ProductV4 changeField4(ProductV3 old) {
      return null;
    }
  }

  @SchemaMigration(Product.class)
  public static class DuplicatedVersion {
    @ToVersion(toVersion = 3, fromSchema = ProductV2.class, toSchema = ProductV3.class)
    public ProductV3 changeField3(ProductV2 old) {
      return null;
    }
  }

  @SchemaMigration(Product.class)
  public static class WithoutMigrations {
    public ProductV3 changeField3(ProductV2 old) {
      return null;
    }
  }

  public static class StaticMigration {
    @ToVersion(toVersion = 2, fromSchema = ProductV1.class, toSchema = ProductV2.class)
    public static ProductV2 change(ProductV1 old) {
      return null;
    }
  }

  @Test
  void throwsWhenTheVersionIsLowerThan2() {
    assertThatThrownBy(() -> Decorators.registerClass(WrongVersion.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Migration versions must always be greater than 1");
  }

  @Test
  void collectsTheMigrationMethodsInDeclarationOrder() {
    assertThat(Decorators.migrationMethods(MigrateProduct.class)).containsExactly(
        new SchemaMigrationMetadata(MigrateProduct.class, "changeField2", 2, ProductV1.class, ProductV2.class),
        new SchemaMigrationMetadata(MigrateProduct.class, "changeField3", 3, ProductV2.class, ProductV3.class));
  }

  @Test
  void addsSeveralMigrationClassesToTheSameConcept() {
    Decorators.registerClass(MigrateProductFrom1To3.class);
    Decorators.registerClass(MigrateProductFrom3To5.class);

    assertThat(Booster.config().schemaMigrations).hasSize(1);
    Map<Integer, SchemaMigrationMetadata> productMigrations = Booster.config().schemaMigrations.get("Product");
    assertThat(productMigrations).hasSize(4);
    assertThat(productMigrations.get(2)).isEqualTo(
        new SchemaMigrationMetadata(MigrateProductFrom1To3.class, "changeField2", 2, ProductV1.class, ProductV2.class));
    assertThat(productMigrations.get(3)).isEqualTo(
        new SchemaMigrationMetadata(MigrateProductFrom1To3.class, "changeField3", 3, ProductV2.class, ProductV3.class));
    assertThat(productMigrations.get(4)).isEqualTo(
        new SchemaMigrationMetadata(MigrateProductFrom3To5.class, "changeField4", 4, ProductV3.class, ProductV4.class));
    assertThat(productMigrations.get(5)).isEqualTo(
        new SchemaMigrationMetadata(MigrateProductFrom3To5.class, "changeField5", 5, ProductV4.class, ProductV5.class));
    assertThat(Booster.config().currentVersionFor("Product")).isEqualTo(5);
  }

  @Test
  void throwsWhenAVersionIsDuplicated() {
    Decorators.registerClass(MigrateProductFrom1To3.class);

    assertThatThrownBy(() -> Decorators.registerClass(DuplicatedVersion.class))
        .hasMessage("Found duplicated migration for 'Product' in migration class 'DuplicatedVersion': There is an"
            + " already defined migration for version 3");
  }

  @Test
  void throwsWhenThereAreNoMigrationMethodsLeavingAnEmptyMap() {
    assertThatThrownBy(() -> Decorators.registerClass(WithoutMigrations.class))
        .hasMessage("No migration methods found in this class. Define at least one migration and annotate it with @ToVersion()");
    assertThat(Booster.config().schemaMigrations.get("Product")).isEmpty();
  }

  @Test
  void throwsForStaticMigrationMethods() {
    assertThatThrownBy(() -> Decorators.registerClass(StaticMigration.class))
        .hasMessage("@ToVersion methods must be instance methods: StaticMigration.change");
  }
}
