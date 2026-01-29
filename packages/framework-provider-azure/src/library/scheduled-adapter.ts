import { BoosterConfig, ScheduledCommandEnvelope, UUID } from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'
import { AzureTimerFunctionInput, isTimerFunctionInput } from '../types/azure-func-types'

/**
 * V4 Programming Model: Input wrapper for scheduled command functions.
 * Extends the base timer input with the command type name.
 */
export interface ScheduledCommandInput extends AzureTimerFunctionInput {
  /** The scheduled command type name */
  typeName: string
}

/**
 * Type guard to check if the input is a scheduled command input
 * @param input - The input to check
 * @returns True if the input is a ScheduledCommandInput, false otherwise
 */
export function isScheduledCommandInput(input: unknown): input is ScheduledCommandInput {
  return isTimerFunctionInput(input)
}

/**
 * Converts the raw timer trigger input to a Booster ScheduledCommandEnvelope.
 * In v4 programming model, the timer info and typeName are passed in the input wrapper.
 * @param config - Booster configuration
 * @param input - The raw input from the Azure Function
 * @returns A promise that resolves to a ScheduledCommandEnvelope
 */
export async function rawScheduledInputToEnvelope(
  config: BoosterConfig,
  input: unknown
): Promise<ScheduledCommandEnvelope> {
  const logger = getLogger(config, 'scheduled-adapter#rawScheduledInputToEnvelope')
  logger.debug('Received AzureScheduledCommand request: ', input)

  if (!isScheduledCommandInput(input)) {
    throw new Error(
      `Invalid scheduled command input. Expected ScheduledCommandInput with typeName and timer, but received: ${JSON.stringify(
        input
      )}`
    )
  }

  const { typeName, timer } = input
  logger.debug(`Processing scheduled command: ${typeName}, isPastDue: ${timer.isPastDue}`)

  return {
    requestID: UUID.generate(),
    typeName: typeName,
  }
}
