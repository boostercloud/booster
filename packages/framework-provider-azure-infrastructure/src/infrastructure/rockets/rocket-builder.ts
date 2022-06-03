import { BoosterConfig } from '@boostercloud/framework-types'
import { InfrastructureRocket } from './infrastructure-rocket'
import { createResourceGroupName } from '../helper/utils'
import { ZipResource } from '../types/zip-resource'
import { FunctionZip } from '../helper/function-zip'
import { buildRocketUtils } from './rocket-utils'
import { getLogger, Promises } from '@boostercloud/framework-common-helpers'
import { ApplicationSynthStack } from '../types/application-synth-stack'
import { FunctionDefinition } from '../types/functionDefinition'

export interface RocketZipResource {
  functionAppName: string
  zip: ZipResource
}

export class RocketBuilder {
  constructor(
    readonly config: BoosterConfig,
    readonly applicationSynthStack: ApplicationSynthStack,
    readonly rockets?: InfrastructureRocket[]
  ) {}

  public async synthRocket(): Promise<void> {
    const logger = getLogger(this.config, 'RocketBuilder#synthRocket')
    logger.info('Synth rockets...')
    const rocketsInfrastructure = this.rockets
      ? this.rockets.map((rocket: InfrastructureRocket) =>
          rocket.mountStack(this.config, this.applicationSynthStack, buildRocketUtils())
        )
      : []

    await Promises.allSettledAndFulfilled(rocketsInfrastructure)
  }

  public uploadRocketsFiles(rocketsZipResources: RocketZipResource[] | undefined): void {
    if (this.rockets) {
      this.rockets.map(async (rocket) => {
        const rocketZipResource = rocketsZipResources?.find(
          (rocketZipResource) => rocketZipResource.functionAppName === this.getFunctionAppName(rocket)
        )
        if (rocketZipResource) {
          const zip = rocketZipResource?.zip
          await this.uploadRocketFile(zip)
        }
      })
    }
  }

  private async uploadRocketFile(zipResource: ZipResource): Promise<void> {
    const logger = getLogger(this.config, 'RocketBuilder#uploadRocketFile')
    logger.info('Uploading rockets zip file')
    const resourceGroupName = createResourceGroupName(this.config.appName, this.config.environmentName)
    this.deployRocketsZips(resourceGroupName, zipResource)
    logger.info('Rocket zip files uploaded')
  }

  public async mountRocketsZipResources(): Promise<Array<RocketZipResource> | undefined> {
    if (!this.rockets) {
      return
    }
    return await Promise.all(
      this.rockets
        ?.filter((value) => value.mountFunctions)
        .map(async (rocket: InfrastructureRocket) => await this.mountRocketZipResource(rocket))
    )
  }

  private async mountRocketZipResource(rocket: InfrastructureRocket): Promise<RocketZipResource> {
    const logger = getLogger(this.config, 'RocketBuilder#mountRocketZipResource')
    const rocketFunctionAppName = this.getFunctionAppName(rocket)
    const functionAppName = rocketFunctionAppName.replace(/(\W+)/gi, '_')
    const fileName = `rocket_${functionAppName}.zip`
    const rocketFeaturesDefinitions = this.mountFunction(rocket)
    logger.info(`Generating Rocket zip file ${fileName}`)
    const rocketZipResource = await FunctionZip.copyZip(rocketFeaturesDefinitions, fileName)
    return {
      functionAppName: rocketFunctionAppName,
      zip: rocketZipResource,
    } as RocketZipResource
  }

  private deployRocketsZips(resourceGroupName: string, zipResource: ZipResource): void {
    this.rockets
      ? this.rockets.map((rocket: InfrastructureRocket) => this.deployRocketZip(rocket, resourceGroupName, zipResource))
      : []
  }

  private async deployRocketZip(
    rocket: InfrastructureRocket,
    resourceGroupName: string,
    zipResource: ZipResource
  ): Promise<void> {
    const functionAppName = this.getFunctionAppName(rocket)
    await FunctionZip.deployZip(functionAppName, resourceGroupName, zipResource)
  }

  private mountFunction(rocket: InfrastructureRocket): Array<FunctionDefinition> {
    if (!rocket.mountFunctions) {
      throw new Error(`MountFunctions method not found on rocket ${rocket}`)
    }
    return rocket.mountFunctions(this.config, this.applicationSynthStack, buildRocketUtils())
  }

  private getFunctionAppName(rocket: InfrastructureRocket): string {
    if (!rocket.getFunctionAppName) {
      throw new Error(`FunctionAppName not found on rocket ${rocket}`)
    }
    return rocket.getFunctionAppName(this.applicationSynthStack)
  }
}
