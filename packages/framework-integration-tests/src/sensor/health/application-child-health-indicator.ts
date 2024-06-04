import { HealthSensor } from '@boostercloud/framework-core'
import {
  BoosterConfig,
  HealthIndicatorResult,
  HealthIndicatorMetadata,
  HealthStatus,
} from '@boostercloud/framework-types'

@HealthSensor({
  id: 'myApplication/child',
  name: 'My Application child',
  enabled: true,
  details: true,
  showChildren: true,
})
export class ApplicationChildHealthIndicator {
  public async health(
    config: BoosterConfig,
    healthIndicatorMetadata: HealthIndicatorMetadata
  ): Promise<HealthIndicatorResult> {
    return {
      status: HealthStatus.OUT_OF_SERVICE,
    } as HealthIndicatorResult
  }
}
