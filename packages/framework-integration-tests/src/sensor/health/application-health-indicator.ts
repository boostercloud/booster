import {
  BoosterConfig,
  HealthIndicatorResult,
  HealthIndicatorMetadata,
  HealthStatus,
} from '@boostercloud/framework-types'
import { HealthSensor } from '@boostercloud/framework-core'

@HealthSensor({
  id: 'myApplication',
  name: 'my-application',
  enabled: true,
  details: true,
  showChildren: true,
})
export class ApplicationHealthIndicator {
  public async health(
    config: BoosterConfig,
    healthIndicatorMetadata: HealthIndicatorMetadata
  ): Promise<HealthIndicatorResult> {
    return {
      status: HealthStatus.UP,
    } as HealthIndicatorResult
  }
}
