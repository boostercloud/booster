package com.boostercloud.framework.core.services.graphql;

import com.boostercloud.framework.core.decorators.All;
import com.boostercloud.framework.core.decorators.CalculatedField;
import com.boostercloud.framework.core.decorators.Command;
import com.boostercloud.framework.core.decorators.Entity;
import com.boostercloud.framework.core.decorators.Event;
import com.boostercloud.framework.core.decorators.NonExposed;
import com.boostercloud.framework.core.decorators.Query;
import com.boostercloud.framework.core.decorators.ReadModel;
import com.boostercloud.framework.core.decorators.Reduces;
import com.boostercloud.framework.core.decorators.SequencedBy;
import com.boostercloud.framework.types.concepts.QueryInfo;
import com.boostercloud.framework.types.concepts.Register;
import com.boostercloud.framework.types.concepts.TimeKey;
import com.boostercloud.framework.types.concepts.UUID;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A small application whose GraphQL schema exercises every generation rule (the Java form of the integration-test
 * fixtures {@code SchemaReadModel}, {@code ChangeCartItem}...).
 */
final class GraphQLTestApp {

  private GraphQLTestApp() {}

  /** The classes to register, in order. */
  static final List<Class<?>> CLASSES = List.of(ProductCreated.class, Product.class, ProductReadModel.class,
      TimelineReadModel.class, CreateProduct.class, DeleteProduct.class, GetStock.class, FindProducts.class);

  @Event
  public record ProductCreated(UUID productId, String sku) {
    public UUID entityID() {
      return productId;
    }
  }

  @Entity(authorizeReadEvents = All.class)
  public record Product(UUID id, String sku) {
    @Reduces(ProductCreated.class)
    public static Product created(ProductCreated event, Product current) {
      return new Product(event.productId(), event.sku());
    }
  }

  public record Address(String street, double zip) {}

  public enum Size {
    SMALL,
    LARGE
  }

  public sealed interface Vehicle permits Car, Bike {}

  public record Car(int wheels) implements Vehicle {}

  public record Bike(String brand) implements Vehicle {}

  public record Page<T>(List<T> items, T first) {}

  public record Category(String name, Optional<Category> parent) {}

  @ReadModel(authorize = All.class)
  public record ProductReadModel(
      UUID id,
      String sku,
      double price,
      boolean available,
      Instant createdAt,
      List<String> tags,
      Optional<Address> address,
      @NonExposed Optional<String> internalNote,
      Size size,
      Map<String, Object> metadata,
      Vehicle vehicle,
      Page<String> page,
      List<Address> addresses,
      Optional<Category> category) {

    @CalculatedField(dependsOn = {"price"})
    public double priceWithTaxes() {
      return price * 1.21;
    }
  }

  @ReadModel(authorize = All.class)
  public record TimelineReadModel(UUID id, @SequencedBy TimeKey timestamp, String text) {}

  @Command(authorize = All.class)
  public record CreateProduct(String sku, double price, @NonExposed Optional<String> secret) {
    public static UUID handle(CreateProduct command, Register register) {
      return UUID.generate();
    }
  }

  @Command(authorize = All.class)
  public record DeleteProduct(UUID productId) {
    public static void handle(DeleteProduct command, Register register) {}
  }

  @Query(authorize = All.class)
  public record GetStock(UUID productId) {
    public static CompletableFuture<Double> handle(GetStock query, QueryInfo queryInfo) {
      return CompletableFuture.completedFuture(1.0);
    }
  }

  @Query(authorize = All.class)
  public record FindProducts(Optional<String> sku) {
    public static List<Address> handle(FindProducts query, QueryInfo queryInfo) {
      return List.of();
    }
  }
}
