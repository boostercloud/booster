# Commands and Command Handlers - The Write Pipeline

![Booster architecture](../img/booster-arch.png)

Commands and Command Handlers define the **write** API of your application (highlighted in yellow in the diagram). Commands are objects that are sent to the `/commands` endpoint. The usage of this endpoint is explained [in the REST API section](_09-rest-api.md).

Similarly to controllers in a traditional [MVC](https://www.martinfowler.com/eaaCatalog/modelViewController.html) architecture, commands are synchronously dispatched by a _handler_ method, which will be in charge of validating the input and registering one or more [events](_04-events.md) in the system. While command handlers can run arbitrary code, it is recommended to keep them small, focusing on data acceptance and delegating as much logic to [event handlers](_04-events.md).

> **Note:** Event registration is not mandatory, but we **strongly** recommend registering at least one event for any possible final state, even in the case of a failure, to make your application activity easier to trace and debug.

A command is a class, decorated with the `@Command` decorator, that defines a data structure
and a `handle` method. The method will process the commands and optionally generate and persist
one or more events to the event store.

You can create a command manually or using the generator provided with the `boost` CLI tool. Let's create a command to confirm a payment:

```shell script
boost new:command ConfirmPayment --fields cartID:UUID confirmationToken:string
```

You can specify as many fields as you want, and Booster will generate a class for you in the `src/commands` folder that more or less will look like this:

```typescript
@Command({
  authorize: 'all',
})
export class ConfirmPayment {
  public constructor(readonly cartID: UUID, readonly confirmationToken: string) {}

  public handle(register: Register): void {
    // The `register` parameter injected can be used to register any number of events.
    register.events(new CartPaid(this.cartId, this.confirmationToken))
  }
}
```

Note how no magic happened in the generator. The only thing that required for Booster to know that this class is a
command, is the `@Command` decorator. You could get the same result by writing the class yourself 😉

Continue reading about [Events](_04events.md)!
