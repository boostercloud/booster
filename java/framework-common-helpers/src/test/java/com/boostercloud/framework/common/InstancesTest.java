package com.boostercloud.framework.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.boostercloud.framework.types.concepts.DataMigrationStatus;
import com.boostercloud.framework.types.concepts.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Port of {@code packages/framework-common-helpers/test/instances.test.ts}, plus the Java binding rules. */
class InstancesTest {

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  @interface CalculatedField {}

  /** The TypeScript fixture: a read model with an asynchronous calculated {@code fullName}. */
  public record PersonReadModel(UUID id, String firstName, String lastName, List<PersonReadModel> friends) {
    @CalculatedField
    public CompletableFuture<String> fullName() {
      return CompletableFuture.supplyAsync(() -> firstName + " " + lastName);
    }
  }

  public record Address(String city, String street) {}

  public record Tag(String name) {}

  /** {@code Optional<T>} is the Java form of a TypeScript {@code T | undefined}. */
  public record Profile(String id, Optional<Address> address, Optional<List<Tag>> tags, Optional<Address> none,
      Optional<String> nickname, OptionalInt score, OptionalInt noScore, List<Optional<Tag>> maybeTags) {
    @CalculatedField
    public Optional<Address> home() {
      return address;
    }

    @CalculatedField
    public CompletableFuture<Optional<Address>> later() {
      return CompletableFuture.supplyAsync(() -> address);
    }
  }

  private Map<String, Object> rawObject;

  @BeforeEach
  void setUp() {
    rawObject = new LinkedHashMap<>();
    rawObject.put("id", java.util.UUID.randomUUID().toString());
    rawObject.put("firstName", "Ada");
    rawObject.put("lastName", "Lovelace");
    rawObject.put("friends", List.of(
        Map.of("id", java.util.UUID.randomUUID().toString(), "firstName", "Alan", "lastName", "Turing"),
        Map.of("id", java.util.UUID.randomUUID().toString(), "firstName", "Grace", "lastName", "Hopper")));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> friend(int index) {
    return ((List<Map<String, Object>>) rawObject.get("friends")).get(index);
  }

  @Nested
  class TheCreateInstanceWithCalculatedPropertiesMethod {
    @Test
    void createsAnInstanceOfTheReadModelClassWithTheCalculatedPropertiesIncluded() {
      Map<String, Object> instance =
          Instances.createInstanceWithCalculatedProperties(PersonReadModel.class, rawObject, List.of("id", "fullName"));
      assertThat(instance).isEqualTo(Map.of("id", UUID.of((String) rawObject.get("id")), "fullName", "Ada Lovelace"));
    }

    @Test
    void correctlySupportsArraysAndNestedObjectsInPropertiesToInclude() {
      Map<String, Object> instance = Instances.createInstanceWithCalculatedProperties(
          PersonReadModel.class, rawObject, List.of("id", "fullName", "friends[].id"));
      assertThat(instance).isEqualTo(Map.of(
          "id", UUID.of((String) rawObject.get("id")),
          "fullName", "Ada Lovelace",
          "friends", List.of(
              Map.of("id", UUID.of((String) friend(0).get("id"))),
              Map.of("id", UUID.of((String) friend(1).get("id"))))));
    }

    @Test
    void followsTheSelectionOrder() {
      Map<String, Object> instance =
          Instances.createInstanceWithCalculatedProperties(PersonReadModel.class, rawObject, List.of("fullName", "id"));
      assertThat(instance.keySet()).containsExactly("fullName", "id");
    }

    @Test
    void evaluatesCalculatedPropertiesOfNestedInstances() {
      Map<String, Object> instance = Instances.createInstanceWithCalculatedProperties(
          PersonReadModel.class, rawObject, List.of("friends[].fullName"));
      assertThat(instance).isEqualTo(Map.of("friends", List.of(Map.of("fullName", "Alan Turing"), Map.of("fullName", "Grace Hopper"))));
    }

    @Test
    void appliesTheTypeScriptNullRules() {
      record Geo(int lat) {}
      record Address(String city, Geo geo) {}
      record Person(String id, Address address, List<Map<String, Object>> others, List<String> tags, String nothing) {}
      Map<String, Object> raw = new LinkedHashMap<>();
      raw.put("id", "p1");
      raw.put("address", Map.of("city", "London", "geo", Map.of("lat", 1)));
      raw.put("others", List.of(Map.of("id", "f1"), Map.of("id", "f2"), Map.of("other", 1)));
      raw.put("tags", List.of("a", "b"));
      assertThat(project(Person.class, raw, "others[].id")).isEqualTo(Map.of("others", List.of(Map.of("id", "f1"), Map.of("id", "f2"))));
      assertThat(project(Person.class, raw, "tags")).isEqualTo(Map.of("tags", List.of("a", "b")));
      assertThat(project(Person.class, raw, "tags[]")).isEqualTo(Map.of("tags", List.of()));
      assertThat(project(Person.class, raw, "address.city")).isEqualTo(Map.of("address", Map.of("city", "London")));
      assertThat(project(Person.class, raw, "address.geo.lat")).isEqualTo(Map.of("address", Map.of("geo", Map.of("lat", 1))));
      assertThat(project(Person.class, raw, "address.missing")).isEqualTo(nullValued("address"));
      assertThat(project(Person.class, raw, "nothing.x")).isEqualTo(nullValued("nothing"));
      assertThat(project(Person.class, raw, "absent.x")).isEqualTo(nullValued("absent"));
      assertThat(project(Person.class, raw, "absentLeaf")).isEmpty();
      assertThat(project(Person.class, raw, "absentArr[].x")).isEqualTo(nullValued("absentArr"));
    }

    @Test
    void failsOnSelectionsThatTypeScriptCannotEvaluate() {
      record Person(String id, List<String> tags, List<String> missing) {}
      Map<String, Object> raw = Map.of("id", "abc", "tags", List.of("a"));
      assertThatThrownBy(() -> project(Person.class, raw, "missing[].x")).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("not iterable");
      assertThatThrownBy(() -> project(Person.class, raw, "tags[].x")).hasMessageContaining("Cannot use 'in' operator");
      assertThatThrownBy(() -> project(Person.class, raw, "id.x")).hasMessageContaining("Cannot use 'in' operator");
      assertThatThrownBy(() -> Instances.buildPropertiesMap(List.of("items.a", "items[].b")))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unwrapsOptionalNestedObjectsListsAndLeaves() {
      assertThat(project(Profile.class, profile(), "address.city"))
          .isEqualTo(Map.of("address", Map.of("city", "Madrid")));
      assertThat(project(Profile.class, profile(), "tags[].name")).isEqualTo(Map.of("tags", List.of(Map.of("name", "vip"))));
      assertThat(project(Profile.class, profile(), "maybeTags[].name"))
          .isEqualTo(Map.of("maybeTags", List.of(Map.of("name", "new"))));
      assertThat(project(Profile.class, profile(), "nickname")).isEqualTo(Map.of("nickname", "ada"));
      assertThat(project(Profile.class, profile(), "score")).isEqualTo(Map.of("score", 7));
      // An empty optional is null, like a null property (a null array cannot be iterated, as in TypeScript)
      assertThat(project(Profile.class, profile(), "none.city")).isEqualTo(nullValued("none"));
      assertThat(project(Profile.class, profile(), "noScore")).isEqualTo(nullValued("noScore"));
      assertThatThrownBy(() -> project(Profile.class, Map.of("id", "p2"), "tags[].name"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("The property 'tags' is not iterable: null");
    }

    @Test
    void unwrapsOptionalCalculatedFields() {
      assertThat(project(Profile.class, profile(), "home.city")).isEqualTo(Map.of("home", Map.of("city", "Madrid")));
      assertThat(project(Profile.class, profile(), "later.street")).isEqualTo(Map.of("later", Map.of("street", "Gran Via")));
      assertThat(project(Profile.class, Map.of("id", "p2"), "home.city")).isEqualTo(nullValued("home"));
    }

    private Map<String, Object> profile() {
      Map<String, Object> raw = new LinkedHashMap<>();
      raw.put("id", "p1");
      raw.put("address", Map.of("city", "Madrid", "street", "Gran Via"));
      raw.put("tags", List.of(Map.of("name", "vip")));
      raw.put("nickname", "ada");
      raw.put("score", 7);
      raw.put("maybeTags", List.of(Map.of("name", "new")));
      return raw;
    }

    @Test
    void buildsTheSelectionTreeLikeTypeScript() {
      Map<String, Instances.PropertyNode> tree = Instances.buildPropertiesMap(List.of("x", "x.y", "items[].a", "items.b"));
      assertThat(tree.get("x").children).containsOnlyKeys("y");
      assertThat(tree.get("items").array).isTrue();
      assertThat(tree.get("items").arrayChildren).containsOnlyKeys("a");
    }

    private Map<String, Object> project(Class<?> type, Map<String, Object> raw, String path) {
      return Instances.createInstanceWithCalculatedProperties(type, raw, List.of(path));
    }

    private Map<String, Object> nullValued(String key) {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put(key, null);
      return result;
    }
  }

  @Nested
  class TheCreateInstanceMethod {
    enum Size { SMALL, BIG }

    record Item(String sku, double quantity) {}

    record Cart(UUID id, List<Item> items, Optional<String> coupon, Size size, Instant createdAt, int checks,
        Set<String> tags, Map<String, Item> byId, DataMigrationStatus status, String[] labels) {}

    record Page<T>(List<T> items, int count) {}

    record Pages(Page<Item> items) {}

    public static final class Mutable {
      private String name;
      private int counter = 7;
      private List<Item> items;

      public String name() {
        return name;
      }
    }

    public static final class Immutable {
      private final String title;
      private final Item item;

      public Immutable(String title, Item item) {
        this.title = title;
        this.item = item;
      }
    }

    public static final class WithConstantField {
      private final String fixed = "fixed";
      private String other;
    }

    record Renamed(@JsonProperty("display_name") String displayName) {}

    sealed interface Vehicle permits Bike, Truck {}

    record Bike(int wheels) implements Vehicle {}

    record Truck(double load, int wheels) implements Vehicle {}

    record Garage(List<Vehicle> vehicles) {}

    @Test
    void bindsRecordsWithNestedTypesCollectionsEnumsDatesAndOptionals() {
      Map<String, Object> raw = new LinkedHashMap<>();
      raw.put("id", "cart-1");
      raw.put("items", List.of(Map.of("sku", "a", "quantity", 2)));
      raw.put("coupon", "SALE");
      raw.put("size", "BIG");
      raw.put("createdAt", "2024-01-31T10:20:30.123Z");
      raw.put("checks", 3);
      raw.put("tags", List.of("x", "y", "x"));
      raw.put("byId", Map.of("a", Map.of("sku", "a", "quantity", 1.5)));
      raw.put("status", 1);
      raw.put("labels", List.of("l1"));
      raw.put("unknown", "ignored");
      Cart cart = Instances.createInstance(Cart.class, raw);
      assertThat(cart.id()).isEqualTo(UUID.of("cart-1"));
      assertThat(cart.items()).containsExactly(new Item("a", 2));
      assertThat(cart.coupon()).contains("SALE");
      assertThat(cart.size()).isEqualTo(Size.BIG);
      assertThat(cart.createdAt()).isEqualTo(Instant.parse("2024-01-31T10:20:30.123Z"));
      assertThat(cart.checks()).isEqualTo(3);
      assertThat(cart.tags()).containsExactly("x", "y");
      assertThat(cart.byId()).containsEntry("a", new Item("a", 1.5));
      assertThat(cart.status()).isEqualTo(DataMigrationStatus.FINISHED);
      assertThat(cart.labels()).containsExactly("l1");
    }

    @Test
    void usesAbsentValuesForMissingProperties() {
      Cart cart = Instances.createInstance(Cart.class, Map.of());
      assertThat(cart.id()).isNull();
      assertThat(cart.coupon()).isEmpty();
      assertThat(cart.checks()).isZero();
      assertThat(Instances.createInstance(Cart.class, null).items()).isNull();
    }

    @Test
    void resolvesGenericRecords() {
      Pages pages = Instances.createInstance(Pages.class,
          Map.of("items", Map.of("items", List.of(Map.of("sku", "s", "quantity", 1)), "count", 1)));
      assertThat(pages.items().items()).containsExactly(new Item("s", 1));
    }

    @Test
    void bindsClassesThroughTheirFieldsOrMatchingConstructors() {
      Mutable mutable = Instances.createInstance(Mutable.class, Map.of("name", "n", "items", List.of(Map.of("sku", "k", "quantity", 1))));
      assertThat(mutable.name()).isEqualTo("n");
      assertThat(mutable.counter).isEqualTo(7);
      assertThat(mutable.items).containsExactly(new Item("k", 1));
      Immutable immutable = Instances.createInstance(Immutable.class, Map.of("title", "t", "item", Map.of("sku", "i", "quantity", 2)));
      assertThat(immutable.title).isEqualTo("t");
      assertThat(immutable.item).isEqualTo(new Item("i", 2));
    }

    @Test
    void refusesToOverwriteFinalFieldsWithoutAMatchingConstructor() {
      assertThat(Instances.createInstance(WithConstantField.class, Map.of("other", "o")).other).isEqualTo("o");
      assertThatThrownBy(() -> Instances.createInstance(WithConstantField.class, Map.of("fixed", "changed")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("final field WithConstantField.fixed");
    }

    @Test
    void honoursJsonPropertyRenames() {
      assertThat(Instances.createInstance(Renamed.class, Map.of("display_name", "Ada")).displayName()).isEqualTo("Ada");
    }

    @Test
    void choosesTheImplementationOfSealedTypesFromTheRawKeys() {
      Garage garage = Instances.createInstance(Garage.class,
          Map.of("vehicles", List.of(Map.of("wheels", 2), Map.of("load", 3.5, "wheels", 6))));
      assertThat(garage.vehicles()).containsExactly(new Bike(2), new Truck(3.5, 6));
      assertThatThrownBy(() -> Instances.createInstance(Garage.class, Map.of("vehicles", List.of(Map.of("color", "red")))))
          .hasMessageContaining("Cannot decide which implementation of Vehicle");
    }

    @Test
    void returnsInstancesUnchangedAndConvertsOtherObjects() {
      Item item = new Item("a", 1);
      assertThat(Instances.createInstance(Item.class, item)).isSameAs(item);
      record SameShape(String sku, double quantity) {}
      assertThat(Instances.createInstance(SameShape.class, item)).isEqualTo(new SameShape("a", 1));
    }

    @Test
    void createsManyInstancesInOrder() {
      assertThat(Instances.createInstances(Item.class, List.of(Map.of("sku", "a"), Map.of("sku", "b"))))
          .containsExactly(new Item("a", 0), new Item("b", 0));
    }

    @Test
    void convertsToGenericTypes() throws NoSuchMethodException {
      java.lang.reflect.Type type = TheCreateInstanceMethod.class.getDeclaredMethod("listOfItems").getGenericReturnType();
      assertThat(Instances.convert(List.of(Map.of("sku", "x")), type)).isEqualTo(List.of(new Item("x", 0)));
      assertThat(Instances.convert("12", Integer.class)).isEqualTo(12);
      assertThat(Instances.convert(Arrays.asList("a", null), String[].class)).isEqualTo(new String[] {"a", null});
      assertThatThrownBy(() -> Instances.convert(List.of(1), Item.class))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("an object was expected");
    }

    @Test
    void refusesNumbersWithAFractionForIntegralProperties() {
      // GraphQL has one number type (every Java number is a Float input), so integral values arrive as doubles
      record Counts(int count, long big, Integer boxed, short small, Optional<Integer> maybe, List<Long> many) {}
      Map<String, Object> integral = new LinkedHashMap<>();
      integral.put("count", 2.0);
      integral.put("big", 3e10);
      integral.put("boxed", 4.0f);
      integral.put("small", new java.math.BigDecimal("5.000"));
      integral.put("maybe", 6.0);
      integral.put("many", List.of(7.0, 8));
      assertThat(Instances.createInstance(Counts.class, integral))
          .isEqualTo(new Counts(2, 30_000_000_000L, 4, (short) 5, Optional.of(6), List.of(7L, 8L)));

      assertThatThrownBy(() -> Instances.createInstance(Counts.class, Map.of("count", 2.5)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Invalid value for Counts.count: Cannot convert 2.5 to int: the number is not an integer");
      assertThatThrownBy(() -> Instances.createInstance(Counts.class, Map.of("maybe", 3.9)))
          .hasMessageContaining("Cannot convert 3.9 to java.lang.Integer: the number is not an integer");
      assertThatThrownBy(() -> Instances.createInstance(Counts.class, Map.of("many", List.of(1, 1.5))))
          .hasMessageContaining("Cannot convert 1.5 to java.lang.Long");
      assertThatThrownBy(() -> Instances.convert(new java.math.BigDecimal("0.1"), java.math.BigInteger.class))
          .hasMessageContaining("the number is not an integer");
      assertThatThrownBy(() -> Instances.convert(Double.NaN, long.class)).hasMessageContaining("not an integer");
      // Floating-point targets keep the fraction
      assertThat(Instances.convert(2.5, double.class)).isEqualTo(2.5);
      assertThat(Instances.convert(2.5, java.math.BigDecimal.class)).isEqualTo(new java.math.BigDecimal("2.5"));
    }

    @Test
    void reportsWhichPropertyCouldNotBeConverted() {
      assertThatThrownBy(() -> Instances.createInstance(Item.class, Map.of("quantity", "lots")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Item.quantity");
    }

    List<Item> listOfItems() {
      return List.of();
    }
  }
}
