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

  public async uploadRocketsFiles(rocketsZipResources: RocketZipResource[] | undefined): Promise<void> {
    if (rocketsZipResources) {
      const resourceGroupName = createResourceGroupName(this.config.appName, this.config.environmentName)
      const rocketZipResourcesPromises = rocketsZipResources.map((rocketZipResource) =>
        this.uploadRocketFile(rocketZipResource, resourceGroupName)
      )
      await Promise.all(rocketZipResourcesPromises)
    }
  }

  private async uploadRocketFile(rocketZipResource: RocketZipResource, resourceGroupName: string): Promise<void> {
    const logger = getLogger(this.config, 'RocketBuilder#uploadRocketFile')
    logger.info('Uploading rockets zip file: ', rocketZipResource.functionAppName, rocketZipResource.zip.fileName)
    await FunctionZip.deployZip(
      this.config,
      rocketZipResource.functionAppName,
      resourceGroupName,
      rocketZipResource.zip
    )
    logger.info('Rocket zip files uploaded', rocketZipResource.functionAppName, rocketZipResource.zip.fileName)
  }

  public async mountRocketsZipResources(): Promise<Array<RocketZipResource> | undefined> {
    if (!this.rockets) {
      return
    }
    const mountRocketZipResourcePromises = this.rockets
      ?.filter((value) => value.mountFunctions)
      .map((rocket: InfrastructureRocket) => this.mountRocketZipResource(rocket))
    return Promise.all(mountRocketZipResourcePromises)
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
