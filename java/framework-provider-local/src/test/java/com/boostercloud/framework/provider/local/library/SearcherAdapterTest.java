package com.boostercloud.framework.provider.local.library;

import static com.boostercloud.framework.provider.local.Raw.list;
import static com.boostercloud.framework.provider.local.Raw.map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.provider.local.store.JsRegExp;
import com.boostercloud.framework.types.concepts.UUID;
import java.util.regex.PatternSyntaxException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Port of {@code test/library/searcher-adapter.test.ts}, plus the operators and quirks it does not cover. */
class SearcherAdapterTest {

  @Nested
  class ConvertsSimpleOperators {
    @Test
    void eq() {
      assertThat(SearcherAdapter.queryRecordFor(map("field", map("eq", "one")))).isEqualTo(map("value.field", "one"));
    }

    @Test
    void ne() {
      assertThat(SearcherAdapter.queryRecordFor(map("field", map("ne", "one"))))
          .isEqualTo(map("value.field", map("$ne", "one")));
    }

    @Test
    void lt() {
      assertThat(SearcherAdapter.queryRecordFor(map("field", map("lt", 1)))).isEqualTo(map("value.field", map("$lt", 1)));
    }

    @Test
    void gt() {
      assertThat(SearcherAdapter.queryRecordFor(map("field", map("gt", 1)))).isEqualTo(map("value.field", map("$gt", 1)));
    }

    @Test
    void lte() {
      assertThat(SearcherAdapter.queryRecordFor(map("field", map("lte", 1))))
          .isEqualTo(map("value.field", map("$lte", 1)));
    }

    @Test
    void gte() {
      assertThat(SearcherAdapter.queryRecordFor(map("field", map("gte", 1))))
          .isEqualTo(map("value.field", map("$gte", 1)));
    }

    @Test
    void in() {
      assertThat(SearcherAdapter.queryRecordFor(map("field", map("in", list("one", "two")))))
          .isEqualTo(map("value.field", map("$in", list("one", "two"))));
    }
  }

  @Nested
  class ConvertsOperatorsThatRelyOnRegexes {
    @Test
    void contains() {
      assertThat(SearcherAdapter.queryRecordFor(map("field", map("contains", "one"))))
          .isEqualTo(map("value.field", map("$regex", new JsRegExp("one"))));
    }

    @Test
    void includes() {
      assertThat(SearcherAdapter.queryRecordFor(map("field", map("includes", "one"))))
          .isEqualTo(map("value.field", map("$regex", new JsRegExp("one"))));
    }

    @Test
    void includesWithObjects() {
      assertThat(SearcherAdapter.queryRecordFor(map("parentField", map("includes", map("children1", "abc", "children2",
          2))))).isEqualTo(map("value.parentField", map("$elemMatch", map("children1", "abc", "children2", 2))));
    }

    @Test
    void nestedOperatorWithObjects() {
      assertThat(SearcherAdapter.queryRecordFor(map("parentField", map("children1", map("eq", "one")))))
          .isEqualTo(map("value.parentField.children1", "one"));
    }

    @Test
    void beginsWith() {
      assertThat(SearcherAdapter.queryRecordFor(map("field", map("beginsWith", "one"))))
          .isEqualTo(map("value.field", map("$regex", new JsRegExp("^one"))));
    }

    @Test
    void useAndAsTheDefaultWhereOperator() {
      assertThat(SearcherAdapter.queryRecordFor(map("field", map("contains", "one"), "field2", map("contains", "two"))))
          .isEqualTo(map("value.field", map("$regex", new JsRegExp("one")), "value.field2",
              map("$regex", new JsRegExp("two"))));
    }

    @Test
    void isDefined() {
      assertThat(SearcherAdapter.queryRecordFor(map("field", map("isDefined", true))))
          .isEqualTo(map("value.field", map("$exists", true)));
    }

    @Test
    void isDefinedOnNestedFields() {
      assertThat(SearcherAdapter.queryRecordFor(map("field", map("otherField", map("isDefined", true)))))
          .isEqualTo(map("value.field.otherField", map("$exists", true)));
    }

    @Test
    void isDefinedForComplexFilters() {
      assertThat(SearcherAdapter.queryRecordFor(map("and", list(
          map("id", map("eq", "3")),
          map("mainItem", map("sku", map("eq", "test"))),
          map("or", list(
              map("days", map("isDefined", true)),
              map("items", map("includes", map("sku", "test", "price", map("cents", 1000, "currency", "EUR")))))),
          map("mainItem", map("sku", map("eq", null))),
          map("mainItem", map("price", map("cents", map("ne", null)))))))).isEqualTo(map("$and", list(
              map("value.id", "3"),
              map("value.mainItem.sku", "test"),
              map("$or", list(
                  map("value.days", map("$exists", true)),
                  map("value.items", map("$elemMatch", map("sku", "test", "price", map("cents", 1000, "currency",
                      "EUR")))))),
              map("value.mainItem.sku", null),
              map("value.mainItem.price.cents", map("$ne", null)))));
    }
  }

  @Nested
  class OperatorsAndQuirksNotCoveredByTypeScript {
    @Test
    void regexAndIRegex() {
      assertThat(SearcherAdapter.queryRecordFor(map("f", map("regex", "a.c"))))
          .isEqualTo(map("value.f", map("$regex", new JsRegExp("a.c"))));
      assertThat(SearcherAdapter.queryRecordFor(map("f", map("iRegex", "abc"))))
          .isEqualTo(map("value.f", map("$regex", new JsRegExp("abc", "i"))));
    }

    @Test
    void notCombinator() {
      assertThat(SearcherAdapter.queryRecordFor(map("not", map("foo", map("beginsWith", "bar")))))
          .isEqualTo(map("$not", map("value.foo", map("$regex", new JsRegExp("^bar")))));
    }

    @Test
    void onlyTheFirstOperatorOfAnOperatorObjectIsTranslated() {
      assertThat(SearcherAdapter.queryRecordFor(map("f", map("gte", 1, "lte", 10))))
          .isEqualTo(map("value.f", map("$gte", 1)));
      assertThatThrownBy(() -> SearcherAdapter.queryRecordFor(map("f", map("gte", 1, "contains", 5))))
          .hasMessage("Attempted to perform a contains operation on a non-string");
    }

    @Test
    void arrayOperandsOfSingleValueOperatorsUseTheirFirstElement() {
      assertThat(SearcherAdapter.queryRecordFor(map("f", map("eq", list("a", "b"))))).isEqualTo(map("value.f", "a"));
      assertThat(SearcherAdapter.queryRecordFor(map("f", map("in", "a")))).isEqualTo(map("value.f", map("$in",
          list("a"))));
    }

    @Test
    void regexOperatorsNeedStrings() {
      assertThatThrownBy(() -> SearcherAdapter.queryRecordFor(map("f", map("contains", 1))))
          .hasMessage("Attempted to perform a contains operation on a non-string");
      assertThatThrownBy(() -> SearcherAdapter.queryRecordFor(map("f", map("beginsWith", 1))))
          .hasMessage("Attempted to perform a begins-with operation on a non-string");
      assertThatThrownBy(() -> SearcherAdapter.queryRecordFor(map("f", map("regex", true))))
          .hasMessage("Attempted to perform a regex operation on a non-string");
      assertThatThrownBy(() -> SearcherAdapter.queryRecordFor(map("f", map("iRegex", map()))))
          .hasMessage("Attempted to perform a iRegex operation on a non-string");
      assertThatThrownBy(() -> SearcherAdapter.queryRecordFor(map("f", map("regex", "(")))).isInstanceOf(
          PatternSyntaxException.class);
      // sources are not escaped
      assertThat(SearcherAdapter.queryRecordFor(map("f", map("contains", "a+")))).isEqualTo(map("value.f",
          map("$regex", new JsRegExp("a+"))));
    }

    @Test
    void emptyAndNonObjectFiltersAreNoOps() {
      assertThat(SearcherAdapter.queryRecordFor(map())).isEqualTo(map());
      assertThat(SearcherAdapter.queryRecordFor(map("f", map()))).isEqualTo(map());
      assertThat(SearcherAdapter.queryRecordFor(map("isDefined", true))).isEqualTo(map());
      assertThatThrownBy(() -> SearcherAdapter.queryRecordFor(map("f", null))).isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> SearcherAdapter.queryRecordFor(map("and", map()))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonRawOperandsAreConvertedToJson() {
      assertThat(SearcherAdapter.queryRecordFor(map("id", map("eq", UUID.of("abc"))))).isEqualTo(map("value.id", "abc"));
      assertThat(SearcherAdapter.queryRecordFor(map("id", map("in", list(UUID.of("a"), UUID.of("b"))))))
          .isEqualTo(map("value.id", map("$in", list("a", "b"))));
    }

    @Test
    void deeplyNestedProperties() {
      assertThat(SearcherAdapter.queryRecordFor(map("a", map("b", map("c", map("gt", 1)), "d", map("eq", 2)))))
          .isEqualTo(map("value.a.b.c", map("$gt", 1), "value.a.d", 2));
    }
  }
}
