import { ScheduledCommandMetadata, BoosterConfig } from '@boostercloud/framework-types'
import * as scheduler from 'node-schedule'

interface ScheduledCommandInfo {
  name: string
  metadata: ScheduledCommandMetadata
}

export function configureScheduler(config: BoosterConfig, userProject: any): scheduler.Job[] {
  const triggerScheduleCommand = userProject['boosterTriggerScheduledCommand']
  const cronJobs: scheduler.Job[] = []
  Object.keys(config.scheduledCommandHandlers)
    .map((scheduledCommandName) => buildScheduledCommandInfo(config, scheduledCommandName))
    .filter((scheduledCommandInfo) => scheduledCommandInfo.metadata.scheduledOn)
    .forEach((scheduledCommandInfo) => {
      const cronJob = scheduler.scheduleJob(
        scheduledCommandInfo.name,
        createCronExpression(scheduledCommandInfo.metadata),
        () => {
          triggerScheduleCommand({ typeName: scheduledCommandInfo.name })
        }
      )
      cronJobs.push(cronJob)
    })
  return cronJobs
}

export function createCronExpression(scheduledCommandMetadata: ScheduledCommandMetadata): string {
  const {
    second = '*',
    minute = '*',
    hour = '*',
    day = '*',
    month = '*',
    weekDay = '*',
  } = scheduledCommandMetadata.scheduledOn
  return `${second} ${minute} ${hour} ${day} ${month} ${weekDay}`
}

export function buildScheduledCommandInfo(config: BoosterConfig, scheduledCommandName: string): ScheduledCommandInfo {
  return {
    name: scheduledCommandName,
    metadata: config.scheduledCommandHandlers[scheduledCommandName],
  }
}
