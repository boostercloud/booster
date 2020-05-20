import { BoosterConfig } from '@boostercloud/framework-types'
import ResourceManagementClient from 'azure-arm-resource/lib/resource/resourceManagementClient'
import { configuration } from '../params'

export class ApplicationStackBuilder {
  public constructor(readonly config: BoosterConfig) {}

  public buildOn(resourceManagementClient:ResourceManagementClient, resourceGroupName: string): void {

    this.buildStorageAccount(resourceManagementClient, resourceGroupName)
  }

  private async buildStorageAccount(resourceManagementClient:ResourceManagementClient, resourceGroupName: string) {
    const parameters = {
      "storageAccountName": {
        "value": "booststtest"
      },
      "location": {
        "value": configuration.region
      }
    }

    const template = require("../templates/storage-account.json");

    const deploymentParameters = {
      "properties": {
        "parameters": parameters,
        "template": template,
        "mode": "Incremental"
      }
    };

    await resourceManagementClient.deployments.createOrUpdate(resourceGroupName, "testdeploymentbooster", deploymentParameters);
  }
}