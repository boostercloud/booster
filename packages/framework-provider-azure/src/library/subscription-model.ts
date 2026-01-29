import { InvocationContext } from '@azure/functions'
import { BoosterMetadata } from '@boostercloud/framework-types'

/**
 * Raw event structure from Cosmos DB change feed
 */
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

/**
 * V4 Programming Model: Input wrapper for subscription notifier functions.
 * In v4, documents are passed directly as the first argument to the handler.
 */
export interface SubscriptionNotifierInput {
  documents: RawEvent[]
  context: InvocationContext
  /** The read model type name, extracted from function configuration */
  typeName: string
}

/**
 * Type guard to check if the input is a v4 subcription notifier input
 * @param input - The input to check
 * @returns True if the input is a SubscriptionNotifierInput, false otherwise
 */
export function isSubscriptionNotifierInput(input: unknown): input is SubscriptionNotifierInput {
  return (
    typeof input === 'object' &&
    input !== null &&
    'documents' in input &&
    'context' in input &&
    'typeName' in input &&
    Array.isArray((input as SubscriptionNotifierInput).documents)
  )
}
