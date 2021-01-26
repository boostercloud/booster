import { BoosterConfig } from '@boostercloud/framework-types'
import { buildResource } from '../utils'
import { configuration } from '../params'
import ResourceManagementClient from 'azure-arm-resource/lib/resource/resourceManagementClient'
import * as Mustache from 'mustache'
import { templates } from '../templates'
import { armTemplates } from '../arm-templates'

export class ApiStack {
  public constructor(
    readonly config: BoosterConfig,
    private resourceManagementClient: ResourceManagementClient,
    private resourceGroupName: string,
    private readonly functionAppName: string
  ) {}

  public async build(): Promise<string> {
    const policy = Mustache.render(templates.policy, { functionAppName: this.functionAppName })

    const apiManagementServiceDeployment = await buildResource(
      this.resourceManagementClient,
      this.resourceGroupName,
      {
        publisherEmail: { value: configuration.publisherEmail },
        publisherName: { value: configuration.publisherName },
        apiName: { value: this.config.appName + '-rest-api' },
        apiDisplayName: { value: this.config.appName + '-rest-api' },
        apiPath: { value: '/' + this.config.environmentName },
        policy: { value: policy },
      },
      armTemplates.apiManagement
    )

    return apiManagementServiceDeployment.properties?.outputs.apiManagementServiceName.value
  }
}
