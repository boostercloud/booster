# Error handling

Booster includes a global error handler annotation `@GlobalErrorHandler` that will catch all errors that are thrown by:

- **Command Handling Errors**: Errors thrown by the `handle` method of the command.
  **Program handling errors**: Errors thrown by the ScheduledCommand `handle` method.
  **Event Handle errors**: Errors thrown by the `Event Handle` method.
- **Reducer errors**: Errors thrown by the `@Reduces` method of the entity.
- **Projection errors**: Errors thrown in the ReadModel `@Projects` method.
- All errors: Errors thrown in any of the previous methods. This method will always be called, also when calling any of the above methods.

You can trap and return new errors in any of these methods annotating a class with `@GlobalErrorHandler` and implementing the following methods:

**Command handle errors**:

```typescript
onCommandHandlerError?(error: Error, command: CommandEnvelope): Promise<Error | undefined>
```

**Schedule handle errors**:

```typescript
onScheduledCommandHandlerError?(error: Error): Promise<Error | undefined>
```

**Event handler errors**:

```typescript
onDispatchEventHandlerError?(error: Error, eventInstance: EventInterface): Promise<Error | undefined>
```

**Reducer errors**:

```typescript
onReducerError?(error: Error, eventInstance: EventInterface, snapshotInstance: EntityInterface | null): Promise<Error | undefined>
```

**Projections errors**:

```typescript
onProjectionError?(error: Error, entity: EntityInterface, readModel: ReadModelInterface | undefined): Promise<Error | undefined>
```

**All errors**

```typescript
  onError?(error: Error | undefined): Promise<Error | undefined>
```

Example:

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

**Note**: if you want to ignore the error thrown, you can simply return `undefined` from the error handler.
