import {
  BoosterConfig,
  HealthIndicatorMetadata,
  HealthIndicatorResult,
  HealthStatus,
} from '@boostercloud/framework-types'
import { osInfo } from './os-info'

export class BoosterFunctionHealthIndicator {
  public async health(
    config: BoosterConfig,
    healthIndicatorMetadata: HealthIndicatorMetadata
  ): Promise<HealthIndicatorResult> {
    try {
      const result: HealthIndicatorResult = {
        status: await this.isUp(config),
      }
      if (healthIndicatorMetadata.healthIndicatorConfiguration.details) {
        const graphQLUrl = await config.provider.sensor.graphQLFunctionUrl(config)
        const osInfoResult = await osInfo()
        result.details = {
          ...osInfoResult,
          graphQL_url: graphQLUrl,
        }
      }
      return result
    } catch (e) {
      return { status: HealthStatus.DOWN, details: e }
    }
  }

  private async isUp(config: BoosterConfig): Promise<HealthStatus> {
    return (await config.provider.sensor.isGraphQLFunctionUp(config)) ? HealthStatus.UP : HealthStatus.DOWN
  }
}
