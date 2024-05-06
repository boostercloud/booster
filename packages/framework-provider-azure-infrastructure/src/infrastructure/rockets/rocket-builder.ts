import { BoosterConfig } from '@boostercloud/framework-types'
import { InfrastructureRocket } from './infrastructure-rocket'
import { createResourceGroupName } from '../helper/utils'
import { ZipResource } from '../types/zip-resource'
import { FunctionZip } from '../helper/function-zip'
import { buildRocketUtils } from './rocket-utils'
import { getLogger, Promises } from '@boostercloud/framework-common-helpers'
import { ApplicationSynthStack } from '../types/application-synth-stack'
import { FunctionAppFunctionsDefinitions, FunctionDefinition } from '../types/functionDefinition'

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
    const mountRocketZipResourcePromises: Array<Promise<Array<RocketZipResource>>> = []
    for (const rocket of this.rockets) {
      if (rocket.mountCode) {
        mountRocketZipResourcePromises.push(this.mountRocketCode(rocket))
      } else {
        if (rocket.mountFunctions) {
          mountRocketZipResourcePromises.push(this.mountRocketZipResource(rocket))
        }
      }
    }
    const resources = await Promise.all(mountRocketZipResourcePromises)
    return resources.flat()
  }

  /**
   * @deprecated use mountRocketCode
   * @param rocket
   * @private
   */
  private async mountRocketZipResource(rocket: InfrastructureRocket): Promise<Array<RocketZipResource>> {
    const logger = getLogger(this.config, 'RocketBuilder#mountRocketZipResource')
    const rocketFunctionAppName = this.getFunctionAppName(rocket)
    const functionAppName = rocketFunctionAppName.replace(/(\W+)/gi, '_')
    const fileName = `rocket_${functionAppName}.zip`
    const rocketFeaturesDefinitions = this.mountFunction(rocket)
    logger.info(`Generating Rocket zip file ${fileName}`)
    const rocketZipResource = await FunctionZip.copyZip(rocketFeaturesDefinitions, fileName)
    return [
      {
        functionAppName: rocketFunctionAppName,
        zip: rocketZipResource,
      } as RocketZipResource,
    ]
  }

  private async mountRocketCode(rocket: InfrastructureRocket): Promise<Array<RocketZipResource>> {
    const logger = getLogger(this.config, 'RocketBuilder#mountRocketCode')
    const rocketFeaturesDefinitions = await this.mountCodeFunction(rocket)
    const result: Array<RocketZipResource> = []
    for (const functionDefinition of rocketFeaturesDefinitions) {
      const functionAppName = functionDefinition.functionAppName.replace(/(\W+)/gi, '_')
      const fileName = `rocket_${functionAppName}.zip`
      logger.info(`Generating Rocket zip file ${fileName} for function ${functionAppName}`)
      const rocketZipResource = await FunctionZip.copyZip(
        functionDefinition.functionsDefinitions,
        fileName,
        functionDefinition.hostJsonPath
      )
      result.push({
        functionAppName: functionAppName,
        zip: rocketZipResource,
      })
    }

    return result
  }

  /**
   * @deprecated use mountCodeFunction
   * @param rocket
   * @private
   */
  private mountFunction(rocket: InfrastructureRocket): Array<FunctionDefinition> {
    if (!rocket.mountFunctions) {
      throw new Error(`mountFunctions method not found on rocket ${rocket}`)
    }
    return rocket.mountFunctions(this.config, this.applicationSynthStack, buildRocketUtils())
  }

  private async mountCodeFunction(rocket: InfrastructureRocket): Promise<FunctionAppFunctionsDefinitions> {
    if (!rocket.mountCode) {
      throw new Error(`mountCode method not found on rocket ${rocket}`)
    }
    return rocket.mountCode(this.config, this.applicationSynthStack, buildRocketUtils())
  }

  /**
   * @deprecated
   * @param rocket
   * @private
   */
  private getFunctionAppName(rocket: InfrastructureRocket): string {
    if (!rocket.getFunctionAppName) {
      throw new Error(`FunctionAppName not found on rocket ${rocket}`)
    }
    return rocket.getFunctionAppName(this.applicationSynthStack)
  }
}
