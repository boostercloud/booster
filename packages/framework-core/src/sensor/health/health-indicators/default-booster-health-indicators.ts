import {
  BOOSTER_HEALTH_INDICATORS_IDS,
  BoosterConfig,
  Class,
  HealthIndicatorInterface,
  HealthIndicatorMetadata,
} from '@boostercloud/framework-types'
import { BoosterHealthIndicator } from './booster-health-indicator'
import { BoosterDatabaseHealthIndicator } from './booster-database-health-indicator'
import { BoosterDatabaseEventsHealthIndicator } from './booster-database-events-health-indicator'
import { BoosterFunctionHealthIndicator } from './booster-function-health-indicator'
import { BoosterDatabaseReadModelsHealthIndicator } from './booster-database-read-models-health-indicator'

function buildMetadata(
  config: BoosterConfig,
  id: BOOSTER_HEALTH_INDICATORS_IDS,
  name: string,
  boosterHealthIndicator: Class<HealthIndicatorInterface>
): HealthIndicatorMetadata {
  const health = config.sensorConfiguration.health
  return {
    class: boosterHealthIndicator,
    healthIndicatorConfiguration: {
      id: id,
      name: name,
      enabled: health.booster[id].enabled,
      details: health.booster[id].details,
      showChildren: health.booster[id].showChildren,
    },
  }
}

/**
 * Booster configured HealthIndicators
 */
export function defaultBoosterHealthIndicators(config: BoosterConfig): Record<string, HealthIndicatorMetadata> {
  const root = buildMetadata(config, BOOSTER_HEALTH_INDICATORS_IDS.ROOT, 'Booster', BoosterHealthIndicator)
  const boosterFunction = buildMetadata(
    config,
    BOOSTER_HEALTH_INDICATORS_IDS.FUNCTION,
    'Booster Function',
    BoosterFunctionHealthIndicator
  )
  const boosterDatabase = buildMetadata(
    config,
    BOOSTER_HEALTH_INDICATORS_IDS.DATABASE,
    'Booster Database',
    BoosterDatabaseHealthIndicator
  )
  const databaseEvents = buildMetadata(
    config,
    BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_EVENTS,
    'Booster Database Events',
    BoosterDatabaseEventsHealthIndicator
  )
  const databaseReadModels = buildMetadata(
    config,
    BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_READ_MODELS,
    'Booster Database ReadModels',
    BoosterDatabaseReadModelsHealthIndicator
  )
  return {
    [root.healthIndicatorConfiguration.id]: root,
    [boosterFunction.healthIndicatorConfiguration.id]: boosterFunction,
    [boosterDatabase.healthIndicatorConfiguration.id]: boosterDatabase,
    [databaseEvents.healthIndicatorConfiguration.id]: databaseEvents,
    [databaseReadModels.healthIndicatorConfiguration.id]: databaseReadModels,
  }
}
