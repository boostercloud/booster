import { UUID } from '../concepts'
import { BoosterConfig } from '../config'

export enum TraceTypes {
  START,
  END,
}

export enum TraceActionTypes {
  CUSTOM = 'CUSTOM',
  EVENT_HANDLERS_PROCESS = 'EVENT_HANDLERS_PROCESS',
  HANDLE_EVENT = 'HANDLE_EVENT',
  DISPATCH_ENTITY_TO_EVENT_HANDLERS = 'DISPATCH_ENTITY_TO_EVENT_HANDLERS',
  DISPATCH_EVENTS = 'DISPATCH_EVENTS',
  FETCH_ENTITY_SNAPSHOT = 'FETCH_ENTITY_SNAPSHOT',
  STORE_SNAPSHOT = 'STORE_SNAPSHOT',
  LOAD_LATEST_SNAPSHOT = 'LOAD_LATEST_SNAPSHOT',
  LOAD_EVENT_STREAM_SINCE = 'LOAD_EVENT_STREAM_SINCE',
  ENTITY_REDUCER = 'ENTITY_REDUCER',
  READ_MODEL_FIND_BY_ID = 'READ_MODEL_FIND_BY_ID',
  GRAPHQL_READ_MODEL_SEARCH = 'GRAPHQL_READ_MODEL_SEARCH',
  READ_MODEL_SEARCH = 'READ_MODEL_SEARCH',
  COMMAND_HANDLER = 'COMMAND_HANDLER',
  MIGRATION_RUN = 'MIGRATION_RUN',
  GRAPHQL_DISPATCH = 'GRAPHQL_DISPATCH',
  GRAPHQL_RUN_OPERATION = 'GRAPHQL_RUN_OPERATION',
  SCHEDULED_COMMAND_HANDLER = 'SCHEDULED_COMMAND_HANDLER',
  DISPATCH_SUBSCRIBER_NOTIFIER = 'DISPATCH_SUBSCRIBER_NOTIFIER',
  READ_MODEL_SCHEMA_MIGRATOR_MIGRATE = 'READ_MODEL_SCHEMA_MIGRATOR_MIGRATE',
  SCHEMA_MIGRATOR_MIGRATE = 'SCHEMA_MIGRATOR_MIGRATE',
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
  (config: BoosterConfig, actionType: string, traceInfo: TraceInfo): Promise<void>
}

export interface TraceConfiguration {
  enableTraceNotification: boolean | Array<string>
  includeInternal?: boolean
  onStart: TraceHandler
  onEnd: TraceHandler
}
