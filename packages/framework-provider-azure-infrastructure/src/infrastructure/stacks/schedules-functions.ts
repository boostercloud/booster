import { BoosterConfig, ScheduledCommandMetadata, ScheduleInterface } from '@boostercloud/framework-types'
import { FunctionDefinition, ScheduleFunctionDefinition } from '../types/functionDefinition'

interface ScheduledCommandInfo {
  name: string
  metadata: ScheduledCommandMetadata
}

export class SchedulesFunctions {
  public constructor(readonly config: BoosterConfig) {}

  public getFunctionDefinitions(): Array<FunctionDefinition> | undefined {
    if (SchedulesFunctions.isEmpty(this.config.scheduledCommandHandlers)) return
    return Object.keys(this.config.scheduledCommandHandlers)
      .map((scheduledCommandName) => this.buildScheduledCommandInfo(scheduledCommandName))
      .filter((scheduledCommandInfo) => !SchedulesFunctions.isEmpty(scheduledCommandInfo.metadata.scheduledOn))
      .map((scheduledCommandInfo) =>
        this.scheduledCommandInfoToTimeTriggerFunction(scheduledCommandInfo)
      ) as Array<FunctionDefinition>
  }

  private scheduledCommandInfoToTimeTriggerFunction(
    scheduledCommandInfo: ScheduledCommandInfo
  ): ScheduleFunctionDefinition {
    const cronExpression = SchedulesFunctions.createCronExpression(scheduledCommandInfo.metadata.scheduledOn)
    return {
      name: `scheduleFunction-${scheduledCommandInfo.name}`,
      config: {
        bindings: [
          {
            type: 'timerTrigger',
            name: `${scheduledCommandInfo.name}`,
            direction: 'in',
            schedule: `${cronExpression}`,
          },
        ],
        scriptFile: '../dist/index.js',
        entryPoint: this.config.scheduledTaskHandler.split('.')[1],
      },
    }
  }

  private static isEmpty(value: Record<string, unknown> | ScheduleInterface): boolean {
    return !Object.keys(value).length
  }

  private buildScheduledCommandInfo(scheduledCommandName: string): ScheduledCommandInfo {
    return {
      name: scheduledCommandName,
      metadata: this.config.scheduledCommandHandlers[scheduledCommandName],
    }
  }

  /**
   * Azure Functions uses the NCronTab library to interpret NCRONTAB expressions.
   * An NCRONTAB expression is similar to a CRON expression except that it includes an additional sixth field at the beginning to use for time precision in seconds:
   * {second} {minute} {hour} {day} {month} {day-of-week}
   * @param scheduledCommandMetadata
   * @private
   */
  private static createCronExpression(scheduledCommandMetadata: ScheduleInterface): string {
    const { minute = '*', hour = '*', day = '*', month = '*', weekDay = '*' } = scheduledCommandMetadata
    return `0 ${minute} ${hour} ${day} ${month} ${weekDay}`
  }
}
