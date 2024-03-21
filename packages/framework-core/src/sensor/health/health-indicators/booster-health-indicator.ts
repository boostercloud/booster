import {
  BoosterConfig,
  HealthIndicatorResult,
  HealthIndicatorMetadata,
  HealthStatus,
} from '@boostercloud/framework-types'
import { boosterVersion } from './booster-version'

export class BoosterHealthIndicator {
  public async health(
    config: BoosterConfig,
    healthIndicatorMetadata: HealthIndicatorMetadata
  ): Promise<HealthIndicatorResult> {
    try {
      const result: HealthIndicatorResult = {
        status: await this.isUp(config),
      }
      if (healthIndicatorMetadata.healthIndicatorConfiguration.details) {
        result.details = {
          boosterVersion: boosterVersion(config),
        }
      }
      return result
    } catch (e) {
      return { status: HealthStatus.DOWN, details: e }
    }
  }

  private async isUp(config: BoosterConfig): Promise<HealthStatus> {
    const graphqlUp = await config.provider.sensor.isGraphQLFunctionUp(config)
    const databaseEvents = await config.provider.sensor.isDatabaseEventUp(config)
    const databaseReadModels = await config.provider.sensor.areDatabaseReadModelsUp(config)
    return graphqlUp && databaseEvents && databaseReadModels ? HealthStatus.UP : HealthStatus.DOWN
  }
}
