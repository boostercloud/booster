# Error handling

## Error handling in Booster

Booster provides a default error handling mechanism that will try to catch all the errors that are thrown in the application and will log them. This is useful for debugging purposes, but you may want to customize the error handling in your application. For example, you may want to send an email to the administrator when an error occurs.

### Custom error handling

To customize the error handling, you need to create a class decorated with the `@GlobalErrorHandler` decorator. This class will contain the methods that will be called when an error is thrown. There is one method for each component in the application where an error can be thrown. All these functions receive the error that was thrown and the information about the component that was being executed when the error occurred. 

They must return a promise that resolves to an `Error` object or `undefined`. If the promise resolves to `undefined`, the error will be ignored and the application will continue working. If the promise resolves to an `Error` object, the error will be thrown.

### Command handle errors

These are the errors that are thrown in the `handle` method of the `@Command`. You can catch and return new errors in this method annotating a class with `@GlobalErrorHandler` and implementing the following method:

```typescript
@GlobalErrorHandler()
export class MyErrorHandler {
  public onCommandHandlerError(error: Error, command: CommandEnvelope): Promise<Error | undefined> {
    // Do something with the error
  }
}
```

Tis method receives the error that was thrown and the command that was being handled when the error occurred. 

### Scheduled command handle errors

These are the errors that are thrown in the `handle` method of the `@ScheduledCommand`. You can catch and return new errors in this function annotating a class with `@GlobalErrorHandler` and implementing the following method:

```typescript
@GlobalErrorHandler()
export class MyErrorHandler {
  public onScheduledCommandHandlerError(error: Error): Promise<Error | undefined> {
    // Do something with the error
  }
}
```

This method receives the error that was thrown.

### Event handler errors

These are the errors that are thrown in the `handle` method of the `@Event`. You can catch and return new errors in this function annotating a class with `@GlobalErrorHandler` and implementing the following method:

```typescript
@GlobalErrorHandler()
export class MyErrorHandler {
  public onEventHandlerError(error: Error, event: EventEnvelope): Promise<Error | undefined> {
    // Do something with the error
  }
}
```

This method receives the error that was thrown and the event that was being handled when the error occurred.

### Reducer errors

These are the errors that are thrown in the `@Reduces` method of the `@Entity`. You can catch and return new errors in this function annotating a class with `@GlobalErrorHandler` and implementing the following method:

```typescript
@GlobalErrorHandler()
export class MyErrorHandler {
  public onReducerError(error: Error, entity: EntityInterface, snapshot: EntityInterface | null): Promise<Error | undefined> {
    // Do something with the error
  }
}
```

This method receives the error that was thrown, the name of the entity, the ID of the entity, and the name of the reducer.

### Projection errors

These are the errors that are thrown in the `@Projects` method of the `@ReadModel`. You can catch and return new errors in this function annotating a class with `@GlobalErrorHandler` and implementing the following method:

```typescript
@GlobalErrorHandler()
export class MyErrorHandler {
  public onProjectionError(error: Error, readModel: ReadModelInterface, entity: EntityInterface): Promise<Error | undefined> {
    // Do something with the error
  }
}
```

This method receives the error that was thrown, the name of the read model, the ID of the read model, and the name of the projection.

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
  public static async onCommandHandlerError(error: Error, command: CommandEnvelope): Promise<Error | undefined> {
    return error
  }

  public static async onScheduledCommandHandlerError(error: Error): Promise<Error | undefined> {
    return error
  }

  public static async onDispatchEventHandlerError(error: Error, eventInstance: EventInterface): Promise<Error | undefined> {
    return error
  }

  public static async onReducerError(
    error: Error,
    eventInstance: EventInterface,
    snapshotInstance: EntityInterface | null
  ): Promise<Error | undefined> {
    return error
  }

  public static async onProjectionError(
    error: Error,
    entity: EntityInterface,
    readModel: ReadModelInterface | undefined
  ): Promise<Error | undefined> {
    return error
  }

  public static async onError(error: Error | undefined): Promise<Error | undefined> {
    return error
  }
}
```
