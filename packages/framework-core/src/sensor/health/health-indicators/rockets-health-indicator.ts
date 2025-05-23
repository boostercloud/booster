import {
  BOOSTER_HEALTH_INDICATORS_IDS,
  BoosterConfig,
  HealthIndicatorMetadata,
  HealthIndicatorsResult,
  HealthStatus,
} from '@boostercloud/framework-types'

export class RocketsHealthIndicator {
  public async health(
    config: BoosterConfig,
    healthIndicatorMetadata: HealthIndicatorMetadata
  ): Promise<HealthIndicatorsResult> {
    const results = await config.provider.sensor.areRocketFunctionsUp(config)
    if (Object.keys(results).length === 0) {
      return {
        name: 'Rockets',
        id: BOOSTER_HEALTH_INDICATORS_IDS.ROCKETS,
        status: HealthStatus.UNKNOWN,
        details: {
          reason: 'No Rockets found',
        },
      }
    }

    // Check if we're looking for a specific rocket
    const componentPath = healthIndicatorMetadata.healthIndicatorConfiguration.id
    const rocketName = componentPath.split('/')[1] // rockets/<rocket-name>

    if (rocketName) {
      const rocketStatus = results[rocketName]
      if (rocketStatus === undefined) {
        throw new Error(`Rocket "${rocketName}" not found`)
      }
      return {
        name: rocketName,
        id: `${BOOSTER_HEALTH_INDICATORS_IDS.ROCKETS}/${rocketName}`,
        status: rocketStatus ? HealthStatus.UP : HealthStatus.DOWN,
      }
    }

    // return all rockets status
    return {
      name: 'Rockets',
      id: BOOSTER_HEALTH_INDICATORS_IDS.ROCKETS,
      status: this.getOverAllHealthStatus(results),
      components: Object.entries(results).map(([rocketName, status]) => {
        return {
          name: rocketName,
          id: `${BOOSTER_HEALTH_INDICATORS_IDS.ROCKETS}/${rocketName}`,
          status: status ? HealthStatus.UP : HealthStatus.DOWN,
        }
      }),
    }
  }

  private getOverAllHealthStatus(results: { [key: string]: boolean }): HealthStatus {
    const statusValues = Object.values(results)

    if (statusValues.every((status) => status)) {
      return HealthStatus.UP
    }

    if (statusValues.every((status) => !status)) {
      return HealthStatus.DOWN
    }

    return HealthStatus.PARTIALLY_UP
  }
}
