import { HealthSensor } from '@boostercloud/framework-core'
import {
  BoosterConfig,
  HealthIndicatorResult,
  HealthIndicatorMetadata,
  HealthStatus,
  BOOSTER_HEALTH_INDICATORS_IDS,
} from '@boostercloud/framework-types'

@HealthSensor({
  id: `${BOOSTER_HEALTH_INDICATORS_IDS.DATABASE}/myApplication2`,
  name: 'A second indicator added to the Booster Database indicator through My Application',
  enabled: true,
  details: true,
  showChildren: true,
})
export class ApplicationAdd2BoosterDatabaseChildrenHealthIndicator {
  public async health(
    config: BoosterConfig,
    healthIndicatorMetadata: HealthIndicatorMetadata
  ): Promise<HealthIndicatorResult> {
    return {
      status: HealthStatus.UNKNOWN,
    } as HealthIndicatorResult
  }
}
