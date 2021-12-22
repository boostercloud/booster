import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import { InfrastructureRocket } from './infrastructure-rocket'
import { createResourceGroupName } from '../helper/utils'
import { ZipResource } from '../types/zip-resource'
import { FunctionZip } from '../helper/function-zip'
import { FunctionDefinition } from '../types/functionDefinition'
import { buildRocketUtils } from './rocket-utils'
import { Promises } from '@boostercloud/framework-common-helpers'
import { ApplicationSynthStack } from '../types/application-synth-stack'

export class RocketBuilder {
  constructor(
    readonly logger: Logger,
    readonly config: BoosterConfig,
    readonly applicationSynthStack: ApplicationSynthStack,
    readonly rockets?: InfrastructureRocket[]
  ) {}

  public async synthRocket(): Promise<void> {
    this.logger.info('Synth rockets...')
    const rocketsInfrastructure = this.rockets
      ? this.rockets.map((rocket: InfrastructureRocket) =>
          rocket.mountStack(this.config, this.applicationSynthStack, buildRocketUtils())
        )
      : []

    await Promises.allSettledAndFulfilled(rocketsInfrastructure)
  }

  public mountRocketFeatureDefinitions(): Array<FunctionDefinition> {
    this.logger.info('Generating Rockets functions')
    if (!this.rockets) {
      return []
    }
    return this.mountRocketFunctions() as Array<FunctionDefinition>
  }

  public async uploadRocketsFiles(zipResource: ZipResource): Promise<void> {
    this.logger.info('Uploading rockets zip file')
    const resourceGroupName = createResourceGroupName(this.config.appName, this.config.environmentName)
    this.deployRocketsZips(resourceGroupName, zipResource)
    this.logger.info('Rocket zip files uploaded')
  }

  private mountRocketFunctions(): Array<FunctionDefinition> | undefined {
    return this.rockets?.flatMap((rocket: InfrastructureRocket) => {
      this.logger.info(`Rocket package: ${rocket.packageName}`)
      return rocket.mountFunctions(this.config, this.applicationSynthStack, buildRocketUtils())
    })
  }

  public deployRocketsZips(resourceGroupName: string, zipResource: ZipResource): void {
    this.rockets
      ? this.rockets.map((rocket: InfrastructureRocket) => this.deployRocketZip(rocket, resourceGroupName, zipResource))
      : []
  }

  private deployRocketZip(rocket: InfrastructureRocket, resourceGroupName: string, zipResource: ZipResource): void {
    rocket.getFunctionsAppNames(this.applicationSynthStack).flatMap(async (functionAppName) => {
      this.logger.info(`Deploying Rocket function: ${functionAppName}`)
      await FunctionZip.deployZip(functionAppName, resourceGroupName, zipResource)
    })
  }
}
