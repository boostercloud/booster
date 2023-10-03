import { BoosterConfig, ScheduledCommandEnvelope, ScheduleInterface } from '@boostercloud/framework-types'
import { Stack } from 'aws-cdk-lib'
import { Rule, Schedule, RuleTargetInput } from 'aws-cdk-lib/aws-events'
import { LambdaFunction } from 'aws-cdk-lib/aws-events-targets'
import { Code, Function as AWSFunction, IEventSource } from 'aws-cdk-lib/aws-lambda'
import * as params from '../params'
import { APIs } from '../params'
export interface ScheduledCommandStackMembers {
  scheduledLambda: AWSFunction
}

export class ScheduledCommandStack {
  public constructor(
    private readonly config: BoosterConfig,
    private readonly stack: Stack,
    private readonly apis: APIs
  ) {}

  public build(): ScheduledCommandStackMembers | undefined {
    if (Object.keys(this.config.scheduledCommandHandlers).length) {
      const scheduledLambda = this.buildLambda('scheduled-task', this.config.scheduledTaskHandler)
      this.scheduleLambda(scheduledLambda)
      return { scheduledLambda }
    }
    return undefined
  }

  private buildLambda(name: string, handler: string, eventSources?: Array<IEventSource>): AWSFunction {
    return new AWSFunction(this.stack, name, {
      ...params.lambda(this.config, this.stack, this.apis),
      functionName: `${this.config.resourceNames.applicationStack}-${name}`,
      handler: handler,
      code: Code.fromAsset(this.config.userProjectRootPath),
      events: eventSources,
    })
  }

  private scheduleLambda(lambda: AWSFunction): void {
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
    const { minute = '*', hour = '*', day = '*', month = '*', weekDay = '?', year = '*' } = scheduledCommandMetadata
    const expression = `${minute} ${hour} ${day} ${month} ${weekDay} ${year}`
    const neverRunByDefault = '0 5 31 2 ? *'
    return Schedule.expression(`cron(${expression !== '* * * * ? *' ? expression : neverRunByDefault})`)
  }
}
