import { BoosterConfig, ScheduledCommandEnvelope, ScheduleInterface } from '@boostercloud/framework-types'
import { Stack } from '@aws-cdk/core'
import { Rule, Schedule, RuleTargetInput } from '@aws-cdk/aws-events'
import { LambdaFunction } from '@aws-cdk/aws-events-targets'
import { Code, Function, IEventSource } from '@aws-cdk/aws-lambda'
import * as params from '../params'
import { APIs } from '../params'

export interface ScheduledTaskStackMembers {
  scheduledLambda: Function
}

export class ScheduledTaskStack {
  public constructor(
    private readonly config: BoosterConfig,
    private readonly stack: Stack,
    private readonly apis: APIs
  ) {}

  public build(): ScheduledTaskStackMembers {
    const scheduledLambda = this.buildLambda('scheduled-task', this.config.scheduledTaskHandler)
    this.scheduleLambda(scheduledLambda)

    return { scheduledLambda }
  }

  private buildLambda(name: string, handler: string, eventSources?: Array<IEventSource>): Function {
    return new Function(this.stack, name, {
      ...params.lambda(this.config, this.stack, this.apis),
      functionName: `${this.config.resourceNames.applicationStack}-${name}`,
      handler: handler,
      code: Code.fromAsset(this.config.userProjectRootPath),
      events: eventSources,
    })
  }

  private scheduleLambda(lambda: Function): void {
    Object.keys(this.config.scheduledCommandHandlers).forEach((scheduledCommandName) => {
      const scheduledCommandMetadata = this.config.scheduledCommandHandlers[scheduledCommandName]
      const input: Partial<ScheduledCommandEnvelope> = {
        typeName: scheduledCommandName,
      }
      const cronExpression = this.createCronExpression(scheduledCommandMetadata.scheduledOn)
      const rule = new Rule(this.stack, `${scheduledCommandName}-EventRule`, {
        schedule: cronExpression,
      })
      rule.addTarget(new LambdaFunction(lambda, { event: RuleTargetInput.fromObject(input) }))
    })
  }

  private createCronExpression(scheduledCommandMetadata: ScheduleInterface): Schedule {
    const { minute = '0', hour = '*', day = '*', month = '*', weekDay = '?', year = '*' } = scheduledCommandMetadata
    return Schedule.expression(`cron(${minute} ${hour} ${day} ${month} ${weekDay} ${year})`)
  }
}
