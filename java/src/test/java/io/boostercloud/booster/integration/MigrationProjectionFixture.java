package io.boostercloud.booster.integration;

import io.boostercloud.booster.core.*;
import io.boostercloud.booster.migration.DataMigration;
import io.boostercloud.booster.migration.DataMigrations;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** A complete domain fixture shared by local end-to-end tests. */
public final class MigrationProjectionFixture {
  private MigrationProjectionFixture() {}
  public record Customer(String id, String name) {}
  public record CustomerV2(String id, String name, String segment) {}
  public record Order(String id, String customerId, long totalCents) {}
  public record OrderView(String id, String customerId, String customerName, String segment, long totalCents) {}
  public record PlaceOrder(String id, String customerId, String customerName, long totalCents) {}
  public record OrderPlaced(String id, String customerId, long totalCents) {}
  public record CustomerNamed(String id, String name) {}
  public record OrderRelinked(String id, String customerId) {}

  public static void configure(Booster.Builder builder) {
    DataMigrations.install(builder)
        .event(OrderPlaced.class, OrderPlaced::id, Order.class,
            (event, old) -> new Order(event.id(), event.customerId(), event.totalCents()))
        .event(OrderRelinked.class, OrderRelinked::id, Order.class,
            (event, old) -> new Order(event.id(), event.customerId(), Objects.requireNonNull(old).totalCents()))
        .event(CustomerNamed.class, CustomerNamed::id, Customer.class,
            (event, old) -> new Customer(event.id(), event.name()))
        .entity(CustomerV2.class)
        .command(PlaceOrder.class, Order.class, AccessPolicy.roles("operator"), (command, register) -> {
          if (command.totalCents() < 0 || command.customerName() == null || command.customerName().isBlank())
            throw new IllegalArgumentException("Valid customer and total are required");
          if (register.entity(Order.class, command.id()) != null)
            throw new BoosterException(BoosterException.Code.CONFLICT, "Order already exists");
          register.emit(new OrderPlaced(command.id(), command.customerId(), command.totalCents()));
          register.emit(new CustomerNamed(command.customerId(), command.customerName()));
          return register.entity(Order.class, command.id());
        })
        .readModel(OrderView.class, AccessPolicy.roles("reader"))
        .projection(OrderView.class, Order.class, Join.key(Order::id), context -> {
          Order order = context.entity();
          OrderView old = context.previous();
          boolean sameCustomer = old != null && old.customerId().equals(order.customerId());
          return ProjectionResult.store(new OrderView(context.id(), order.customerId(),
              sameCustomer ? old.customerName() : null, sameCustomer ? old.segment() : null, order.totalCents()));
        }, context -> ProjectionResult.delete())
        .projection(OrderView.class, Customer.class,
            Join.filter(customer -> Json.MAPPER.createObjectNode().set("customerId",
                Json.MAPPER.createObjectNode().put("eq", customer.id()))),
            context -> ProjectionResult.store(new OrderView(context.id(), context.previous().customerId(),
                context.entity().name(), "standard", context.previous().totalCents())),
            context -> ProjectionResult.store(new OrderView(context.id(), context.previous().customerId(),
                null, null, context.previous().totalCents())))
        .projection(OrderView.class, CustomerV2.class,
            Join.filter(customer -> Json.MAPPER.createObjectNode().set("customerId",
                Json.MAPPER.createObjectNode().put("eq", customer.id()))),
            context -> ProjectionResult.store(new OrderView(context.id(), context.previous().customerId(),
                context.entity().name(), context.entity().segment(), context.previous().totalCents())),
            context -> ProjectionResult.store(new OrderView(context.id(), context.previous().customerId(),
                null, null, context.previous().totalCents())));
  }

  public static List<DataMigration> migrations(AtomicBoolean interruptOnce) {
    return List.of(new DataMigration("customer-v2", 10,
        Json.MAPPER.createObjectNode().put("oldId", "customer-old").put("newId", "customer-new"), context -> {
      String destination = context.parameters().path("newId").asText();
      if (!context.checkpoint().path("firstOrderDone").asBoolean()) {
        context.step("relink:order-1", Json.MAPPER.valueToTree(new OrderRelinked("order-1", destination)),
            (input, register) -> { register.emit(Json.MAPPER.treeToValue(input, OrderRelinked.class)); return null; });
        context.checkpoint(Json.MAPPER.createObjectNode().put("firstOrderDone", true));
      }
      if (interruptOnce.getAndSet(false)) throw new IllegalStateException("Simulated process interruption after first durable step");
      context.step("relink:order-2", Json.MAPPER.valueToTree(new OrderRelinked("order-2", destination)),
          (input, register) -> { register.emit(Json.MAPPER.treeToValue(input, OrderRelinked.class)); return null; });
      context.migrateEntity("rename-customer", "Customer", context.parameters().path("oldId").asText(),
          new CustomerV2(destination, "Ada Lovelace", "gold"));
      context.finish();
    }), new DataMigration("reproject-customer-v2", 20, context -> {
      context.touch("refresh:customer-new", CustomerV2.class, "customer-new");
      context.finish();
    }));
  }
}
