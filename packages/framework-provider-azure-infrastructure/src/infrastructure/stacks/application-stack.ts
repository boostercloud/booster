import { BoosterConfig } from '@boostercloud/framework-types'
import ResourceManagementClient from 'azure-arm-resource/lib/resource/resourceManagementClient'
import { DeploymentExtended } from 'azure-arm-resource/lib/resource/models'

export class ApplicationStackBuilder {
  public constructor(readonly config: BoosterConfig) {}

  public async buildOn(resourceManagementClient: ResourceManagementClient, resourceGroupName: string): Promise<void> {
    const storageAccountDeployment = await this.buildResource(
      resourceManagementClient,
      resourceGroupName,
      {},
      '../templates/storage-account.json'
    )
    // @ts-ignore
    const functionAppDeployment = await this.buildResource(
      resourceManagementClient,
      resourceGroupName,
      {
        storageAccountName: {
          // @ts-ignore
          value: storageAccountDeployment.properties.outputs.storageAccountName.value,
        },
      },
      '../templates/function-app.json'
    )
    // @ts-ignore
    const apiManagementServiceDeployment = await this.buildResource(
      resourceManagementClient,
      resourceGroupName,
      {
        publisherEmail: { value: 'mario@theagilemonkeys.com' },
        publisherName: { value: 'The Agile Monkeys' },
        apiName: { value: this.config.appName + '-rest-api' },
        apiDisplayName: { value: this.config.appName + '-rest-api' },
        apiPath: { value: '/' + this.config.environmentName },
      },
      '../templates/api-management.json'
    )
  }

  /**
   * Deploys an Azure resource to a resource group.
   *
   * @param {ResourceManagementClient} resourceManagementClient A ResourceManagementClient instance
   * @param {string} resourceGroupName The resource group where the resource will be deployed to
   * @param {object} parameters A JSON object with parameters for the ARM template
   * @param {string} templatePath The path of the ARM template JSON file
   *
   * @returns {Promise<DeploymentExtended>}
   */
  private async buildResource(
    resourceManagementClient: ResourceManagementClient,
    resourceGroupName: string,
    parameters: object,
    templatePath: string
  ): Promise<DeploymentExtended> {
    const template = require(templatePath)

    const deploymentParameters = {
      properties: {
        parameters: parameters,
        template: template,
        mode: 'Incremental',
      },
    }

    return resourceManagementClient.deployments.createOrUpdate(
      resourceGroupName,
      'testdeploymentbooster',
      deploymentParameters
    )
  }
}
