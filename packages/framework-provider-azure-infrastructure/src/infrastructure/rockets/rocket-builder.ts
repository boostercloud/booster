import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import { InfrastructureRocket } from './infrastructure-rocket'
import { Promises } from '@boostercloud/framework-common-helpers/dist'
import { FunctionDefinition } from '../types/functionDefinition'
import { ApplicationSynthStack } from '../types/application-synth-stack'
import { buildRocketUtils } from './rocket-utils'

export class RocketBuilder {
  public constructor(
    readonly logger: Logger,
    readonly config: BoosterConfig,
    readonly applicationSynthStack: ApplicationSynthStack,
    readonly rockets?: InfrastructureRocket[]
  ) {}

  public async mount(): Promise<void> {
    const rocketsInfrastructure = this.rockets
      ? this.rockets.map((rocket: InfrastructureRocket) =>
          rocket.mountStack(this.config, this.applicationSynthStack, buildRocketUtils(rocket.packageName ?? ''))
        )
      : []

    await Promises.allSettledAndFulfilled(rocketsInfrastructure)
  }

  public getFunctionDefinitions(): Array<FunctionDefinition> {
    this.logger.info('Resolving rockets functions definitions...')
    if (!this.rockets) {
      return []
    }
    return this.mountRocketFunctions() as Array<FunctionDefinition>
  }

  private mountRocketFunctions(): Array<FunctionDefinition> | undefined {
    return this.rockets?.flatMap((rocket: InfrastructureRocket) => {
      this.logger.info(`Rocket package: ${rocket.packageName}`)
      return rocket.mountFunctions(this.config, this.applicationSynthStack, buildRocketUtils(rocket.packageName ?? ''))
    })
  }
}
