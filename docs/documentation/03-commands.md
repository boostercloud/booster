# Commands and Command Handlers - The Write Pipeline

![Booster architecture](../img/booster-arch.png)

Commands and Command Handlers define the **Write** API of your application (highlighted in yellow in the diagram). Commands are objects that are sent to the `/commands` endpoint.

Instead of a controller, like in a traditional architecture like [MVC](https://www.martinfowler.com/eaaCatalog/modelViewController.html), you define a _Handler_ method, which will be in charge of processing the command, calling any third-party services, performing side-effects, and finally, registering [events](#events).

> **Note:** Event registration is not mandatory, but we **strongly** recommend registering at least one event for any possible final state, even in the case of a failure, to make your application activity easier to trace and debug.

A command is a class, decorated with the `@Command` decorator, that defines a data structure
and a handler method. The method will process the commands and optionally generate and persist
one or more events to the event store.

To create a command, you can do so manually, or by running the generator provided by the `boost` CLI tool. Let's create a command to confirm a payment:

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
    // implementation for the handler
  }
}
```

The `handle` method is the Handler we were talking about some paragraphs ago. Here you can write arbitrary code like integration with 3rd party services, data validation, and any other side effect that your application requires.

Again, we strongly advise that after a command has been executed, even if it didn’t succeed, you `register` an event
specifying what happened. This also goes in the `handle` method, and that’s what the `register` parameter is used for:

```typescript
public handle(register: Register): void {
  // i.e. code that performs a payment
  register.events(new CartPaid(this.cartId, this.confirmationToken))
}
```

Note how no magic happened here yet. The only thing that is needed for Booster to know that this class is a
command, is the `@Command` decorator. Apart from that, the generator only writes code, nothing else!
You could achieve the same result by writing the class yourself 😉

Continue reading about [Events](04-events.md)!