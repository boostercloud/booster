# Events

![Booster architecture](../img/booster-arch.png)

An event is a data structure that represents a **fact** and is the source of truth for your application. Instead of mutating your database, you store an event representing that mutation. Think of your bank account, instead of storing your balance in some database table, mutating the value every time you perform an operation, it stores events for each of them. The balance is then calculated on the fly and shown to you any time you request it. Two examples of events in your bank account would be:

- `WithdrawMoney`
- `DepositMoney`

You can define as many event handler classes as you want to react to them. For example, imagine that a specific event represents that your account has reached zero. You can write a handler to notify the user by email. In a Booster application, it is recommended to write most your domain logic in event handlers.

To create an event class, you can do the same thing that you did with a command, either manually,
or with the generator, using the `boost` command line tool:

```shell script
boost new:event <name of the event> --fields fieldName:fieldType
```

Booster will generate a class for you in the `src/events` folder:

```typescript
@Event
export class CartPaid {
  public constructor(readonly cartID: UUID, readonly confirmationToken: string) {}

  public entityID(): UUID {
    return this.cartId
  }
}
```

Notice the required `entityID` method. All events are grouped by their event type and the value returned by `entityID`. All events are somehow tied to a concept in your domain model, in our bank account example, this could be the account number.

In the previous example, the `CartPaid` event has a `cartID` field, which then you will return in the `entityID` method. This allows booster to find this event when the system requests to build the state of a specific Cart.

In most situations your event stream will be reduced to a domain model object, like that Cart (An [Entity](_05-entities.md)), but there are some use cases on which the event stream is just related to a specific entity, for example, a register of sensor values in a weather station, which are related to the station, but the station has no specific value that needs to be reduced. You can implement the semantics that best suit your needs.

## Event Handlers

You can react to events implementing an **Event Handler** class. An Event Handler is a regular class that is subscribed to an event with the decorator `@EventHandler(<name of the event class>`. Any time that a new event is added to the event store, the `handle` method in the event handler will be called with the instance of the event and the `register` object that can be used to emit new events. Event handlers can run arbitrary code and is where it is recommended to write most of the business logic in a reactive way:

```typescript
@EventHandler(CartPaid)
export class CartPaidHandler {
  public static handle(event: CartPaid, register: Register) {
    register.events(new OrderPreparationStarted(event.cartID))
  }
}
```

Let's continue learning about [Entities](_05-entities.md)!