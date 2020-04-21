# Entities

![Booster architecture](../img/booster-arch.png)

Entities are not shown in the diagram because they're just a different view of the data in the events store.

Entities represent domain model objects, that is, something that can be mapped to an object with semantics in your domain. Entities only exist conceptually, they're not explicitly stored in any database, but generated on the fly from a list of related [events](_04-events.md).

Booster creates snapshots of the entities automatically under the hoods to reduce access times, but the developer doesn't has to worry about that.

Examples of entities are:

- A Cart
- An Account
- A User

To create an entity... You guessed it! We use the `boost` tool:

```shell script
boost new:entity <name of the entity> --fields fieldName:fieldType --reduces EventOne EventTwo EventThree
```

For instance, running the following command:

```shell script
boost new:entity Order --fields shippingAddress:Address orderItems:"Array<OrderItem>" --reduces OrderCreated
```

will generate a class in the `src/entities` folder with the following structure:

```typescript
@Entity
export class Order {
  public constructor(readonly id: UUID, readonly shippingAddress: Address, readonly orderItems: Array<OrderItem>) {}

  @Reduces(OrderCreated)
  public static createOrder(event: OrderCreated, previousOrder?: Order): Order {
    return event.order
  }
}
```

As you can see, entities are also regular TypeScript classes, like the rest of the Booster artifacts.

Take a look, entities have a special **reducer function** decorated with `@Reduces`,
that will be triggered each time that a specific kind of event is generated.

All projection functions receive:

- The event
- A possible previous state (note the `?` meaning that there could be no previous state. i.e. when the app is just starting)

And it **always** must return a new entity. This function **must be pure**, which means that it cannot perform any side effects, only create a new object based on some conditions on the input data, and then return it.

## Reading Entity "state"

Booster provides a handy `fetchEntitySnapshot` method to check the value of an entity from any handler method in order to make domain-driven decisions:

```typescript
@Command({
  authorize: 'all',
})
export class MoveStock {
  public constructor(readonly productSKU: UUID, readonly fromLocationId: UUID, readonly toLocationId: UUID, readonly quantity: number) {}

  public handle(register: Register): void {
    const productStock = fetchEntitySnapshot('ProductStock', this.productSKU)

    if (productStock.locations[this.fromLocationId].count >= this.quantity) {
      // Enough stock, we confirm the movement
      register.events(new StockMovement(this.productSKU, this.fromLocationId, this.toLocationID, quantity))
    } else {
      // Not enough stock, we register this fact
      register.events(new FailedCommand({
        command: this,
        reason: `Not enough stock in origin location`
      ))
    }
  }
}
```

Continue reading about [Read Models](_06-read-models.md)!
