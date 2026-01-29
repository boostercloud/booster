/**
 * Azure Functions v4 Programming Model Types
 *
 * These types provide a wrapper for Azure Functions vs inputs to standardize
 * the interface between function handlers and Booster adapters
 */
import { HttpRequest, InvocationContext } from '@azure/functions'

/**
 * Standard wrapper for HTTP-triggered Azure Function v4 inputs.
 * This replaces the v3 Context objetc for HTTP triggers.
 */
export interface AzureHttpFunctionInput {
  request: HttpRequest
  context: InvocationContext
}

/**
 * Standard wrapper for CosmosDB-triggered Azure Function v4 inputs.
 */
export interface AzureCosmosDBFunctioninput {
  documents: unknown[]
  context: InvocationContext
}

/**
 * Standard wrapper for EventHub-triggered Azure Function v4 inputs.
 */
export interface AzureEventHubFunctionInput {
  messages: unknown[]
  context: InvocationContext
}

/**
 * Standard wrapper for Timer-triggered Azure Function v4 inputs.
 */
export interface AzureTimerFunctionInput {
  timer: {
    isPastDue: boolean
    schedule: {
      adjustForDST: boolean
    }
    scheduleStatus?: {
      last: string
      next: string
      lastUpdated: string
    }
  }
  context: InvocationContext
}

/**
 * Web PubSub connection context structure
 */
export interface WebPubSubConnectionContext {
  connectionId: string
  userId?: string
  hub: string
  eventType: string
  eventName: string
}

/**
 * Standard wrapper for Web PubSub-triggered Azure Function v4 inputs.
 * Note: connectionContext can be found in either request.connectionContext
 * or context.triggerMetadata.connectionContext depending in Azure Functions version.
 */
export interface AzureWebPubSubFunctionInput {
  request: {
    connectionContext?: WebPubSubConnectionContext
    data?: unknown
    dataType?: string
  }
  context: InvocationContext & {
    triggerMetadata?: {
      connectionContext?: WebPubSubConnectionContext
    }
  }
}

/**
 * Type alias for the raw request that Booster adapters receive.
 * This can be any of the v4 input types.
 */
export type AzureFunctionRawRequest =
  | AzureHttpFunctionInput
  | AzureCosmosDBFunctioninput
  | AzureEventHubFunctionInput
  | AzureTimerFunctionInput
  | AzureWebPubSubFunctionInput

/**
 * Type guard to check if the input is an HTTP function input
 * @param input The input to check
 * @returns True if the input is an HTTP function input, false otherwise
 */
export function isHttpFunctionInput(input: unknown): input is AzureHttpFunctionInput {
  return (
    typeof input === 'object' &&
    input !== null &&
    'request' in input &&
    'context' in input &&
    typeof (input as AzureHttpFunctionInput).request?.method === 'string'
  )
}

/**
 * Type guard to check if the input is a CosmosDB function input
 * @param input The input to check
 * @returns True if the input is a CosmosDB function input, false otherwise
 */
export function isCosmosDBFunctionInput(input: unknown): input is AzureCosmosDBFunctioninput {
  return (
    typeof input === 'object' &&
    input !== null &&
    'documents' in input &&
    'context' in input &&
    Array.isArray((input as AzureCosmosDBFunctioninput).documents)
  )
}

/**
 * Type guard to check if the input is an EventHub function input
 * @param input The input to check
 * @returns True if the input is an EventHub function input, false otherwise
 */
export function isEventHubFunctionInput(input: unknown): input is AzureEventHubFunctionInput {
  return (
    typeof input === 'object' &&
    input !== null &&
    'messages' in input &&
    'context' in input &&
    Array.isArray((input as AzureEventHubFunctionInput).messages)
  )
}

/**
 * Type guard to check if the input is a Timer function input
 * @param input The input to check
 * @returns True if the input is a Timer function input, false otherwise
 */
export function isTimerFunctionInput(input: unknown): input is AzureTimerFunctionInput {
  return (
    typeof input === 'object' &&
    input !== null &&
    'timer' in input &&
    'context' in input &&
    typeof (input as AzureTimerFunctionInput).timer?.isPastDue === 'boolean'
  )
}

/**
 * Type guard to check if the input is a Web PubSub function input.
 * Checks for connectionContext in both request.connectionContext and context.triggerMetadata.connectionContext
 * as Azure Functions v4 may provide it in different locations.
 * @param input The input to check
 * @returns True if the input is a Web PubSub function input, false otherwise
 */
export function isWebPubSubFunctionInput(input: unknown): input is AzureWebPubSubFunctionInput {
  if (typeof input !== 'object' || input === null || !('request' in input) || !('context' in input)) {
    return false
  }

  const typedInput = input as AzureWebPubSubFunctionInput

  // Check connectionContext in request.connectionContext
  if (typeof typedInput.request?.connectionContext?.connectionId === 'string') {
    return true
  }

  // Check connectionContext in context.triggerMetadata.connectionContext
  return typeof typedInput.context?.triggerMetadata?.connectionContext?.connectionId === 'string'
}

/**
 * Helper to extract connectionContext from either location in Web PubSub input
 * @param input The Azure Web PubSub function input
 * @returns The connection context if present, undefined otherwise
 */
export function getWebPubSubConnectionContext(
  input: AzureWebPubSubFunctionInput
): WebPubSubConnectionContext | undefined {
  return input.request?.connectionContext ?? input.context?.triggerMetadata?.connectionContext
}
