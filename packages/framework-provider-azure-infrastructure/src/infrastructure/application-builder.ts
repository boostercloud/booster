import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import { InfrastructureRocket } from './rockets/infrastructure-rocket'
import { AzureStack } from './azure-stack'
import * as ckdtfTemplate from './templates/cdktf'
import { createFunctionResourceGroupName, createResourceGroupName, renderToFile } from './helper/utils'
import { Promises } from '@boostercloud/framework-common-helpers/dist'
import { App } from 'cdktf'
import { ZipResource } from './types/zip-resource'
import { FunctionZip } from './helper/function-zip'
import { FunctionDefinition } from './types/functionDefinition'
import { RocketBuilder } from './rockets/rocket-builder'

export interface ApplicationBuild {
  azureStack: AzureStack
  zipResource: ZipResource
  rocketZipResource: ZipResource
}

export class ApplicationBuilder {
  constructor(readonly logger: Logger, readonly config: BoosterConfig, readonly rockets?: InfrastructureRocket[]) {}

  public async buildApplication(): Promise<ApplicationBuild> {
    await this.generateSynthFiles()

    const app = new App()
    const azureStack = await this.synthApplication(app)
    const rocketBuilder = new RocketBuilder(this.logger, this.config, azureStack.applicationStack, this.rockets)
    await rocketBuilder.synthRocket()
    app.synth()

    const featureDefinitions = this.mountFeatureDefinitions(azureStack)
    const rocketFeaturesDefinitions = rocketBuilder.mountRocketFeatureDefinitions()

    const zipResource = await this.generateFunctionsZipFile(featureDefinitions, 'functionApp.zip')
    const rocketZipResource = await this.generateFunctionsZipFile(rocketFeaturesDefinitions, 'rocketApp.zip')

    return {
      azureStack,
      zipResource,
      rocketZipResource,
    }
  }

  public async uploadFile(zipResource: ZipResource): Promise<void> {
    this.logger.info('Uploading zip file')
    const resourceGroupName = createResourceGroupName(this.config.appName, this.config.environmentName)
    const functionAppName = createFunctionResourceGroupName(resourceGroupName)
    await FunctionZip.deployZip(functionAppName, resourceGroupName, zipResource)
    this.logger.info('Zip file uploaded')
  }

  private async synthApplication(app: App): Promise<AzureStack> {
    this.logger.info('Synth...')
    return new AzureStack(app, this.config.appName + this.config.environmentName)
  }

  private mountFeatureDefinitions(azureStack: AzureStack): Array<FunctionDefinition> {
    this.logger.info('Generating Azure functions')
    azureStack.applicationStack.functionDefinitions = FunctionZip.buildAzureFunctions(this.config)
    return azureStack.applicationStack.functionDefinitions
  }

  private async generateSynthFiles(): Promise<void> {
    this.logger.info('Generating cdktf files')
    const filesToGenerate: Array<[Array<string>, string]> = [[['cdktf.json'], ckdtfTemplate.template]]
    await Promises.allSettledAndFulfilled(filesToGenerate.map(renderToFile(this.config)))
  }

  private async generateFunctionsZipFile(
    functionDefinitions: Array<FunctionDefinition>,
    fileName: string
  ): Promise<ZipResource> {
    this.logger.info('Generating zip file')
    return await FunctionZip.copyZip(functionDefinitions, fileName)
  }
}
