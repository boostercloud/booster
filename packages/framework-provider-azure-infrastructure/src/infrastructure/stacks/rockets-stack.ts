import { BoosterConfig } from '@boostercloud/framework-types'
import { InfrastructureRocket } from '../../rockets/infrastructure-rocket'
import ResourceManagementClient from 'azure-arm-resource/lib/resource/resourceManagementClient'

export class RocketsStack {
  public constructor(
    readonly config: BoosterConfig,
    private resourceManagementClient: ResourceManagementClient,
    private resourceGroupName: string,
    private readonly functionAppName: string,
    private readonly rockets?: InfrastructureRocket[]
  ) {}

  public async build(): Promise<void> {
    const rocketsInfraestructure = this.rockets
      ? this.rockets.map((rocket: InfrastructureRocket) =>
          rocket.mountStack(this.config, this.resourceManagementClient, this.resourceGroupName, this.functionAppName)
        )
      : []

    await Promise.all(rocketsInfraestructure)
  }
}
