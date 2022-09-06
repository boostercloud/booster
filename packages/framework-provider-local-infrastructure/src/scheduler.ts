import { ScheduledCommandMetadata, BoosterConfig } from '@boostercloud/framework-types'
import * as scheduler from 'node-schedule'

interface ScheduledCommandInfo {
  name: string
  metadata: ScheduledCommandMetadata
}

export function configureScheduler(config: BoosterConfig, userProject: any): void {
  const triggerScheduleCommand = userProject['boosterTriggerScheduledCommand']

  const isEmpty = (obj: {}) => {
    return Object.keys(obj).length === 0
  }

  Object.keys(config.scheduledCommandHandlers)
    .map((scheduledCommandName) => buildScheduledCommandInfo(config, scheduledCommandName))
    .filter((scheduledCommandInfo) => scheduledCommandInfo.metadata.scheduledOn && !isEmpty(scheduledCommandInfo.metadata.scheduledOn))
    .forEach((scheduledCommandInfo) => {
      scheduler.scheduleJob(scheduledCommandInfo.name, createCronExpression(scheduledCommandInfo.metadata), () => {
        triggerScheduleCommand({ typeName: scheduledCommandInfo.name })
      })
    })
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
