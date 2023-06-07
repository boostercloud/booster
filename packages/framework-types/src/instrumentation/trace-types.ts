import { UUID } from '../concepts'
import { BoosterConfig } from '../config'

export enum TraceTypes {
  START,
  END,
}

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
  READ_MODEL_SCHEMA_MIGRATOR_MIGRATE,
  SCHEMA_MIGRATOR_MIGRATE,
}

export interface TraceInfo {
  className: string
  methodName: string
  args: Array<unknown>
  traceId: UUID
  elapsedInvocationMillis?: number
  internal?: {
    target: unknown
    descriptor: PropertyDescriptor
  }
  description?: string
}

export interface TraceHandler {
  (config: BoosterConfig, actionType: TraceActionTypes, traceInfo: TraceInfo): Promise<void>
}

export interface TraceConfiguration {
  enableTraceNotification: boolean | Array<TraceActionTypes>
  includeInternal?: boolean
  onStart: TraceHandler
  onEnd: TraceHandler
}
