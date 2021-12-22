import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import { InfrastructureRocket } from './infrastructure-rocket'
import { createResourceGroupName } from '../helper/utils'
import { ZipResource } from '../types/zip-resource'
import { FunctionZip } from '../helper/function-zip'
import { buildRocketUtils } from './rocket-utils'
import { Promises } from '@boostercloud/framework-common-helpers'
import { ApplicationSynthStack } from '../types/application-synth-stack'

export interface RocketZipResource {
  packageName: string
  zip: ZipResource
}

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

  public uploadRocketsFiles(rocketsZipResources: RocketZipResource[] | undefined): void {
    if (this.rockets) {
      this.rockets.map(async (rocket) => {
        const rocketZipResource = rocketsZipResources?.find(
          (rocketZipResource) => rocketZipResource.packageName === rocket.packageName
        )
        if (rocketZipResource) {
          const zip = rocketZipResource?.zip
          await this.uploadRocketFile(zip)
        }
      })
    }
  }

  private async uploadRocketFile(zipResource: ZipResource): Promise<void> {
    this.logger.info('Uploading rockets zip file')
    const resourceGroupName = createResourceGroupName(this.config.appName, this.config.environmentName)
    this.deployRocketsZips(resourceGroupName, zipResource)
    this.logger.info('Rocket zip files uploaded')
  }

  public async mountRocketZipResources(): Promise<Array<RocketZipResource> | undefined> {
    if (!this.rockets) {
      return
    }
    this.logger.info('Generating Rocket zip resources')
    return await Promise.all(
      this.rockets?.map(async (rocket: InfrastructureRocket) => {
        const packageName = rocket.packageName?.replace(/(\W+)/gi, '_')
        const fileName = `rocket_${packageName}.zip`
        this.logger.info(`Generating Rocket ${rocket.packageName} functions`)
        const rocketFeaturesDefinitions = rocket.mountFunctions(
          this.config,
          this.applicationSynthStack,
          buildRocketUtils()
        )
        this.logger.info(`Generating Rocket ${rocket.packageName} zip file ${fileName}`)
        const rocketZipResource = await FunctionZip.copyZip(rocketFeaturesDefinitions, fileName)
        return {
          packageName: rocket.packageName,
          zip: rocketZipResource,
        } as RocketZipResource
      })
    )
  }

  private deployRocketsZips(resourceGroupName: string, zipResource: ZipResource): void {
    this.rockets
      ? this.rockets.map((rocket: InfrastructureRocket) => this.deployRocketZip(rocket, resourceGroupName, zipResource))
      : []
  }

  private deployRocketZip(rocket: InfrastructureRocket, resourceGroupName: string, zipResource: ZipResource): void {
    rocket.getFunctionsAppNames(this.applicationSynthStack).flatMap(async (functionAppName) => {
      await FunctionZip.deployZip(functionAppName, resourceGroupName, zipResource)
    })
  }
}
