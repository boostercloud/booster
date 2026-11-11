package com.boostercloud.framework.core.decorators;

import static org.assertj.core.api.Assertions.assertThat;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.BoosterTestSupport;
import com.boostercloud.framework.types.concepts.Register;
import org.junit.jupiter.api.Test;

/** {@code @NonExposed} (no TypeScript test). */
class NonExposedTest extends BoosterTestSupport {

  @Command(authorize = All.class)
  public record CartTotalQuantity(String cartId, @NonExposed double multiply) {
    public static void handle(CartTotalQuantity command, Register register) {}
  }

  public static class Base {
    @NonExposed
    public String inherited;
  }

  public static class Nested extends Base {
    public String visible;

    @NonExposed
    public double testProperty;

    public final String fromConstructor;

    public Nested(@NonExposed String fromConstructor) {
      this.fromConstructor = fromConstructor;
    }

    @NonExposed
    public String helper() {
      return "";
    }
  }

  @Test
  void registersRecordComponents() {
    Decorators.registerClass(CartTotalQuantity.class);

    assertThat(Booster.config().nonExposedGraphQLMetadataKey.get("CartTotalQuantity")).containsExactly("multiply");
  }

  @Test
  void registersMethodsFieldsAndConstructorParametersOfTheDeclaringClassOnly() {
    Decorators.registerClass(Base.class);
    Decorators.registerClass(Nested.class);

    assertThat(Booster.config().nonExposedGraphQLMetadataKey.get("Base")).containsExactly("inherited");
    assertThat(Booster.config().nonExposedGraphQLMetadataKey.get("Nested"))
        .containsExactly("helper", "testProperty", "fromConstructor");
  }

  @Test
  void appendsWithoutDeduplicating() {
    Decorators.registerNonExposed(Nested.class, "a");
    Decorators.registerNonExposed(Nested.class, "a");

    assertThat(Booster.config().nonExposedGraphQLMetadataKey.get("Nested")).containsExactly("a", "a");
  }
}
