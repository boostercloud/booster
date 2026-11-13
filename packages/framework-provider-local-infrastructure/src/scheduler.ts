import { ScheduledCommandMetadata, BoosterConfig } from '@boostercloud/framework-types'
import * as scheduler from 'node-schedule'

interface ScheduledCommandInfo {
  name: string
  metadata: ScheduledCommandMetadata
}

type TriggerScheduledCommands = (request: { typeName: string }) => Promise<void>

/**
 * Validates the scheduled commands setup and returns a function that schedules them. The validation runs
 * immediately, so a missing trigger export fails the provider startup instead of crashing later.
 */
export function prepareScheduler(config: BoosterConfig, userProject: Record<string, unknown>): () => void {
  const scheduledCommandInfos = Object.keys(config.scheduledCommandHandlers)
    .map((scheduledCommandName) => buildScheduledCommandInfo(config, scheduledCommandName))
    .filter((scheduledCommandInfo) => scheduledCommandInfo.metadata.scheduledOn)
  if (scheduledCommandInfos.length === 0) return () => {}

  const triggerScheduledCommands = getTriggerScheduledCommands(userProject)
  return () =>
    scheduledCommandInfos.forEach((scheduledCommandInfo) => {
      scheduler.scheduleJob(scheduledCommandInfo.name, createCronExpression(scheduledCommandInfo.metadata), () => {
        triggerScheduledCommands({ typeName: scheduledCommandInfo.name }).catch((error) =>
          console.error(`Scheduled command ${scheduledCommandInfo.name} failed:`, error)
        )
      })
    })
}

/**
 * Resolves the scheduled commands trigger exported by the user app. `boosterTriggerScheduledCommands` is the
 * current name; the deprecated `boosterTriggerScheduledCommand` alias is still accepted for older apps.
 */
function getTriggerScheduledCommands(userProject: Record<string, unknown>): TriggerScheduledCommands {
  const trigger = userProject?.boosterTriggerScheduledCommands ?? userProject?.boosterTriggerScheduledCommand
  if (typeof trigger !== 'function') {
    throw new Error(
      'Scheduled commands are defined, but the app does not export `boosterTriggerScheduledCommands`. ' +
        "Add it to the exports from '@boostercloud/framework-core' in your app's src/index.ts"
    )
  }
  return trigger as TriggerScheduledCommands
}

function createCronExpression(scheduledCommandMetadata: ScheduledCommandMetadata): string {
  const { minute = '*', hour = '*', day = '*', month = '*', weekDay = '*' } = scheduledCommandMetadata.scheduledOn
  return `${minute} ${hour} ${day} ${month} ${weekDay}`
}

function buildScheduledCommandInfo(config: BoosterConfig, scheduledCommandName: string): ScheduledCommandInfo {
  return {
    name: scheduledCommandName,
    metadata: config.scheduledCommandHandlers[scheduledCommandName],
  }
}
