# Error handling

## Error handling in Booster

Booster provides a default error handling mechanism that will try to catch all the errors that are thrown in the application and will log them. This is useful for debugging purposes, but you may want to customize the error handling in your application. For example, you may want to email the administrator when an error occurs.

### Custom error handling

To customize the error handling, you need to create a class decorated with the `@GlobalErrorHandler` decorator. This class will contain the methods that will be called when an error is thrown. There is one method for each component in the application where an error can be thrown. All these functions receive the error that was thrown and the information about the component that was being executed when the error occurred. 

They must return a promise that resolves to an `Error` object or `undefined`. If the promise resolves to `undefined`, the error will be ignored and **Booster** will continue working. If the promise resolves to an `Error` object, the error will be thrown and **Booster** will handle it on a case-by-case basis in the default way.

### Command handle errors

These are the errors that are thrown in the `handle` method of the `@Command`. You can catch and return new errors in this method annotating a class with `@GlobalErrorHandler` and implementing the following method:

```typescript
@GlobalErrorHandler()
export class MyErrorHandler {
  public static async onCommandHandlerError(
    error: Error,
    commandEnvelope: CommandEnvelope,
    commandMetadata: CommandMetadata
  ): Promise<Error | undefined> {
    // Do something with the error
  }
}
```

This method receives the error that was thrown and the command that was being handled when the error occurred. 

### Scheduled command handle errors

These are the errors that are thrown in the `handle` method of the `@ScheduledCommand`. You can catch and return new errors in this function annotating a class with `@GlobalErrorHandler` and implementing the following method:

```typescript
@GlobalErrorHandler()
export class MyErrorHandler {
  public static async onScheduledCommandHandlerError(
    error: Error,
    scheduledCommandEnvelope: ScheduledCommandEnvelope,
    scheduledCommandMetadata: ScheduledCommandMetadata
  ): Promise<Error | undefined> {
    // Do something with the error
  }
}
```

Note that if an error is thrown on a ScheduleCommand, **Booster** will stop working.

### Event handler errors

These are the errors that are thrown in the `handle` method of the `@Event`. You can catch and return new errors in this function annotating a class with `@GlobalErrorHandler` and implementing the following method:

```typescript
@GlobalErrorHandler()
export class MyErrorHandler {
  public static async onDispatchEventHandlerError(
    error: Error,
    eventEnvelope: EventEnvelope | NotificationInterface,
    eventHandlerMetadata: unknown,
    eventInstance: EventInterface
  ): Promise<Error | undefined> {
    // Do something with the error
  }
}
```

### Reducer errors

These are the errors that are thrown in the `@Reduces` method of the `@Entity`. You can catch and return new errors in this function annotating a class with `@GlobalErrorHandler` and implementing the following method:

```typescript
@GlobalErrorHandler()
export class MyErrorHandler {
  public static async onReducerError(
    error: Error,
    eventEnvelope: EventEnvelope,
    reducerMetadata: ReducerMetadata,
    eventInstance: EventInterface,
    snapshotInstance: EntityInterface | null
  ): Promise<Error> {
    // Do something with the error
  }
}
```

Note you can not ignore a Reducer error as the new entity could not be created

### Event errors

These are the errors that are thrown if the event doesn't exist. You can catch and return new errors in this function annotating a class with `@GlobalErrorHandler` and implementing the following method:

```typescript
@GlobalErrorHandler()
export class MyErrorHandler {
  public static async onEventError(error: Error, eventEnvelope: EventEnvelope): Promise<Error | undefined> {
    // Do something with the error
  }
}
```

This method receives the error that was thrown and the event received.

### Projection errors

These are the errors that are thrown in the `@Projects` method of the `@ReadModel`. You can catch and return new errors in this function annotating a class with `@GlobalErrorHandler` and implementing the following method:

```typescript
@GlobalErrorHandler()
export class MyErrorHandler {
  public static async onProjectionError(
    error: Error,
    entityEnvelope: EntitySnapshotEnvelope,
    projectionMetadata: ProjectionMetadata<EntityInterface>,
    entity: EntityInterface,
    readModel: ReadModelInterface | undefined
  ): Promise<Error | undefined> {
    // Do something with the error
  }
}
```

### All errors

These are the errors that are thrown in any of the previous methods. You can catch and return new errors in this function annotating a class with `@GlobalErrorHandler` and implementing the following method:

```typescript
@GlobalErrorHandler()
export class MyErrorHandler {
  public onError(error: Error | undefined): Promise<Error | undefined> {
    // Do something with the error
  }
}
```

This method receives the error that was thrown.

## Global error handler example

You can implement all error handling functions in the same class. Here is an example of a global error handler that will handle all the errors mentioned above:

```typescript
@GlobalErrorHandler()
export class AppErrorHandler {
  public static async onCommandHandlerError(
    error: Error,
    commandEnvelope: CommandEnvelope,
    commandMetadata: CommandMetadata
  ): Promise<Error | undefined> {
    return error
  }

  public static async onScheduledCommandHandlerError(
    error: Error,
    scheduledCommandEnvelope: ScheduledCommandEnvelope,
    scheduledCommandMetadata: ScheduledCommandMetadata
  ): Promise<Error | undefined> {
    return error
  }

  public static async onDispatchEventHandlerError(
    error: Error,
    eventEnvelope: EventEnvelope | NotificationInterface,
    eventHandlerMetadata: unknown,
    eventInstance: EventInterface
  ): Promise<Error | undefined> {
    return error
  }

  public static async onReducerError(
    error: Error,
    eventEnvelope: EventEnvelope,
    reducerMetadata: ReducerMetadata,
    eventInstance: EventInterface,
    snapshotInstance: EntityInterface | null
  ): Promise<Error | undefined> {
    return error
  }

  public static async onProjectionError(
    error: Error,
    entityEnvelope: EntitySnapshotEnvelope,
    projectionMetadata: ProjectionMetadata<EntityInterface>,
    entity: EntityInterface,
    readModel: ReadModelInterface | undefined
  ): Promise<Error | undefined> {
    return error
  }

  public static async onEventError(error: Error, eventEnvelope: EventEnvelope): Promise<Error | undefined> {
    return error
  }

  public static async onError(error: Error | undefined): Promise<Error | undefined> {
    return error
  }
}
```
