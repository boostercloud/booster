import { BoosterConfig, ScheduleInterface } from '@boostercloud/framework-types'
import { FunctionDefinition } from '../types/functionDefinition'

interface TimeTriggerFunction {
  name: string
  config: {
    bindings: [
      {
        type: string
        name: string
        direction: string
        schedule: string
      }
    ]
    scriptFile: string
    entryPoint: string
  }
}

export class SchedulesFunctions {
  public constructor(readonly config: BoosterConfig) {}

  public getFunctionDefinitions(): Array<FunctionDefinition> | undefined {
    if (Object.keys(this.config.scheduledCommandHandlers).length) {
      return Object.keys(this.config.scheduledCommandHandlers).map((scheduledCommandName) => {
        const scheduledCommandMetadata = this.config.scheduledCommandHandlers[scheduledCommandName]
        const cronExpression = SchedulesFunctions.createCronExpression(scheduledCommandMetadata.scheduledOn)
        return {
          name: `scheduleFunction-${scheduledCommandName}`,
          config: {
            bindings: [
              {
                type: 'timerTrigger',
                name: `${scheduledCommandName}`,
                direction: 'in',
                schedule: `${cronExpression}`,
              },
            ],
            scriptFile: '../dist/index.js',
            entryPoint: this.config.scheduledTaskHandler.split('.')[1],
          },
        } as TimeTriggerFunction
      }) as Array<FunctionDefinition>
    }
    return undefined
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
    const expression = `0 ${minute} ${hour} ${day} ${month} ${weekDay}`
    const neverRunByDefault = '0 0 5 31 2 ? *'
    return `${expression !== '* * * ? * *' ? expression : neverRunByDefault}`
  }
}
