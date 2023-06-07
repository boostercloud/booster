# Booster instrumentation

## Trace Decorator
The Trace Decorator is a **Booster** functionality that facilitates the reception of notifications whenever significant events occur in Booster's core, such as event dispatching or migration execution.

### Usage
To configure a custom tracer, you need to define an object with two methods: onStart and onEnd. The onStart method is called before the traced method is invoked, and the onEnd method is called after the method completes. Both methods receive a TraceInfo object, which contains information about the traced method and its arguments.

Here's an example of a custom tracer that logs trace events to the console:

```typescript
import {
  TraceParameters,
  BoosterConfig,
  TraceActionTypes,
} from '@boostercloud/framework-types'

class MyTracer {
  static async onStart(config: BoosterConfig, actionType: string, traceParameters: TraceParameters): Promise<void> {
    console.log(`Start ${actionType}: ${traceParameters.className}.${traceParameters.methodName}`)
  }

  static async onEnd(config: BoosterConfig, actionType: string, traceParameters: TraceParameters): Promise<void> {
    console.log(`End ${actionType}: ${traceParameters.className}.${traceParameters.methodName}`)
  }
}
```

You can then configure the tracer in your Booster application's configuration:

```typescript
import { BoosterConfig } from '@boostercloud/framework-types'
import { MyTracer } from './my-tracer'

const config: BoosterConfig = {
// ...other configuration options...
  trace: {
    enableTraceNotification: true,
    onStart: MyTracer.onStart,
    onEnd: MyTracer.onStart,
  }
}
```

In the configuration above, we've enabled trace notifications and specified our onStart and onEnd as the methods to use. Verbose disable will reduce the amount of information generated excluding the internal parameter in the trace parameters. 

Setting `enableTraceNotification: true` would enable the trace for all actions. You can either disable them by setting it to `false` or selectively enable only specific actions using an array of TraceActionTypes.

```typescript
import { BoosterConfig, TraceActionTypes } from '@boostercloud/framework-types'
import { MyTracer } from './my-tracer'

const config: BoosterConfig = {
// ...other configuration options...
  trace: {
    enableTraceNotification: [TraceActionTypes.DISPATCH_EVENT, TraceActionTypes.MIGRATION_RUN, 'OTHER'],
    includeInternal: false,
    onStart: MyTracer.onStart,
    onEnd: MyTracer.onStart,
  }
}
```

In this example, only DISPATCH_EVENT, MIGRATION_RUN and 'OTHER' actions will trigger trace notifications.

### TraceActionTypes

The TraceActionTypes enum defines all the traceable actions in Booster's core:

```typescript
export enum TraceActionTypes {
  CUSTOM,
  EVENT_HANDLERS_PROCESS,
  HANDLE_EVENT,
  DISPATCH_ENTITY_TO_EVENT_HANDLERS,
  DISPATCH_EVENTS,
  FETCH_ENTITY_SNAPSHOT,
  STORE_SNAPSHOT,
  LOAD_LATEST_SNAPSHOT,
  LOAD_EVENT_STREAM_SINCE,
  ENTITY_REDUCER,
  READ_MODEL_FIND_BY_ID,
  GRAPHQL_READ_MODEL_SEARCH,
  READ_MODEL_SEARCH,
  COMMAND_HANDLER,
  MIGRATION_RUN,
  GRAPHQL_DISPATCH,
  GRAPHQL_RUN_OPERATION,
  SCHEDULED_COMMAND_HANDLER,
  DISPATCH_SUBSCRIBER_NOTIFIER,
  READ_MODEL_SCHEMA_MIGRATOR_RUN,
  SCHEMA_MIGRATOR_MIGRATE,
}
```

### TraceInfo
The TraceInfo interface defines the data that is passed to the tracer's onBefore and onAfter methods:

```typescript
export interface TraceInfo {
  className: string
  methodName: string
  args: Array<unknown>
  traceId: UUID
  elapsedInvocationMillis?: number
  internal: {
    target: unknown
    descriptor: PropertyDescriptor
  }
  description?: string
}
```

`className` and `methodName` identify the function that is being traced.

### Adding the Trace Decorator to Your own async methods
In addition to using the Trace Decorator to receive notifications when events occur in Booster's core, you can also use it to trace your own methods. To add the Trace Decorator to your own methods, simply add @Trace() before your method declaration.

Here's an example of how to use the Trace Decorator on a custom method:

```typescript
import { Trace } from '@boostercloud/framework-core'
import { BoosterConfig, Logger } from '@boostercloud/framework-types'

export class MyCustomClass {
  @Trace('OTHER')
  public async myCustomMethod(config: BoosterConfig, logger: Logger): Promise<void> {
    logger.debug('This is my custom method')
    // Do some custom logic here...
  }
}
```

In the example above, we added the @Trace('OTHER') decorator to the myCustomMethod method. This will cause the method to emit trace events when it's invoked, allowing you to trace the flow of your application and detect performance bottlenecks or errors.

Note that when you add the Trace Decorator to your own methods, you'll need to configure your Booster instance to use a tracer that implements the necessary methods to handle these events. 
