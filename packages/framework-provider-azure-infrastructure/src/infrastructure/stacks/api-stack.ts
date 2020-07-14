import { BoosterConfig } from '@boostercloud/framework-types/dist'
import { buildResource } from '../utils'
import { configuration } from '../params'
import ResourceManagementClient from 'azure-arm-resource/lib/resource/resourceManagementClient'

export class ApiStack {
  public constructor(
    readonly config: BoosterConfig,
    private resourceManagementClient: ResourceManagementClient,
    private resourceGroupName: string,
    private readonly functionAppName: string
  ) {}

  apiManagementTemplatePath = './templates/api-management.json'

  public async build(): Promise<string> {
    const policy = `<policies>
        <inbound>
          <base />
      <set-backend-service base-url="https://${this.functionAppName}.azurewebsites.net/api" />
      </inbound>
      <backend>
      <base />
      </backend>
      <outbound>
      <base />
      </outbound>
      <on-error>
      <base />
      </on-error>
      </policies>`

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
      this.apiManagementTemplatePath
    )

    return apiManagementServiceDeployment.properties?.outputs.apiManagementServiceName.value
  }
}
