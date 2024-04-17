import {
  BoosterConfig,
  HealthIndicatorResult,
  HealthIndicatorMetadata,
  HealthStatus,
} from '@boostercloud/framework-types'

export class BoosterDatabaseHealthIndicator {
  public async health(
    config: BoosterConfig,
    healthIndicatorMetadata: HealthIndicatorMetadata
  ): Promise<HealthIndicatorResult> {
    try {
      const result: HealthIndicatorResult = {
        status: await this.isUp(config),
      }
      if (healthIndicatorMetadata.healthIndicatorConfiguration.details) {
        const details = {
          urls: await config.provider.sensor.databaseUrls(config),
        }
        result.details = details as any
      }
      return result
    } catch (e) {
      return { status: HealthStatus.DOWN, details: e }
    }
  }

  private async isUp(config: BoosterConfig): Promise<HealthStatus> {
    const databaseEvents = await config.provider.sensor.isDatabaseEventUp(config)
    const databaseReadModels = await config.provider.sensor.areDatabaseReadModelsUp(config)
    return databaseEvents && databaseReadModels ? HealthStatus.UP : HealthStatus.DOWN
  }
}
