import { BoosterConfig } from '@boostercloud/framework-types'
import ResourceManagementClient from 'azure-arm-resource/lib/resource/resourceManagementClient'
import { User } from 'azure-arm-website/lib/models'
import { InfrastructureRocket } from '../../rockets/infrastructure-rocket'

export interface CoreAzureStackConfig {
  resourceGroupName: string
  storageAccountName: string
  cosmosDbConnectionString: string
  credentials: User
}

export class RocketsStackBuilder {
  public constructor(
    readonly config: BoosterConfig,
    readonly stackCurrentConfig: CoreAzureStackConfig,
    readonly resourceManagementClient: ResourceManagementClient,
    readonly rockets?: InfrastructureRocket[]
  ) {}

  public async build(): Promise<void> {
    const rocketsInfraestructure = this.rockets
      ? this.rockets.map((rocket: InfrastructureRocket) =>
          rocket.mountStack(this.config, this.stackCurrentConfig, this.resourceManagementClient)
        )
      : []

    await Promise.all(rocketsInfraestructure)
  }
}
