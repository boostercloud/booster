package com.boostercloud.framework.provider.local.store;

import static com.boostercloud.framework.provider.local.Raw.list;
import static com.boostercloud.framework.provider.local.Raw.map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ModelTest {

  private static final Object U = Undefined.INSTANCE;

  @Nested
  class DeepCopy {
    @Test
    void copiesMapsAndListsDroppingUndefinedAndKeepingOrder() {
      Map<String, Object> source = map("b", 1, "a", list(1, U, map("c", U, "d", "x")), "u", U);
      Object copy = Model.deepCopy(source);
      assertThat(copy).isEqualTo(map("b", 1, "a", list(1, null, map("d", "x"))));
      assertThat(new java.util.ArrayList<Object>(((Map<?, ?>) copy).keySet())).containsExactly("b", "a");
      assertThat(copy).isNotSameAs(source);
    }

    @Test
    void strictKeysDropsOperatorAndDottedKeys() {
      assertThat(Model.deepCopy(map("a", 1, "$set", 2, "b.c", 3, "d", map("$x", 1)), true))
          .isEqualTo(map("a", 1, "d", map()));
    }

    @Test
    void convertsDatesRegularExpressionsFunctionsAndObjects() {
      Instant instant = Instant.parse("2020-01-01T00:00:00.123Z");
      record Point(int x, String y) {}
      Predicate<Object> function = o -> true;
      assertThat(Model.deepCopy(map("date", instant, "re", new JsRegExp("a"), "fn", function, "p", new Point(1, "z"))))
          .isEqualTo(map("date", new NedbDate(instant.toEpochMilli()), "re", map(), "p", map("x", 1, "y", "z")));
    }
  }

  @Nested
  class CheckObject {
    @Test
    void rejectsDollarAndDotKeysRecursively() {
      assertThatThrownBy(() -> Model.checkObject(map("a", map("$b", 1))))
          .isInstanceOf(NedbError.class).hasMessage("Field names cannot begin with the $ character");
      assertThatThrownBy(() -> Model.checkObject(map("a", list(map("b.c", 1)))))
          .isInstanceOf(NedbError.class).hasMessage("Field names cannot contain a .");
    }

    @Test
    void acceptsNedbMarkers() {
      Model.checkObject(map("$$deleted", true, "_id", "x"));
      Model.checkObject(map("$$indexCreated", map("fieldName", "a")));
      Model.checkObject(map("$$indexRemoved", "a"));
      Model.checkObject(map("d", map("$$date", 5)));
      assertThatThrownBy(() -> Model.checkObject(map("$$deleted", false))).isInstanceOf(NedbError.class);
      assertThatThrownBy(() -> Model.checkObject(map("d", map("$$date", "x")))).isInstanceOf(NedbError.class);
    }
  }

  @Nested
  class GetDotValue {
    @Test
    void navigatesObjectsAndArrays() {
      Map<String, Object> doc = map("a", map("b", map("c", 1)), "arr", list(map("x", 1), map("x", 2), map("y", 3)),
          "nested", list(list(1, 2), list(3)), "s", "abc");
      assertThat(Model.getDotValue(doc, "a.b.c")).isEqualTo(1);
      assertThat(Model.getDotValue(doc, "a.b")).isEqualTo(map("c", 1));
      assertThat(Model.getDotValue(doc, "arr.x")).isEqualTo(list(1, 2, U));
      assertThat(Model.getDotValue(doc, "arr.1.x")).isEqualTo(2);
      assertThat(Model.getDotValue(doc, "arr.5.x")).isEqualTo(U);
      assertThat(Model.getDotValue(doc, "missing.x")).isEqualTo(U);
      assertThat(Model.getDotValue(doc, "s.length")).isEqualTo(3);
      assertThat(Model.getDotValue(doc, "nested.0")).isEqualTo(list(1, 2));
    }

    @Test
    void falsyIntermediateValuesGiveUndefined() {
      Map<String, Object> doc = map("zero", 0, "empty", "", "nil", null, "f", false);
      assertThat(Model.getDotValue(doc, "zero.x")).isEqualTo(U);
      assertThat(Model.getDotValue(doc, "nil.x")).isEqualTo(U);
      assertThat(Model.getDotValue(doc, "nil")).isNull();
      assertThat(Model.getDotValue(list(0), "0")).isEqualTo(0);
      // a falsy array element reached through an index is undefined (NeDB's !obj check)
      assertThat(Model.getDotValue(map("a", list(0, 5)), "a.0")).isEqualTo(U);
    }
  }

  @Nested
  class Equality {
    @Test
    void primitivesUseStrictEqualityWithJavaScriptNumbers() {
      assertThat(Model.areThingsEqual(1, 1.0)).isTrue();
      assertThat(Model.areThingsEqual(1L, 1)).isTrue();
      assertThat(Model.areThingsEqual("1", 1)).isFalse();
      assertThat(Model.areThingsEqual(null, null)).isTrue();
      assertThat(Model.areThingsEqual(null, U)).isFalse();
      assertThat(Model.areThingsEqual(U, U)).isFalse();
      assertThat(Model.areThingsEqual(true, true)).isTrue();
    }

    @Test
    void objectsAndArraysAreDeep() {
      assertThat(Model.areThingsEqual(map("a", 1, "b", list(1, map("c", 2))), map("b", list(1, map("c", 2)), "a", 1)))
          .isTrue();
      assertThat(Model.areThingsEqual(map("a", 1), map("a", 1, "b", 2))).isFalse();
      assertThat(Model.areThingsEqual(list(1, 2), list(1, 2))).isTrue();
      assertThat(Model.areThingsEqual(list(1, 2), list(2, 1))).isFalse();
      assertThat(Model.areThingsEqual(list(1), map("0", 1))).isFalse();
      assertThat(Model.areThingsEqual(new NedbDate(5), new NedbDate(5))).isTrue();
      assertThat(Model.areThingsEqual(new NedbDate(5), 5)).isFalse();
    }
  }

  @Nested
  class Ordering {
    @Test
    void sortsMixedTypesInNedbOrder() {
      List<Object> values = new ArrayList<>(list(map("a", 1), list(1), new NedbDate(0), true, "b", 3, null, U, false,
          "a", 2.5, list(0, 9)));
      values.sort(Model::compareThings);
      assertThat(values).containsExactly(U, null, 2.5, 3, "a", "b", false, true, new NedbDate(0), list(0, 9), list(1),
          map("a", 1));
    }

    @Test
    void stringsCompareByUtf16CodeUnits() {
      assertThat(Model.compareThings("Z", "a")).isNegative();
      assertThat(Model.compareThings("a", "B")).isPositive();
      assertThat(Model.compareThings("😀", "￿")).isNegative();
    }

    @Test
    void arraysAndObjectsCompareElementWise() {
      assertThat(Model.compareThings(list(1, 2), list(1, 2, 0))).isNegative();
      assertThat(Model.compareThings(list(1, 3), list(1, 2, 9))).isPositive();
      // objects compare the values of their sorted keys, then the key count
      assertThat(Model.compareThings(map("b", 1, "a", 2), map("a", 2, "b", 0))).isPositive();
      assertThat(Model.compareThings(map("a", 1), map("a", 1, "b", 0))).isNegative();
    }
  }

  @Nested
  class Matching {
    private final Map<String, Object> doc = map(
        "name", "Cart", "age", 5, "price", 9.5, "nil", null, "tags", list("a", "b"),
        "items", list(map("sku", "x", "qty", 1), map("sku", "y", "qty", 3)),
        "nested", map("deep", map("value", 7)), "date", new NedbDate(1000), "empty", list());

    @Test
    void equalityOnFieldsAndDotPaths() {
      assertThat(Model.match(doc, map("name", "Cart"))).isTrue();
      assertThat(Model.match(doc, map("name", "Cart", "age", 6))).isFalse();
      assertThat(Model.match(doc, map("nested.deep.value", 7))).isTrue();
      assertThat(Model.match(doc, map("nested", map("deep", map("value", 7))))).isTrue();
      assertThat(Model.match(doc, map("nested", map("deep", map("value", 7, "x", 1))))).isFalse();
      assertThat(Model.match(doc, map("nil", null))).isTrue();
      assertThat(Model.match(doc, map("missing", null))).isFalse();
      assertThat(Model.match(doc, map())).isTrue();
    }

    @Test
    void arrayFieldsMatchAnyElementUnlessTheQueryIsAnArray() {
      assertThat(Model.match(doc, map("tags", "a"))).isTrue();
      assertThat(Model.match(doc, map("tags", "c"))).isFalse();
      assertThat(Model.match(doc, map("tags", list("a", "b")))).isTrue();
      assertThat(Model.match(doc, map("tags", list("b", "a")))).isFalse();
      assertThat(Model.match(doc, map("items.sku", "y"))).isTrue();
      assertThat(Model.match(doc, map("items.qty", map("$gt", 2)))).isTrue();
      assertThat(Model.match(doc, map("items.qty", map("$gt", 3)))).isFalse();
      assertThat(Model.match(doc, map("tags", new JsRegExp("^b")))).isTrue();
    }

    @Test
    void comparisonOperatorsOnlyCompareSameTypes() {
      assertThat(Model.match(doc, map("age", map("$gt", 4, "$lte", 5)))).isTrue();
      assertThat(Model.match(doc, map("age", map("$lt", 5)))).isFalse();
      assertThat(Model.match(doc, map("age", map("$gte", "1")))).isFalse();
      assertThat(Model.match(doc, map("name", map("$gt", "Car")))).isTrue();
      assertThat(Model.match(doc, map("name", map("$lt", "cart")))).isTrue();
      assertThat(Model.match(doc, map("date", map("$gt", new NedbDate(999))))).isTrue();
      assertThat(Model.match(doc, map("date", map("$gt", 999)))).isFalse();
      assertThat(Model.match(doc, map("missing", map("$lt", 10)))).isFalse();
      assertThat(Model.match(doc, map("nil", map("$lt", 10)))).isFalse();
    }

    @Test
    void neInNinExists() {
      assertThat(Model.match(doc, map("age", map("$ne", 6)))).isTrue();
      assertThat(Model.match(doc, map("missing", map("$ne", 6)))).isTrue();
      assertThat(Model.match(doc, map("missing", map("$ne", null)))).isTrue();
      assertThat(Model.match(doc, map("nil", map("$ne", null)))).isFalse();
      assertThat(Model.match(doc, map("age", map("$in", list(1, 5))))).isTrue();
      assertThat(Model.match(doc, map("age", map("$nin", list(1, 5))))).isFalse();
      assertThat(Model.match(doc, map("tags", map("$in", list("z", "b"))))).isTrue();
      assertThat(Model.match(doc, map("nil", map("$exists", true)))).isTrue();
      assertThat(Model.match(doc, map("missing", map("$exists", false)))).isTrue();
      assertThat(Model.match(doc, map("missing", map("$exists", "")))).isFalse();
      assertThat(Model.match(doc, map("age", map("$exists", 0)))).isFalse();
      // an empty array has no element that exists (NeDB's any-element rule)
      assertThat(Model.match(doc, map("empty", map("$exists", true)))).isFalse();
      assertThatThrownBy(() -> Model.match(doc, map("age", map("$in", 5))))
          .isInstanceOf(NedbError.class).hasMessage("$in operator called with a non-array");
      assertThatThrownBy(() -> Model.match(doc, map("age", map("$nin", 5))))
          .isInstanceOf(NedbError.class).hasMessage("$nin operator called with a non-array");
    }

    @Test
    void regexIsAnUnanchoredSearchOnStrings() {
      assertThat(Model.match(doc, map("name", map("$regex", new JsRegExp("ar"))))).isTrue();
      assertThat(Model.match(doc, map("name", map("$regex", new JsRegExp("^ar"))))).isFalse();
      assertThat(Model.match(doc, map("name", map("$regex", new JsRegExp("CART", "i"))))).isTrue();
      assertThat(Model.match(doc, map("name", map("$regex", java.util.regex.Pattern.compile("C.r"))))).isTrue();
      assertThat(Model.match(doc, map("age", map("$regex", new JsRegExp("5"))))).isFalse();
      assertThatThrownBy(() -> Model.match(doc, map("name", map("$regex", "ar"))))
          .isInstanceOf(NedbError.class).hasMessage("$regex operator called with non regular expression");
    }

    @Test
    void elemMatchAndSize() {
      assertThat(Model.match(doc, map("items", map("$elemMatch", map("sku", "y", "qty", 3))))).isTrue();
      assertThat(Model.match(doc, map("items", map("$elemMatch", map("sku", "y", "qty", 1))))).isFalse();
      assertThat(Model.match(doc, map("items", map("$elemMatch", map("qty", map("$gt", 2)))))).isTrue();
      assertThat(Model.match(doc, map("tags", map("$elemMatch", "b")))).isTrue();
      assertThat(Model.match(doc, map("name", map("$elemMatch", "b")))).isFalse();
      assertThat(Model.match(doc, map("tags", map("$size", 2)))).isTrue();
      assertThat(Model.match(doc, map("tags", map("$size", 1)))).isFalse();
      assertThatThrownBy(() -> Model.match(doc, map("tags", map("$size", 1.5))))
          .isInstanceOf(NedbError.class).hasMessage("$size operator called without an integer");
    }

    @Test
    void logicalOperators() {
      assertThat(Model.match(doc, map("$or", list(map("age", 1), map("name", "Cart"))))).isTrue();
      assertThat(Model.match(doc, map("$and", list(map("age", 5), map("name", "Cart"))))).isTrue();
      assertThat(Model.match(doc, map("$and", list(map("age", 5), map("name", "x"))))).isFalse();
      assertThat(Model.match(doc, map("$not", map("age", 5)))).isFalse();
      assertThat(Model.match(doc, map("$where", (Predicate<Object>) o -> ((Map<?, ?>) o).containsKey("age")))).isTrue();
      assertThat(Model.match(doc, map("$and", list(map("$or", list(map("age", 2), map("age", 5))),
          map("$not", map("tags", "z")))))).isTrue();
      assertThatThrownBy(() -> Model.match(doc, map("$or", map())))
          .isInstanceOf(NedbError.class).hasMessage("$or operator used without an array");
      assertThatThrownBy(() -> Model.match(doc, map("$and", "x")))
          .isInstanceOf(NedbError.class).hasMessage("$and operator used without an array");
      assertThatThrownBy(() -> Model.match(doc, map("$foo", 1)))
          .isInstanceOf(NedbError.class).hasMessage("Unknown logical operator $foo");
    }

    @Test
    void malformedQueries() {
      assertThatThrownBy(() -> Model.match(doc, map("age", map("$gt", 1, "x", 2))))
          .isInstanceOf(NedbError.class).hasMessage("You cannot mix operators and normal fields");
      assertThatThrownBy(() -> Model.match(doc, map("age", map("$foo", 1))))
          .isInstanceOf(NedbError.class).hasMessage("Unknown comparison function $foo");
      assertThatThrownBy(() -> Model.match(doc, map("age", map("$or", list()))))
          .isInstanceOf(NedbError.class).hasMessage("Unknown comparison function $or");
    }

    @Test
    void undefinedQueryValuesNeverMatch() {
      assertThat(Model.match(doc, map("age", U))).isFalse();
      assertThat(Model.match(doc, map("missing", U))).isFalse();
    }
  }

  @Nested
  class Modify {
    private final Map<String, Object> doc = map("_id", "id1", "a", 1, "arr", list(1, 2, 3), "o", map("x", 1));

    @Test
    void replacementKeepsTheId() {
      assertThat(Model.modify(doc, map("b", 2))).isEqualTo(map("b", 2, "_id", "id1"));
      assertThat(Model.modify(doc, map("_id", "id1", "b", 2))).isEqualTo(map("_id", "id1", "b", 2));
      assertThatThrownBy(() -> Model.modify(doc, map("_id", "other")))
          .isInstanceOf(NedbError.class).hasMessage("You cannot change a document's _id");
      assertThatThrownBy(() -> Model.modify(doc, map("$set", map("a", 2), "b", 1)))
          .isInstanceOf(NedbError.class).hasMessage("You cannot mix modifiers and normal fields");
      assertThatThrownBy(() -> Model.modify(doc, map("b", map("$c", 1))))
          .isInstanceOf(NedbError.class).hasMessage("Field names cannot begin with the $ character");
    }

    @Test
    void setUnsetIncMinMax() {
      assertThat(Model.modify(doc, map("$set", map("o.y.z", 5, "a", 2))))
          .isEqualTo(map("_id", "id1", "a", 2, "arr", list(1, 2, 3), "o", map("x", 1, "y", map("z", 5))));
      assertThat(Model.modify(doc, map("$unset", map("o.x", true, "missing.deep", true))))
          .isEqualTo(map("_id", "id1", "a", 1, "arr", list(1, 2, 3), "o", map()));
      assertThat(Model.modify(doc, map("$inc", map("a", 2, "n", 3))).get("a")).isEqualTo(3L);
      assertThat(Model.modify(doc, map("$inc", map("n", 3))).get("n")).isEqualTo(3);
      assertThatThrownBy(() -> Model.modify(doc, map("$inc", map("o", 1))))
          .isInstanceOf(NedbError.class).hasMessage("Don't use the $inc modifier on non-number fields");
      assertThat(Model.modify(doc, map("$max", map("a", 10))).get("a")).isEqualTo(10);
      assertThat(Model.modify(doc, map("$min", map("a", 10))).get("a")).isEqualTo(1);
      assertThatThrownBy(() -> Model.modify(doc, map("$foo", map())))
          .isInstanceOf(NedbError.class).hasMessage("Unknown modifier $foo");
      assertThatThrownBy(() -> Model.modify(doc, map("$set", 5)))
          .isInstanceOf(NedbError.class).hasMessage("Modifier $set's argument must be an object");
      assertThatThrownBy(() -> Model.modify(doc, map("$set", map("_id", "x"))))
          .isInstanceOf(NedbError.class).hasMessage("You can't change a document's _id");
    }

    @Test
    void arrayModifiers() {
      assertThat(Model.modify(doc, map("$push", map("arr", 4))).get("arr")).isEqualTo(list(1, 2, 3, 4));
      assertThat(Model.modify(doc, map("$push", map("new", 4))).get("new")).isEqualTo(list(4));
      assertThat(Model.modify(doc, map("$push", map("arr", map("$each", list(4, 5), "$slice", -2)))).get("arr"))
          .isEqualTo(list(4, 5));
      assertThat(Model.modify(doc, map("$push", map("arr", map("$each", list(4), "$slice", 2)))).get("arr"))
          .isEqualTo(list(1, 2));
      assertThatThrownBy(() -> Model.modify(doc, map("$push", map("a", 1))))
          .isInstanceOf(NedbError.class).hasMessage("Can't $push an element on non-array values");
      assertThat(Model.modify(doc, map("$addToSet", map("arr", 2))).get("arr")).isEqualTo(list(1, 2, 3));
      assertThat(Model.modify(doc, map("$addToSet", map("arr", map("$each", list(3, 4, 4))))).get("arr"))
          .isEqualTo(list(1, 2, 3, 4));
      assertThat(Model.modify(doc, map("$pop", map("arr", 1))).get("arr")).isEqualTo(list(1, 2));
      assertThat(Model.modify(doc, map("$pop", map("arr", -1))).get("arr")).isEqualTo(list(2, 3));
      assertThat(Model.modify(doc, map("$pull", map("arr", map("$gte", 2)))).get("arr")).isEqualTo(list(1));
      assertThat(doc.get("arr")).isEqualTo(list(1, 2, 3));
    }
  }

  @Test
  void jsHelpersFollowJavaScript() {
    assertThat(Js.parseInt("5abc")).isEqualTo(5);
    assertThat(Js.parseInt("  -12")).isEqualTo(-12);
    assertThat(Js.parseInt("abc")).isNaN();
    assertThat(Js.parseInt(7)).isEqualTo(7);
    assertThat(Js.truthy(0)).isFalse();
    assertThat(Js.truthy(Double.NaN)).isFalse();
    assertThat(Js.truthy("")).isFalse();
    assertThat(Js.truthy(list())).isTrue();
    assertThat(Js.toJsString(2.0)).isEqualTo("2");
    assertThat(Js.toJsString(list(1, null, "a"))).isEqualTo("1,,a");
    assertThat(Js.toJsString(map())).isEqualTo("[object Object]");
    assertThat(Js.toNumber("12")).isEqualTo(12);
    assertThat(Js.toNumber("")).isEqualTo(0);
    assertThat(Js.toNumber("1x")).isNaN();
    assertThat(Js.slice(list(1, 2, 3), Double.NaN, Double.NaN)).isEmpty();
    assertThat(Js.slice(list(1, 2, 3), -2, 10)).isEqualTo(list(2, 3));
    assertThat(Js.stringify(U)).isEqualTo("undefined");
    assertThat(Js.stringify("a")).isEqualTo("\"a\"");
  }
}
