import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import { InfrastructureRocket } from './rockets/infrastructure-rocket'
import { AzureStack } from './azure-stack'
import * as ckdtfTemplate from './templates/cdktf'
import { createFunctionResourceGroupName, createResourceGroupName, renderToFile } from './helper/utils'
import { Promises } from '@boostercloud/framework-common-helpers/dist'
import { App } from 'cdktf'
import { RocketBuilder } from './rockets/rocket-builder'
import { ZipResource } from './types/zip-resource'
import { FunctionZip } from './helper/function-zip'
import { FunctionDefinition } from './types/functionDefinition'

export class ApplicationBuilder {
  constructor(readonly logger: Logger, readonly config: BoosterConfig, readonly rockets?: InfrastructureRocket[]) {}

  public async buildApplication(): Promise<ZipResource> {
    const azureStack = await this.synthApplication()
    const featureDefinitions = this.mountFeatureDefinitions(azureStack)
    return await this.generateFunctionsZipFile(featureDefinitions)
  }

  public async synthApplication(): Promise<AzureStack> {
    this.logger.info('Synth...')

    await this.generateSynthFiles()
    const app = new App()
    const azureStack = new AzureStack(app, this.config.appName + this.config.environmentName)
    const rocketBuilder = new RocketBuilder(this.logger, this.config, azureStack.applicationStack, this.rockets)
    await rocketBuilder.mount()
    app.synth()

    return azureStack
  }

  public mountFeatureDefinitions(azureStack: AzureStack): Array<FunctionDefinition> {
    this.logger.info('Generating Azure functions')
    azureStack.applicationStack.functionDefinitions = FunctionZip.buildAzureFunctions(this.config)
    const rocketBuilder = new RocketBuilder(this.logger, this.config, azureStack.applicationStack, this.rockets)
    return rocketBuilder.getFunctionDefinitions()
  }

  public async uploadFile(zipResource: ZipResource): Promise<void> {
    this.logger.info('Uploading zip file')
    const resourceGroupName = createResourceGroupName(this.config.appName, this.config.environmentName)
    const functionAppName = createFunctionResourceGroupName(resourceGroupName)
    await FunctionZip.deployZip(functionAppName, resourceGroupName, zipResource)
    this.logger.info('Zip file uploaded')
  }

  private async generateFunctionsZipFile(functionDefinitions: Array<FunctionDefinition>): Promise<ZipResource> {
    this.logger.info('Generating zip file')
    return await FunctionZip.copyZip(functionDefinitions)
  }

  private async generateSynthFiles(): Promise<void> {
    this.logger.info('Generating cdktf files')
    const filesToGenerate: Array<[Array<string>, string]> = [[['cdktf.json'], ckdtfTemplate.template]]
    await Promises.allSettledAndFulfilled(filesToGenerate.map(renderToFile(this.config)))
  }
}
