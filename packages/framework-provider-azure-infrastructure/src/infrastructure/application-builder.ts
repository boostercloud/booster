import { BoosterConfig } from '@boostercloud/framework-types'
import { InfrastructureRocket } from './rockets/infrastructure-rocket'
import { AzureStack } from './azure-stack'
import * as ckdtfTemplate from './templates/cdktf'
import { createFunctionResourceGroupName, createResourceGroupName, renderToFile } from './helper/utils'
import { getLogger, Promises } from '@boostercloud/framework-common-helpers'
import { App } from 'cdktf'
import { ZipResource } from './types/zip-resource'
import { FunctionZip } from './helper/function-zip'
import { FunctionDefinition } from './types/functionDefinition'
import { RocketBuilder, RocketZipResource } from './rockets/rocket-builder'

export interface ApplicationBuild {
  azureStack: AzureStack
  zipResource: ZipResource
  rocketsZipResources?: RocketZipResource[] | undefined
}

export class ApplicationBuilder {
  constructor(readonly config: BoosterConfig, readonly rockets?: InfrastructureRocket[]) {}

  public async buildApplication(): Promise<ApplicationBuild> {
    await this.generateSynthFiles()

    const app = new App()
    const azureStack = await this.synthApplication(app)
    const rocketBuilder = new RocketBuilder(this.config, azureStack.applicationStack, this.rockets)
    await rocketBuilder.synthRocket()
    app.synth()

    const featureDefinitions = this.mountFeatureDefinitions(azureStack)
    const zipResource = await FunctionZip.copyZip(featureDefinitions, 'functionApp.zip')

    const rocketsZipResources = await rocketBuilder.mountRocketsZipResources()

    return {
      azureStack,
      zipResource,
      rocketsZipResources,
    }
  }

  public async uploadFile(zipResource: ZipResource): Promise<void> {
    const logger = getLogger(this.config, 'ApplicationBuilder#uploadFile')
    logger.info('Uploading zip file')
    const resourceGroupName = createResourceGroupName(this.config.appName, this.config.environmentName)
    const functionAppName = createFunctionResourceGroupName(resourceGroupName)
    await FunctionZip.deployZip(functionAppName, resourceGroupName, zipResource)
    logger.info('Zip file uploaded')
  }

  private async synthApplication(app: App): Promise<AzureStack> {
    const logger = getLogger(this.config, 'ApplicationBuilder#synthApplication')
    logger.info('Synth...')
    return new AzureStack(app, this.config.appName + this.config.environmentName)
  }

  private mountFeatureDefinitions(azureStack: AzureStack): Array<FunctionDefinition> {
    const logger = getLogger(this.config, 'ApplicationBuilder#mountFeatureDefinitions')
    logger.info('Generating Azure functions')
    azureStack.applicationStack.functionDefinitions = FunctionZip.buildAzureFunctions(this.config)
    return azureStack.applicationStack.functionDefinitions
  }

  private async generateSynthFiles(): Promise<void> {
    const logger = getLogger(this.config, 'ApplicationBuilder#generateSynthFiles')
    logger.info('Generating cdktf files')
    const filesToGenerate: Array<[Array<string>, string]> = [[['cdktf.json'], ckdtfTemplate.template]]
    await Promises.allSettledAndFulfilled(filesToGenerate.map(renderToFile(this.config)))
  }
}
