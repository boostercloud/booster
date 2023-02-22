# Advanced uses of the Register object

The Register object is a built-in object that is automatically injected by the framework into all command or event handlers to let users interact with the execution context. It can be used for a variety of purposes, including:

* Registering events to be emitted at the end of the command or event handler
* Manually flush the events to be persisted synchronously to the event store
* Access the current signed in user, their roles and other claims included in their JWT token
* In a command: Access the request context or alter the HTTP response headers

## Registering events

When handling a command or event, you can use the Register object to register one or more events that will be emitted when the command or event handler is completed. Events are registered using the `register.events()` method, which takes one or more events as arguments. For example:

```typescript
public async handle(register: Register): Promise<void> {
  // Do some work...
  register.events(new OrderConfirmed(this.orderID))
  // Do more work...
}
```

In this example, we're registering an OrderConfirmed event to be persisted to the event store when the handler finishes. You can also register multiple events by passing them as separate arguments to the register.events() method:

```typescript
public async handle(register: Register): Promise<void> {
  // Do some work...
  register.events(
    new OrderConfirmed(this.orderID),
    new OrderShipped(this.orderID)
  )
  // Do more work...
}
```

It's worth noting that events registered with `register.events()` aren't immediately persisted to the event store. Instead, they're stored in memory until the command or event handler finishes executing. To force the events to be persisted immediately, you can call the `register.flush()` method that is described in the next section.

## Manually flush the events

As mentioned in the previous section, events registered with `register.events()` aren't immediately persisted to the event store. Instead, they're stored in memory until the command or event handler finishes its execution, but this doesn't work in all situations, sometimes it's useful to store partial updates of a longer process, and some scenarios could accept partial successes. To force the events to be persisted and wait for the database to confirm the write, you can use the `register.flush()` method.

The `register.flush()` method takes no arguments and returns a promise that resolves when the events have been successfully persisted to the event store. For example:

```typescript
public async handle(register: Register): Promise<void> {
  // Do some work...
  register.events(new OrderConfirmed(this.orderID))
  await register.flush()
  const mailID = await sendConfirmationEmail(this.orderID)
  register.events(new MailSent(this.orderID, mailID))
  // Do more work...
}
```

In this example, we're calling `register.flush()` after registering an `OrderConfirmed` event to ensure that it's persisted to the event store before continuing with the rest of the handler logic. In this way, even if an error happens while sending the confirmation email, the order will be persisted.

## Access the current signed in user

When handling a command or event, you can use the injected `Register` object to access the currently signed-in user as well as any metadata included in their JWT token like their roles or other claims (the specific claims will depend on the specific auth provider used). To do this, you can use the `currentUser` property. This property is an instance of the `UserEnvelope` class, which has the following properties:

```typescript
export interface UserEnvelope {
  id?: string // An optional identifier of the user
  username: string // The unique username of the current user
  roles: Array<string> // The list of role names assigned to this user
  claims: Record<string, unknown> // An object containing the claims included in the body of the JWT token
  header?: Record<string, unknown> // An object containing the headers of the JWT token for further verification
}
```

For example, to access the username of the currently signed-in user, you can use the `currentUser.username` property:

```typescript
public async handle(register: Register): Promise<void> {
  console.log(`The currently signed-in user is ${register.currentUser?.username}`)
}

// Output: The currently signed-in user is john.doe
```

## Command-specific features

The command handlers are executed as part of a GraphQL mutation request, so they have access to a few additional features that are specific to commands that can be used to access the request context or alter the HTTP response headers.

### Access the request context

The request context is injected in the command handler as part of the register command and you can access it using the `context` property. This property is an instance of the `ContextEnvelope` interface, which has the following properties:

```typescript
export interface ContextEnvelope {
  /** Decoded request header and body */
  request: {
    headers: unknown
    body: unknown
  }
  /** Provider-dependent raw request context object */
  rawContext: unknown
}
```

The `request` property exposes a normalized version of the request headers and body that can be used regardless the provider. We recommend using this property instead of the `rawContext` property, as it will be more portable across providers.

The `rawContext` property exposes the full raw request context as it comes in the original request, so it will depend on the underlying provider used. For instance, in AWS, it will be [a lambda context object](https://docs.aws.amazon.com/lambda/latest/dg/nodejs-context.html), while in Azure it will be [an Azure Functions context object](https://docs.microsoft.com/en-us/azure/azure-functions/functions-reference-node#context-object).

### Alter the HTTP response headers

Finally, you can use the `responseHeaders` property to alter the HTTP response headers that will be sent back to the client. This property is a plain Typescript object which is initialized with the default headers. You can add, remove or modify any of the headers by using the standard object methods:

```typescript
public async handle(register: Register): Promise<void> {
  register.responseHeaders['X-My-Header'] = 'My custom header'
  register.responseHeaders['X-My-Other-Header'] = 'My other custom header'
  delete register.responseHeaders['X-My-Other-Header']
}
```