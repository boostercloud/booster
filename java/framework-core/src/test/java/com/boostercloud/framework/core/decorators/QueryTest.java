package com.boostercloud.framework.core.decorators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.core.Booster;
import com.boostercloud.framework.core.BoosterAuthorizer;
import com.boostercloud.framework.core.BoosterTestSupport;
import com.boostercloud.framework.types.concepts.QueryBeforeFunction;
import com.boostercloud.framework.types.concepts.QueryInfo;
import com.boostercloud.framework.types.concepts.QueryMetadata;
import com.boostercloud.framework.types.envelope.UserEnvelope;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@code @Query} (no TypeScript test). */
class QueryTest extends BoosterTestSupport {

  public static class Hook implements QueryBeforeFunction {
    @Override
    public Map<String, Object> apply(Map<String, Object> input, UserEnvelope currentUser) {
      return input;
    }
  }

  @Query(authorize = All.class, before = Hook.class)
  public record CartTotalQuantity(String cartId) {
    public static double handle(CartTotalQuantity query, QueryInfo queryInfo) {
      return 0;
    }
  }

  @Query
  public record WithoutHandle(String cartId) {}

  @Test
  void registersTheQuery() {
    Decorators.registerClass(CartTotalQuantity.class);

    QueryMetadata metadata = Booster.config().queryHandlers.get("CartTotalQuantity");
    assertThat(metadata.type()).isEqualTo(CartTotalQuantity.class);
    assertThat(metadata.authorizer()).isSameAs(BoosterAuthorizer.ALLOW_ACCESS);
    assertThat(metadata.before()).singleElement().isInstanceOf(Hook.class);
    assertThat(metadata.properties()).singleElement().satisfies(property -> assertThat(property.name()).isEqualTo("cartId"));
    assertThat(metadata.methods()).singleElement().satisfies(method -> {
      assertThat(method.name()).isEqualTo("handle");
      assertThat(method.typeInfo().name()).isEqualTo("number");
    });
  }

  @Test
  void failsWhenRegisteredTwiceOrWithoutHandle() {
    Decorators.registerClass(CartTotalQuantity.class);

    assertThatThrownBy(() -> Decorators.registerClass(CartTotalQuantity.class))
        .hasMessage("A query called CartTotalQuantity is already registered.\n"
            + "        If you think that this is an error, try performing a clean build.");
    assertThatThrownBy(() -> Decorators.registerClass(WithoutHandle.class))
        .hasMessage("The query WithoutHandle must declare a static method called \"handle\"");
  }
}
