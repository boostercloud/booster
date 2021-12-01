import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import { User } from 'azure-arm-website/lib/models'
import { InfrastructureRocket } from '../../rockets/infrastructure-rocket'
import { FunctionDefinition, Binding } from '../types/functionDefinition'

export interface CoreAzureStackConfig {
  resourceGroupName: string
  storageAccountName: string
  cosmosDbConnectionString: string
  credentials: User
}

export class RocketsStackBuilder {
  public constructor(
    readonly logger: Logger,
    readonly config: BoosterConfig,
    readonly rockets?: InfrastructureRocket[]
  ) {}

  public async build(): Promise<void> {
    const rocketsInfraestructure = this.rockets
      ? this.rockets.map((rocket: InfrastructureRocket) => rocket.mountStack(this.config))
      : []

    await Promise.all(rocketsInfraestructure)
  }

  public getFunctionDefinitions(): FunctionDefinition<Binding>[] {
    this.logger.info('Resolving rockets functions definitions...')
    const functionDefinitions = this.rockets
      ? this.rockets.flatMap((rocket: InfrastructureRocket) => {
          this.logger.info(`Rocket package: ${rocket.packageName!}`)
          return this.resolveFunctionDefinitions(rocket.packageName!, rocket.getFunctionDefinitions(this.config))
        })
      : []
    return functionDefinitions
  }

  private resolveFunctionDefinitions(
    packageName: string,
    functionDefinitions: FunctionDefinition<Binding>[]
  ): FunctionDefinition<Binding>[] {
    return functionDefinitions.map((fd) => {
      fd.config.scriptFile = `../node_modules/${packageName}/${fd.config.scriptFile}`
      return fd
    })
  }
}
