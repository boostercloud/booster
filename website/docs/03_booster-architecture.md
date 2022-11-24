# Booster architecture

Two patterns influence the Booster's event-driven architecture: Command-Query Responsibility Segregation ([CQRS](https://www.martinfowler.com/bliki/CQRS.html)) and [Event Sourcing](https://martinfowler.com/eaaDev/EventSourcing.html). They're complex techniques to implement from scratch with lower-level frameworks, but Booster makes them feel natural and very easy to use.

![architecture](/img/booster-arch.png)

The public interface of a Booster application is just `Commands` and `ReadModels`. Booster proposes an entirely different approach to the Model-View-\* and CRUD frameworks. With Booster, clients submit commands, query the read models, or subscribe to them for receiving real-time updates thanks to the out of the box [GraphQL API](04_features#graphql-api)

Booster applications are event-driven and event-sourced so, **the source of truth is the whole history of events**. When a client submits a command, the `CommandHandler` _wakes up_ and executes its logic. Optionally, it can _register_ as many `Events` as needed. The framework caches the current state by automatically _reducing_ all the registered events into `Entities`. Interested parties can _react_ to events via `EventHandlers`, and finally, the _projection_ functions transform the entities into `ReadModels`.

In this chapter you'll walk through these concepts and its details.
o80
# 1. Command and command handlers

Booster is different than MVC frameworks in which you typically implement controller classes with CRUD methods. Instead of that, you define commands, which are the user actions when interacting with an application. This approach fits very well with Domain-Driven Design. Depending on your application's domain, some examples of commands would be: `RemoveItemFromCart`, `RatePhoto`, `AddCommentToPost`, etc. Although, you can still have `Create*`, `Delete*`, or `Update*` commands when they make sense.

There is an architectural split between commands and command handlers though they _live_ in the same file. The command is the class with the `@Command` decorator, and the generated method called `handle` is the command handler. That is because Booster adopts several concepts from functional programming; the separation between data structures and data transformations is one of them. In Booster, a command looks like this:

```typescript
@Command({
  authorize: 'all' | Array<RoleClass>
})
export class CommandName {
  public constructor(
    readonly fieldA: SomeType,
    readonly fieldB: SomeOtherType,
    /* as many fields as needed */
  ) {}

  public static async handle(command: CommandName, register: Register): Promise<void> {
    // Validate inputs
    // Run domain logic
    // register.events([event1,...])
  }
}
```

Every time you submit a command through the GraphQL API, Booster calls the command handler function for the given command. Commands are part of the public API, so you can define authorization policies for them. They are also the place for validating input data before registering events into the event store because they are immutable once there.

## Commands naming convention

Semantics is very important in Booster as it will play an essential role in designing a coherent system. Your application should reflect your domain concepts, and commands are not an exception. Although you can name commands in any way you want, we strongly recommend you to name them starting with verbs in imperative plus the object being affected. If we were designing an e-commerce application, some commands would be:

- CreateProduct
- DeleteProduct
- UpdateProduct
- ChangeCartItems
- ConfirmPayment
- MoveStock
- UpdateCartShippingAddress

Despite you can place commands, and other Booster files, in any directory, we strongly recommend you to put them in `<project-root>/src/commands`. Having all the commands in one place will help you to understand your application's capabilities at a glance.

```text
<project-root>
├── src
│   ├── commands <------ put them here
│   ├── common
│   ├── config
│   ├── entities
│   ├── events
│   ├── index.ts
│   └── read-models
```

## Creating a command

The preferred way to create a command is by using the generator, e.g.

```shell
boost new:command CreateProduct --fields sku:SKU displayName:string description:string price:Money
```

The generator will automatically create a file called `create-product.ts` with a TypeScript class of the same name under the `commands` directory. You can still create (or modify) the command manually. Since the generator is not doing any _magic_, all you need is a class decorated as `@Command`. Anyway, we recommend you always to use the generator, because it handles the boilerplate code for you.



> [!NOTE] Generating a command with the same name as an already existing one will prompt the user for confirmation.

## The command handler function

Each command class must have a method called `handle`. This function is the command handler, and it will be called by the framework every time one instance of this command is submitted. Inside the handler you can run validations, return errors, query entities to make decisions, and register relevant domain events.

### Returning a value

> [!WARN] As of version 0.26.1 this annotation is deprecated. You may return any type without annotating the method

By default, the command handler function is generated with `void` as a return type,
and in consequence, it will return `true` when called through the GraphQL.

> [!NOTE] `true` is returned because GraphQL lacks of a `void` type, and it represents that the execution was successful.

If you want to return something back to the client, you have to decorate this
function with the `@Returns` decorator, passing the **class** that you want to
return.


> [!NOTE] For primitive types like `number`, `string`. The class is the name of the type but with the first letter in uppercase. E.g. `Number`, `String`

For example:

```typescript
@Command({
  authorize: 'all',
})
export class CreateProduct {
  public constructor(readonly sku: string, readonly price: number) {}

  @Returns(String)
  public static async handle(command: CreateProduct, register: Register): Promise<string> {
    return "Product created!"
  }
}
```

### Validating data

Booster uses the typed nature of GraphQL to ensure that types are correct before reaching the handler, so you don't have to validate types.

####  Throw an error

There are still business rules to be checked before proceeding with a command. For example, a given number must be between a threshold or a string must match a regular expression. In that case, it is enough just to throw an error in the handler. Booster will use the error's message as the response to make it descriptive, e.g.

Given this command:

```typescript
@Command({
  authorize: 'all',
})
export class CreateProduct {
  public constructor(readonly sku: string, readonly price: number) {}

  public static async handle(command: CreateProduct, register: Register): Promise<void> {
    const priceLimit = 10
    if (command.price >= priceLimit) {
      throw new Error(`price must be below ${priceLimit}, and it was ${command.price}`)
    }
  }
}
```

And this mutation:

```graphql
mutation($input: CreateProductInput!) {
  CreateProduct(input: $input)
}

  Variables

{
  "input": {
    "sku": "MYSKU",
    "price": 19.99
  }
}
```

You'll get something like this response:

```grapqhl
{
  "errors": [
    {
      "message": "price must be below 10, and it was 19.99",
      "path": [
        "CreateProduct"
      ]
    }
  ]
}
```

####  Register error events

There could be situations in which you want to register an event representing an error. For example, when moving items with insufficient stock from one location to another:

```typescript
@Command({
  authorize: [Admin],
})
export class MoveStock {
  public constructor(
    readonly productID: string,
    readonly origin: string,
    readonly destination: string,
    readonly quantity: number
  ) {}

  public static async handle(command: MoveStock, register: Register): Promise<void> {
    if (!command.enoughStock(command.productID, command.origin, command.quantity)) {
      register.events(new ErrorEvent(`There is not enough stock for ${command.productID} at ${command.origin}`))
    } else {
      register.events(new StockMoved(/*...*/))
    }
  }

  private enoughStock(productID: string, origin: string, quantity: number): boolean {
    /* ... */
  }
}
```

In this case, the command operation can still be completed. An event handler will take care of that `ErrorEvent and proceed accordingly.

###  Reading entities

Event handlers are a good place to make decisions and, to make better decisions, you need information. There is a Booster function called `entity` within the `Booster` package and allows you to inspect the application state. This function receives two arguments, the `Entity` to fetch and the `entityID`. Here is an example of fetching an entity called `Stock`:

```typescript
@Command({
  authorize: [Admin],
})
export class MoveStock {
  public constructor(
    readonly productID: string,
    readonly origin: string,
    readonly destination: string,
    readonly quantity: number
  ) {}

  public static async handle(command: MoveStock, register: Register): Promise<void> {
    const stock = await Booster.entity(Stock, command.productID)
    if (!command.enoughStock(command.origin, command.quantity, stock)) {
      register.events(new ErrorEvent(`There is not enough stock for ${command.productID} at ${command.origin}`))
    }
  }

  private enoughStock(origin: string, quantity: number, stock?: Stock): boolean {
    const count = stock?.countByLocation[origin]
    return !!count && count >= quantity
  }
}
```

###  Registering events

Within the command handler execution, it is possible to register domain events. The command handler function receives the `register` argument, so within the handler, it is possible to call `register.events(...)` with a list of events. For more details about events and the register parameter, see the [`Events`](03_booster-architecture#_2-events) section.

##  Authorizing a command

Commands are part of the public API of a Booster application, so you can define who is authorized to submit them. The Booster authorization feature is covered in [this](04_features#authentication-and-authorization) section. So far, we have seen that you can make a command publicly accessible by authorizing `'all'` to submit it. You can also set specific roles as we did with the `authorize: [Admin]` parameter of the `MoveStock` command.

##  Submitting a command

Booster commands are accessible to the outside world as GraphQL mutations. GrahpQL fits very well with Booster's CQRS approach because it has two kinds of operations: Mutations and Queries. Mutations are actions that modify the server-side data, as the commands are.

Booster automatically creates one mutation per command. The framework infers the mutation input type from the command fields, e.g., given this `CreateProduct` command:

```typescript
@Command({
  authorize: 'all',
})
export class CreateProduct {
  public constructor(
    readonly sku: Sku,
    readonly displayName: string,
    readonly description: string,
    readonly price: number
  ) {}

  public static async handle(command: CreateProduct, register: Register): Promise<void> {
    register.events(/* YOUR EVENT HERE */)
  }
}
```

Booster generates this GraphQL mutation:

```text
mutation CreateProduct($input: CreateProductInput!): Boolean
```

where the schema for `CreateProductInput` is

```text
{
  sku: String
  displayName: String
  description: String
  price: Float
}
```

##  Scheduling a command

Scheduled commands are the way to add automated tasks to your application, like checking an e-commerce abandoned carts every two hours to send notifications to the customer to come back and complete the checkout. Booster scheduled commands are TypeScript classes decorated with `@ScheduledCommand`, and unlike conventional commands, their handle function doesn't have any parameters.

In Booster, a scheduled command looks like this:

```typescript
@ScheduledCommand({
  minute: '0/5', // runs every 5 minutes
})
export class CheckCartCount {
  public static async handle(): Promise<void> {
    /* YOUR CODE HERE */
  }
}
```

Notice that you can pass as parameters `minute`, `hour`, `day`, `month`, `weekDay` and `year` to set up a cron expression. By default, if no paramaters are passed, the scheduled command will not be triggered.

##  Creating a scheduled command

The preferred way to create a scheduled command is by using the generator, e.g.

```shell
boost new:scheduled-command CheckCartCount
```

#  2. Events

Events are **immutable records of facts** within your application's domain. They are the cornerstone of Booster because of its event-driven and event-sourced nature. Booster events are TypeScript classes decorated with `@Event`. An event class may look like this:

```typescript
@Event
export class EventName {
  public constructor(readonly field1: SomeType, readonly field2: SomeOtherType) {}

  public entityID(): UUID {
    return /* the associated entity ID */
  }
}
```

Events and [entities](03_booster-architecture#_4-entities-and-reducers) are closely related. Each event belongs to one entity uniquely identified through the entityID method, and entities represent the application's state after reducing the stream of events. Indeed, an entity is just an aggregated representation of the same data present in its events, so it is possible to rebuild entities from events at any time. Booster guarantees that all the events associated with an entity will be reduced in the same order they were stored. Take a look at this event:

```typescript
@Event
export class CartPaid {
  public constructor(readonly cartID: UUID, readonly paymentID: UUID) {}

  public entityID(): UUID {
    // returns cartID because we want to associate it with
    // (and reduce it within) the Cart entity
    return this.cartID
  }
}
```

An event has to know the ID of the entity it belongs to and you need to implement the `entityID` method to return it. You can inject the entity ID directly in the event's constructor or as a nested attribute. If your domain requires a _singleton_ entity, where there's only one instance, you can return a constant value. In the `CartPaid` example, the entity ID (`cartID`) is injected directly.



> [!NOTE] The `entityID` method must always return the same value for the same event's instance. Otherwise, the result of the entity reduction will be unpredictable.

##  Events naming convention

As with commands, you can name events in any way you want, depending on your application's domain. However, we recommend you to choose short sentences written in past tense because events are facts that have happened and can't be changed. Some event names would be:

- ProductCreated
- ProductUpdated
- ProductDeleted
- CartItemChanged
- StockMoved

As with other Booster files, events have their own directory:

```text
<project-root>
├── src
│   ├── commands
│   ├── common
│   ├── config
│   ├── entities
│   ├── events <------ put them here
│   ├── index.ts
│   └── read-models
```

##  Creating events

The preferred way to create event files is the `new:event` generator, e.g.

```shell
boost new:event StockMoved --fields productID:string origin:string destination:string quantity:number
```

That will generate a file called `stock-moved.ts` under the proper `<project-root>/src/events` directory. You can also create the file manually, but we recommend using the generator and avoid dealing manually with boilerplate code.

> [!NOTE] Generating an event with the same name as an already existing one will prompt the user for confirmation.

##  Registering events in the event store

Creating an event file is different than storing an event instance in the event store. In Booster terminology, the latter receives the name of `registering` an event. As said before, Booster applications are event-sourced, which means that all the events are stored forever. Imagine this store as an infinite log used by the [reducer functions](03_booster-architecture#_4-entities-and-reducers) to recreate the application's current state.

Booster injects the register as a parameter in the `handle` method of both the command and the event handlers. Then you can register events by calling the `register.events(...)` method as many times as you want, e.g.

###  Registering events from command handlers

```typescript
@Command({
  authorize: [Admin],
})
export class MoveStock {
  public constructor(
    readonly productID: string,
    readonly origin: string,
    readonly destination: string,
    readonly quantity: number
  ) {}

  public static async handle(command: MoveStock, register: Register): Promise<void> {
    if (!command.enoughStock(command.origin, command.quantity, command.productID)) {
      register.events(new ErrorEvent(`There is not enough stock for ${command.productID} at ${command.origin}`))
    }
  }
}
```

###  Registering events from event handlers

In the case of the event handlers, you also receive the event instance that triggered the handle function.

```typescript
@EventHandler(StockMoved)
export class HandleAvailability {
  public static async handle(event: StockMoved, register: Register): Promise<void> {
    if (event.origin == 'provider') {
      register.events(new ProductAvailabilityChanged(event.productID, event.quantity))
    } else if (event.destination == 'customer') {
      register.events(new ProductAvailabilityChanged(event.productID, -event.quantity))
    }
  }
}
```

#  3. Event handlers

As expected with event-driven architectures, multiple parts of our application react to events. In the case of Booster, we have entities (in charge of reducing the events) and event handlers. These last ones are classes decorated with the @EventHandler decorator. Every time a new instance of a given event is registered, the handle method of this class is triggered. This method can contain any business logic defined by the user or it can also register new events.

An event handler would look like this:

```typescript
@EventHandler(StockMoved)
export class HandleAvailability {
  public static async handle(event: StockMoved, register: Register): Promise<void> {
    if (event.origin == 'provider') {
      // New stock enters the system
      register.events(new ProductAvailabilityChanged(event.productID, event.quantity))
    } else if (event.destination == 'customer') {
      // Stock goes to the customer
      register.events(new ProductAvailabilityChanged(event.productID, -event.quantity))
    }
    // In terms of availability, it doesn't matter in which warehouse the stock is as soon as there's stock
  }
}
```

##  Creating an event handler

Event handlers can be easily created using the Booster CLI command `boost new:event-handler`. There are two mandatory arguments: the event handler name, and the name of the event it will react to. For instance:

```typescript
boost new:event-handler HandleAvailability --event StockMoved
```

Once the creation is completed, there will be a new file in the event handlers directory `<project-root>/src/event-handlers/handle-availability.ts`.

```text
<project-root>
├── src
│   ├── commands
│   ├── common
│   ├── config
│   ├── entities
│   ├── events
│   ├── event-handlers <------ put them here
│   └── read-models
```

##  Registering events from an event handler

Booster injects a `register` instance in the `handle` method that we can use to register extra events. In the above example, you can see there is some logic that ends up registering new events.

The `events(...)` method of the `register` allows triggering several events, you can specify as many as you need separated by commas as arguments of the function.

An example can be found below:

```typescript
register.events(new ProductAvailabilityChanged(event.productID, -event.quantity))
```

##  Reading entities from event handlers

Just as we do in command handlers, we can also retrieve entities information to make decisions based on their current state.

Let's say that we want to check the status of a product before we trigger its availability update. In that case we would call the `Booster.entity` function, which will return information about the entity.

```typescript
public static async handle(event: StockMoved, register: Register): Promise<void> {
  const productSnapshot = await Booster.entity(Product, event.productID)
  ...
}
```

#  4. Entities and reducers

The source of truth of your Booster app are the events, but events make sense in the context of a domain entity.
For example, in a banking app, there might be two events: `MoneyDeposited` and `MoneyWithdrawn`. However, these events
only make sense in the context of a `BankAccount`.

You can assume that entities are created on the fly by _reducing_ the whole event stream. Under the hood, Booster is creating
automatic snapshots for each entity so that the reduction process is efficient.

An entity is defined as a class with the `@Entity` decorator. Inside of it, you can write one or more static methods (called "reducers") with
the `@Reduces` decorator specifying the event they reduce. The reducer method will be called with two arguments: the event and the current state of the entity. Booster expects you to return a new entity with the changes implied by the event applied to the current one.

An entity class looks like this:

```typescript
@Entity
export class EntityName {
  public constructor(readonly fieldA: SomeType, readonly fieldB: SomeOtherType /* as many fields as needed */) {}

  @Reduces(SomeEvent)
  public static reduceSomeEvent(event: SomeEvent, currentEntityState?: EntityName): EntityName {
    /* Return a new entity based on the current one */
  }
}
```

There could be a lot of events being reduced concurrently among many entities, but, **for a specific entity instance, the events order is preserved**. This means that while one event is being reduced, all other events of any kind _that belong to the same entity instance_ will be waiting in a queue until the previous reducer has finished (with "entity instance" we refer to an entity of a specific type and with a specific ID). This is important to make sure that entities state is built correctly.

##  Entities naming convention

Entities are a representation of your application state in a specific moment, so name them as closely to your domain objects as possible. Typical entity names are nouns that might appear when you think about your app. In an e-commerce application, some entities would be:

- Cart
- Product
- UserProfile
- Order
- Address
- PaymentMethod
- Stock

Entities live within the entities directory of the project source: `<project-root>/src/entities`.

```text
<project-root>
├── src
│   ├── commands
│   ├── common
│   ├── config
│   ├── entities <------ put them here
│   ├── events
│   ├── index.ts
│   └── read-models
```

##  Creating entities

The preferred way to create an entity is by using the generator, e.g.

```text
boost new:entity Product --fields displayName:string description:string price:Money
```

The generator will automatically create a file called `product.ts` with a TypeScript class of the same name under the `entities` directory. You can still create the entity manually, writing a class decorated with `@Entity`. Anyway, we recommend you always to use the generator because it handles the boilerplate code for you.



> [!NOTE] Generating an entity with the same name as an already existing one will prompt the user for confirmation.

##  The reducer function

Booster generates the reducer function as a static method of the entity class. That function is called by the framework every time that an event of the specified type needs to be reduced. It's highly recommended to **keep your reducer functions pure**, which means that you should be able to produce the new entity version by just looking at the event and the current entity state. You should avoid calling third party services, reading or writing to a database, or changing any external state.

Booster injects two parameters to the reducer functions:

- `event` - The event object that triggered the reducer
- `currentEntity?` - The current state of the entity instance that the event belongs to if it exists. **This parameter is optional** and will be `undefined` if the entity don't exist yet (For example, when you process a `ProductCreated` event that will generate the first version of a `Product` entity).

This is how events change your application state.

Given this entity:

```ts
@Entity
export class Cart {
  public constructor(public id: UUID, readonly items: Array<CartItem>) {}

  @Reduces(ProductAdded)
  public static reduceProductAdded(event: ProductAdded, currentCart?: Cart): Cart {
    const newItems = addToCart(event.item, currentCart)
    return new Cart(event.cartID, newItems)
  }

  @Reduces(ProductRemoved)
  public static reduceProductRemoved(event: ProductRemoved, currentCart?: Cart): Cart {
    const newItems = removeFromCart(event.item, currentCart)
    return new Cart(event.cartID, newItems)
  }
}
```

You can visualize reduction like this:

![reducer process gif](/img/reducer.gif)

##  Eventual consistency

Due to the event driven and async nature of Booster, your data might not be instantly updated. Booster will consume the commands, generate events, and _eventually_ generate the entities. Most of the time this is not perceivable, but under huge loads, it could be noticed.

This property is called [Eventual Consistency](https://en.wikipedia.org/wiki/Eventual_consistency), and it is a trade-off to have high availability for extreme situations, where other systems might simply fail.

#  5. Read models and projections

Read Models are cached data optimized for read operations. They're updated reactively when [Entities](03_booster-architecture#_4-entities-and-reducers) are updated after reducing [events](03_booster-architecture#_2-events). They also define the _Read API_.

Read Models are classes decorated with the `@ReadModel` decorator that have one or more projection methods.

```typescript
@ReadModel
export class ReadModelName {
  public constructor(readonly fieldA: SomeType, readonly fieldB: SomeType /* as many fields as needed */) {}

  @Projects(SomeEntity, 'entityField')
  public static projectionName(
    entity: SomeEntity,
    currentEntityReadModel?: ReadModelName
  ): ProjectionResult<ReadModelName> {
    return new ReadModelName(/* initialize here your constructor properties */)
  }

  @Projects(SomeEntity, 'othetEntityField')
  public static projectionName(
    entity: SomeEntity,
    currentEntityReadModel?: ReadModelName
  ): ProjectionResult<ReadModelName> {
    return new ReadModelName(/* initialize here your constructor properties */)
  }
  /* as many projections as needed */
}
```

##  Read models naming convention

As it has been previously commented, semantics plays an important role in designing a coherent system and your application should reflect your domain concepts, we recommend choosing a representative domain name and use the `ReadModel` suffix in your read models name.

Despite you can place your read models in any directory, we strongly recommend you to put them in `<project-root>/src/read-models`. Having all the read models in one place will help you to understand your application's capabilities at a glance.

```text
<project-root>
├── src
│   ├── commands
│   ├── common
│   ├── config
│   ├── entities
│   ├── read-models  <------ put them here
│   ├── events
│   ├── index.ts
│   └── read-models
```

##  Creating a read model

The preferred way to create a read model is by using the generator, e.g.

```shell
boost new:read-model CartReadModel --fields id:UUID cartItems:"Array<CartItem>" paid:boolean --projects Cart
```

The generator will create a Typescript class under the read-models directory `<project-root>/src/read-models/cart-read-model.ts`.

Read Model classes can also be created by hand and there are no restrictions. The structure of the data is totally open and can be as complex as you can manage in your projection functions.

##  The projection function

A `Projection` is a method decorated with the `@Projects` decorator that, given a new entity value and (optionally) the current read model state, generate a new read model value.

Read models can be projected from one or more [entities](03_booster-architecture#_4-entities-and-reducers) as soon as all the entities involved have one field with the same semantics that can be used as a join key (usually an identifier or a reference to other entity). A join key in Booster is similar to join keys in relational databases, so you could see Read Models as reactive join operations that you can use to build data aggregates. When an entity is updated, Booster uses the join key to find the right read model instance, so all entities that share the same join key value will trigger the projections of the same read model. When defining a projection with the `@Projects` decorator, it's required to set the field name of the join key in each entity. Let's see an example:

```typescript
@ReadModel
export class UserReadModel {
  public constructor(readonly username: string, /* ...(other interesting fields from users)... */) {}

  @Projects(User, 'id')
  public static projectUser(entity: User, current?: UserReadModel): ProjectionResult<UserReadModel> { 
    // Here we update the user fields
  }

  @Projects(Post, 'ownerId')
  public static projectUserPost(entity: Post, current?: UserReadModel): ProjectionResult<UserReadModel> { 
    //Here we can adapt the read model to show specific user information related with the Post entity
  }
}
```

In the previous example we are projecting the `User` entity using the user `id` and also we are projecting the `User` entity based on the `ownerId` of the `Post` entity. Notice that both join keys are references to the `User` identifier, but it's not required that the join key is an identifier.

You can even select arrays of UUIDs as `joinKey`, Booster will execute the projection for all the read models corresponding to those ids contained in the array (projections are completely isolated from each other). A subtle difference with non-array `joinKey` is the projection method signature. With array join keys, sometimes, we need extra information to know which is the read model we are projecting (especially for not yet existent read models, where the current argument is not present)
So, for example, if we would have a `Group` with an array of users in that group (`users: Array<UUID>`), we can have the following to update each `UserReadModel` accordingly:

```typescript
  @Projects(Group, 'users')
  public static projectUserGroup(entity: Group, readModelID: UUID, current?: UserReadModel): ProjectionResult<UserReadModel> { 
    //Here we can update the read models with group information
    //This logic will be executed for each read model id in the array 
  }
```

As you may have notice from the `ProjectionResult` type, projections can also return `ReadModelAction`, which includes:

1. Deletion of read models by returning the `ReadModelAction.Delete` value
2. You can also return `ReadModelAction.Nothing` to keep the read model untouched

```
@ReadModel
export class UserReadModel {
  public constructor(readonly username: string, /* ...(other interesting fields from users)... */) {}

  @Projects(User, 'id')
  public static projectUser(entity: User, current?: UserReadModel): ProjectionResult<UserReadModel>  {
    if (current?.deleted) {
      return ReadModelAction.Delete
    } else if (!current?.modified) {
      return ReadModelAction.Nothing
    }
    return new UserReadModel(...)
  }
```

##  Authorizing read models

Read models are the tool to build the public read API of a Booster application, so you can define who is authorized to query and subscribe to them. The Booster authorization feature is covered in [the auth section](04_features#authentication-and-authorization). So far, we have seen that you can make a read model publicly accessible by authorizing `'all'` to query it or you can set specific roles providing an array of roles in this way: `authorize: [Admin]`.

##  Querying a read model

For every read model, Booster automatically creates all the necessary queries and subscriptions. For example, given this `CartReadModel`:

```typescript
@ReadModel({
  authorize: 'all',
})
export class CartReadModel {
  public constructor(public id: UUID, readonly items: Array<CartItem>) {}

  @Projects(Cart, 'id')
  public static projectCart(entity: Cart, currentReadModel: CartReadModel): ProjectionResult<CartReadModel> {
    return new CartReadModel(entity.id, entity.items)
  }
}
```

You will get the following GraphQL query and subscriptions:

```graphQL
query CartReadModel(id: ID!): CartReadModel
subscription CartReadModel(id: ID!): CartReadModel
subscription CartReadModels(id: UUIDPropertyFilter!): CartReadModel
```

For more information about queries and how to use them, please check the [GraphQL API](04_features#reading-read-models) section.

## Time Sequenced Read Models

There are some use cases when it's desirable to model your read models as time sequences. An example could be building a chat app where all messages are identified by a specific channelID and a timestamp of when it was sent. Booster provides a special decorator to tag a specific property as a sequence key for a read model:

```typescript
export class MessageReadModel {
  public constructor(
    readonly id: UUID, // A channel ID
    @sequencedBy readonly timestamp: string,
    readonly contents: string
  )

  @Projects(Message, 'id')
  public static projectMessage(entity: Message, currentReadModel: MessageReadModel): ProjectionResult<MessageReadModel> {
    return new MessageReadModel(entity.id, entity.timestamp, entity.contents)
  }
}
```

### Querying time sequences

Adding a sequence key to a read model changes the behavior of the singular query, which now accepts the sequence key as an optional parameter:

```graphQL
query MessageReadModel(id: ID!, timestamp: string): [MessageReadModel]
```

Using this query, when only the id is provided, you get an array of all the messages in the channel ordered by timestamp in ascending order (from older to newer). When you also provide an specific timestam, you still get an array, but it will only contain the message sent in that exact moment.

As the timestamp field is used as an index, it is important to guarantee that two messages never have the same timestamp value. In order to make it easier to generate unique timestamps, you can use the method `TimeKey.generate()`, which will generate timestamps with an UUID as a suffix to resolve any coincidences.

For more information about queries and how to use them, please check the [GraphQL API](04_features#reading-read-models) section.

##  Getting real-time updates for a read model

Booster GraphQL API also provides support for real-time updates using subscriptions and a web-socket. To get more information about it go to the [GraphQL API](04_features#subscribing-to-read-models) section.

## Filtering a read model

The Booster GraphQL API provides support for filtering Read Models on `queries` and `subscriptions`. To get more information about it go to the [GraphQL API](04_features#filtering-a-read-model) section.
