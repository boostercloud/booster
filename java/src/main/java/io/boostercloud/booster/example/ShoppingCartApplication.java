package io.boostercloud.booster.example;

import io.boostercloud.booster.core.AccessPolicy;
import io.boostercloud.booster.core.Booster;
import io.boostercloud.booster.core.EventStore;
import java.util.ArrayList;
import java.util.List;

/** An executable domain example. Prices use integral cents; historical events are immutable. */
public final class ShoppingCartApplication implements io.boostercloud.booster.local.LocalApplication {
  public ShoppingCartApplication() {}
  @Override public Booster create(io.boostercloud.booster.local.LocalProvider provider) {
    return create(provider.eventStore(), provider.stateStore());
  }

  public record Line(String sku, int quantity, long unitPriceCents) {}
  public record Cart(String id, List<Line> lines, boolean checkedOut) {
    public Cart { lines = List.copyOf(lines); }
  }
  public record CartView(String id, int totalQuantity, long totalCents, boolean checkedOut) {}
  public record CartId(String id) {}
  public record OpenCart(String cartId) {}
  public record AddItem(String cartId, String sku, int quantity, long unitPriceCents) {}
  public record RemoveItem(String cartId, String sku, int quantity) {}
  public record CheckoutCart(String cartId) {}
  public record CartOpened(String cartId) {}
  public record ItemAdded(String cartId, String sku, int quantity, long unitPriceCents) {}
  public record ItemRemoved(String cartId, String sku, int quantity) {}
  public record CartCheckedOut(String cartId) {}

  public static Booster create(EventStore store) {
    return create(store, new io.boostercloud.booster.core.InMemoryStateStore());
  }
  public static Booster create(EventStore store, io.boostercloud.booster.core.StateStore state) {
    return Booster.builder(store).stateStore(state)
        .entity(Cart.class)
        .event(CartOpened.class, CartOpened::cartId, Cart.class, (event, old) -> {
          if (old != null) throw new IllegalArgumentException("Cart already exists");
          return new Cart(event.cartId(), List.of(), false);
        })
        .event(ItemAdded.class, ItemAdded::cartId, Cart.class, ShoppingCartApplication::add)
        .event(ItemRemoved.class, ItemRemoved::cartId, Cart.class, ShoppingCartApplication::remove)
        .event(CartCheckedOut.class, CartCheckedOut::cartId, Cart.class,
            (event, cart) -> new Cart(cart.id(), cart.lines(), true))
        .command(OpenCart.class, CartId.class, AccessPolicy.publicAccess(), (command, register) -> {
          requireText(command.cartId(), "cartId");
          if (register.entity(Cart.class, command.cartId()) != null) {
            throw new IllegalArgumentException("Cart already exists");
          }
          register.emit(new CartOpened(command.cartId()));
          return new CartId(command.cartId());
        })
        .command(AddItem.class, CartId.class, AccessPolicy.publicAccess(), (command, register) -> {
          Cart cart = requireActive(register.entity(Cart.class, command.cartId()));
          requireText(command.sku(), "sku");
          requireQuantity(command.quantity());
          if (command.unitPriceCents() < 0 || command.unitPriceCents() > 1_000_000_000L) {
            throw new IllegalArgumentException("unitPriceCents must be between 0 and 1000000000");
          }
          ItemAdded event = new ItemAdded(command.cartId(), command.sku(), command.quantity(), command.unitPriceCents());
          add(event, cart); // Validate aggregate limits before appending the event.
          register.emit(event);
          return new CartId(command.cartId());
        })
        .command(RemoveItem.class, CartId.class, AccessPolicy.publicAccess(), (command, register) -> {
          Cart cart = requireActive(register.entity(Cart.class, command.cartId()));
          requireText(command.sku(), "sku");
          requireQuantity(command.quantity());
          ItemRemoved event = new ItemRemoved(command.cartId(), command.sku(), command.quantity());
          remove(event, cart);
          register.emit(event);
          return new CartId(command.cartId());
        })
        .command(CheckoutCart.class, CartId.class, AccessPolicy.publicAccess(), (command, register) -> {
          Cart cart = requireActive(register.entity(Cart.class, command.cartId()));
          if (cart.lines().isEmpty()) throw new IllegalArgumentException("Cannot check out an empty cart");
          register.emit(new CartCheckedOut(command.cartId()));
          return new CartId(command.cartId());
        })
        .readModel(CartView.class, Cart.class, AccessPolicy.publicAccess(),
            (cart, previous) -> summarize(cart))
        .build();
  }

  private static Cart add(ItemAdded event, Cart previous) {
    Cart cart = requireActive(previous);
    List<Line> lines = new ArrayList<>(cart.lines());
    int index = indexOf(lines, event.sku());
    if (index < 0) {
      if (lines.size() >= 100) throw new IllegalArgumentException("A cart can contain at most 100 distinct items");
      lines.add(new Line(event.sku(), event.quantity(), event.unitPriceCents()));
    } else {
      Line old = lines.get(index);
      if (old.unitPriceCents() != event.unitPriceCents()) {
        throw new IllegalArgumentException("A product's price cannot change within a cart");
      }
      int quantity = Math.addExact(old.quantity(), event.quantity());
      if (quantity > 10_000) throw new IllegalArgumentException("A product quantity cannot exceed 10000");
      lines.set(index, new Line(event.sku(), quantity, event.unitPriceCents()));
    }
    return new Cart(cart.id(), lines, false);
  }

  private static Cart remove(ItemRemoved event, Cart previous) {
    Cart cart = requireActive(previous);
    List<Line> lines = new ArrayList<>(cart.lines());
    int index = indexOf(lines, event.sku());
    if (index < 0 || lines.get(index).quantity() < event.quantity()) {
      throw new IllegalArgumentException("Cannot remove more items than the cart contains");
    }
    Line old = lines.get(index);
    if (old.quantity() == event.quantity()) lines.remove(index);
    else lines.set(index, new Line(old.sku(), old.quantity() - event.quantity(), old.unitPriceCents()));
    return new Cart(cart.id(), lines, false);
  }

  private static int indexOf(List<Line> lines, String sku) {
    for (int i = 0; i < lines.size(); i++) if (lines.get(i).sku().equals(sku)) return i;
    return -1;
  }

  private static CartView summarize(Cart cart) {
    if (cart == null) return null;
    int quantity = 0;
    long total = 0;
    for (Line line : cart.lines()) {
      quantity = Math.addExact(quantity, line.quantity());
      total = Math.addExact(total, Math.multiplyExact(line.quantity(), line.unitPriceCents()));
    }
    return new CartView(cart.id(), quantity, total, cart.checkedOut());
  }

  private static Cart requireActive(Cart cart) {
    if (cart == null) throw new IllegalArgumentException("Cart does not exist");
    if (cart.checkedOut()) throw new IllegalArgumentException("Cart has already been checked out");
    return cart;
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank() || value.length() > 128) {
      throw new IllegalArgumentException(field + " must contain between 1 and 128 characters");
    }
  }

  private static void requireQuantity(int quantity) {
    if (quantity < 1 || quantity > 1000) throw new IllegalArgumentException("quantity must be between 1 and 1000");
  }
}
