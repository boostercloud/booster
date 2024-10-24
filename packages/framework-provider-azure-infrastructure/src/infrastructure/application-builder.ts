import { BoosterConfig } from '@boostercloud/framework-types'
import { InfrastructureRocket } from './rockets/infrastructure-rocket'
import { AzureStack } from './azure-stack'
import * as ckdtfTemplate from './templates/cdktf'
import { renderToFile } from './helper/utils'
import { getLogger, Promises } from '@boostercloud/framework-common-helpers'
import { App } from 'cdktf'
import { ZipResource } from './types/zip-resource'
import { FunctionZip } from './helper/function-zip'
import { RocketBuilder, RocketZipResource } from './rockets/rocket-builder'

export interface ApplicationBuild {
  azureStack: AzureStack
  zipResource: ZipResource
  consumerZipResource?: ZipResource | undefined
  rocketsZipResources?: RocketZipResource[] | undefined
}

export class ApplicationBuilder {
  constructor(readonly config: BoosterConfig, readonly rockets?: InfrastructureRocket[]) {}

  public async buildApplication(): Promise<ApplicationBuild> {
    await this.generateSynthFiles()

    const app = new App()
    let webPubSubBaseFile: string | undefined
    if (this.config.enableSubscriptions) {
      webPubSubBaseFile = await FunctionZip.copyBaseZip(this.config)
    }
    const azureStack = await this.synthApplication(app, webPubSubBaseFile)
    const rocketBuilder = new RocketBuilder(this.config, azureStack.applicationStack, this.rockets)
    await rocketBuilder.synthRocket()
    // add rocket-related env vars to main function app settings
    azureStack.addAppSettingsToFunctionApp(this.rockets)
    app.synth()

    azureStack.applicationStack.functionDefinitions = FunctionZip.buildAzureFunctions(this.config)
    azureStack.applicationStack.consumerFunctionDefinitions = FunctionZip.buildAzureConsumerFunctions(this.config)
    const zipResource = await FunctionZip.copyZip(azureStack.applicationStack.functionDefinitions!, 'functionApp.zip')
    let consumerZipResource: ZipResource | undefined
    if (this.config.eventStreamConfiguration.enabled) {
      consumerZipResource = await FunctionZip.copyZip(
        azureStack.applicationStack.consumerFunctionDefinitions!,
        'consumerFunctionApp.zip'
      )
    }
    const rocketsZipResources = await rocketBuilder.mountRocketsZipResources()

    return {
      azureStack,
      zipResource,
      consumerZipResource,
      rocketsZipResources,
    }
  }

  private async synthApplication(app: App, destinationFile?: string): Promise<AzureStack> {
    const logger = getLogger(this.config, 'ApplicationBuilder#synthApplication')
    logger.info('Synth...')
    return new AzureStack(app, this.config.appName + this.config.environmentName, destinationFile)
  }

  private async generateSynthFiles(): Promise<void> {
    const logger = getLogger(this.config, 'ApplicationBuilder#generateSynthFiles')
    logger.info('Generating cdktf files')
    const filesToGenerate: Array<[Array<string>, string]> = [[['cdktf.json'], ckdtfTemplate.template]]
    await Promises.allSettledAndFulfilled(filesToGenerate.map(renderToFile(this.config)))
  }
}
