import { BoosterMetadata } from '@boostercloud/framework-types'

export interface SubscriptionContext {
  invocationId: string
  traceContext: unknown
  executionContext: ExecutionContext
  bindings: Bindings
  bindingData: unknown
  bindingDefinitions: unknown
}

export interface Bindings {
  rawEvent: RawEvent[]
}

export interface RawEvent {
  id: string
  _rid: string
  _self: string
  _ts: number
  _etag: string
  [key: string]: unknown
  boosterMetadata: BoosterMetadata
  _lsn: number
}

export interface ExecutionContext {
  invocationId: string
  functionName: string
  functionDirectory: string
  retryContext: null
}
