package com.boostercloud.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.metadata.fixtures.Fixtures.GenericClass;
import com.boostercloud.metadata.fixtures.Fixtures.StringBox;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The generic type utilities exposed to the binding code of other modules. */
class TypesTest {

  static final class Tags extends ArrayList<String> {}

  record Page<T>(List<T> items) {}

  @Test
  void resolvesTypeArgumentsThroughTheHierarchy() {
    assertThat(Types.typeArguments(Tags.class, Iterable.class)).containsExactly(String.class);
    ParameterizedType mapType = Types.parameterized(Map.class, String.class, Integer.class);
    assertThat(Types.typeArguments(mapType, Map.class)).containsExactly(String.class, Integer.class);
    assertThat(Types.typeArguments(List.class, Iterable.class)).containsExactly(Object.class);
  }

  @Test
  void bindsTheTypeVariablesOfParameterizedTypesAndSuperclasses() {
    ParameterizedType pageOfStrings = Types.parameterized(Page.class, String.class);
    Map<TypeVariable<?>, Type> bindings = Types.bindings(pageOfStrings);
    Type itemsType = Page.class.getRecordComponents()[0].getGenericType();
    Type resolved = Types.resolve(itemsType, bindings);
    assertThat(resolved).isEqualTo(Types.parameterized(List.class, String.class));
    assertThat(Types.bindings(StringBox.class)).containsValue(String.class);
    assertThat(Types.rawClass(pageOfStrings)).isEqualTo(Page.class);
    assertThat(Types.rawClass(GenericClass.class.getTypeParameters()[0])).isNull();
  }

  @Test
  void validatesTheNumberOfTypeArguments() {
    assertThatThrownBy(() -> Types.parameterized(Map.class, String.class)).isInstanceOf(IllegalArgumentException.class);
    assertThat(Types.parameterized(List.class, String.class).toString()).isEqualTo("java.util.List<java.lang.String>");
  }
}
