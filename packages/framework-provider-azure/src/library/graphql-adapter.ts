import {
  BoosterConfig,
  EventType,
  GraphQLClientMessage,
  GraphQLOperation,
  GraphQLRequestEnvelope,
  GraphQLRequestEnvelopeError,
} from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'
import {
  AzureHttpFunctionInput,
  AzureWebPubSubFunctionInput,
  getWebPubSubConnectionContext,
  isHttpFunctionInput,
  isWebPubSubFunctionInput,
} from '../types/azure-func-types'

export async function rawGraphQLRequestToEnvelope(
  config: BoosterConfig,
  rawRequest: unknown
): Promise<GraphQLRequestEnvelope | GraphQLRequestEnvelopeError> {
  const logger = getLogger(config, 'graphql-adapter#rawGraphQLRequestToEnvelope')

  // Handle HTTP requests (v4 programming model)
  if (isHttpFunctionInput(rawRequest)) {
    return handleHttpRequest(config, rawRequest, logger)
  }

  // Handle WebSocket requests (Web PubSub v4 programming model)
  if (isWebPubSubFunctionInput(rawRequest)) {
    return handleWebPubSubRequest(config, rawRequest, logger)
  }

  // Fallback error for unknown input types
  const error = new Error('Unknown request type received by GraphQL adapter')
  logger.error('Received unknown request type:', rawRequest)
  return {
    error,
    requestID: 'unknown',
    eventType: 'MESSAGE',
    context: {
      request: {
        headers: {},
        body: undefined,
      },
      rawContext: rawRequest,
    },
  }
}

async function handleHttpRequest(
  config: BoosterConfig,
  input: AzureHttpFunctionInput,
  logger: ReturnType<typeof getLogger>
): Promise<GraphQLRequestEnvelope | GraphQLRequestEnvelopeError> {
  const { request, context } = input
  logger.debug('Received GraphQL request: ', request.url)

  const requestID = context.invocationId

  try {
    // Parse the request body
    let graphQLValue: unknown = undefined
    try {
      graphQLValue = await request.json()
    } catch {
      // Body might not be JSON, try text
      const textBody = await request.text()
      if (textBody) {
        try {
          graphQLValue = JSON.parse(textBody)
        } catch {
          graphQLValue = textBody
        }
      }
    }

    // Convert headers to a plain object
    const headers: Record<string, string> = {}
    request.headers.forEach((value, key) => {
      headers[key] = value
    })

    return {
      requestID,
      eventType: 'MESSAGE' as EventType,
      token: request.headers.get('authorization') || undefined,
      value: graphQLValue as GraphQLOperation | GraphQLClientMessage | undefined,
      context: {
        request: {
          headers,
          body: graphQLValue,
        },
        rawContext: input,
      },
    }
  } catch (e) {
    const error = e as Error
    logger.error('Error processing GraphQL HTTP request: ', error)

    // Convert headers to a plain object for error response
    const headers: Record<string, string> = {}
    request.headers.forEach((value, key) => {
      headers[key] = value
    })

    return {
      error,
      requestID,
      eventType: 'MESSAGE',
      context: {
        request: {
          headers,
          body: undefined,
        },
        rawContext: input,
      },
    }
  }
}

async function handleWebPubSubRequest(
  config: BoosterConfig,
  input: AzureWebPubSubFunctionInput,
  logger: ReturnType<typeof getLogger>
): Promise<GraphQLRequestEnvelope | GraphQLRequestEnvelopeError> {
  const { request: webPubSubRequest, context } = input
  const requestID = context.invocationId

  // Get connection context from either location (request or triggerMetadata)
  const connectionContext = getWebPubSubConnectionContext(input)

  if (!connectionContext) {
    const error = new Error('WebPubSub connectionContext not found in request or triggerMetadata')
    logger.error('Missing connectionContext. Input structure', JSON.stringify(input, null, 2))
    return {
      error,
      requestID,
      eventType: 'MESSAGE',
      context: {
        request: {
          headers: {},
          body: undefined,
        },
        rawContext: input,
      },
    }
  }

  logger.debug('Received GraphQL WebSocket request: ', connectionContext.eventName)

  const connectionID = connectionContext.connectionId

  // Map Web PubSub event names to Booster EventType
  let eventType: EventType = 'MESSAGE'
  if (connectionContext.eventType?.toLowerCase() === 'system') {
    const eventName = connectionContext.eventName?.toUpperCase()
    if (eventName === 'CONNECT' || eventName === 'DISCONNECT') {
      eventType = eventName as EventType
    }
  }

  try {
    // Parse the data if it's a JSON string (Web PubSub sends data as string)
    let messageData = webPubSubRequest?.data
    if (typeof messageData === 'string') {
      try {
        messageData = JSON.parse(messageData)
        logger.debug('Parsed Web PubSub message data from string:', messageData)
      } catch {
        logger.debug('WebPubSub data is not valid JSON, using as-is:', messageData)
      }
    }

    return {
      requestID,
      connectionID,
      eventType,
      value: messageData as GraphQLOperation | GraphQLClientMessage | undefined,
      context: {
        request: {
          headers: {},
          body: messageData,
        },
        rawContext: input,
      },
    }
  } catch (e) {
    const error = e as Error
    logger.error('Error processing GraphQL WebSocket request: ', error)
    return {
      error,
      requestID,
      connectionID,
      eventType,
      context: {
        request: {
          headers: {},
          body: undefined,
        },
        rawContext: input,
      },
    }
  }
}
