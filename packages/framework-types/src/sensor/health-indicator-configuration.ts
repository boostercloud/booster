import { HealthRoleAccess } from '../concepts'
import { BoosterConfig } from '../config'
import { Class } from '../typelevel'

export enum HealthStatus {
  UP = 'UP', // The component or subsystem is working as expected
  DOWN = 'DOWN', // The component is not working
  OUT_OF_SERVICE = 'OUT_OF_SERVICE', // The component is out of service temporarily
  UNKNOWN = 'UNKNOWN', // The component state is unknown
}

export interface HealthIndicatorResult {
  status: HealthStatus
  details?: {
    [key: string]: unknown
  }
}

export interface HealthIndicatorsResult extends HealthIndicatorResult {
  name: string
  id: string
  components?: Array<HealthIndicatorsResult>
}

export enum BOOSTER_HEALTH_INDICATORS_IDS {
  ROOT = 'booster',
  FUNCTION = 'booster/function',
  DATABASE = 'booster/database',
  DATABASE_EVENTS = 'booster/database/events',
  DATABASE_READ_MODELS = 'booster/database/readmodels',
}

export const DEFAULT_HEALTH_CONFIGURATION_BOOSTER: SensorBoosterHealthConfigurationDetails = {
  enabled: false,
  details: true,
  showChildren: true,
}

export const DEFAULT_SENSOR_HEALTH_BOOSTER_CONFIGURATIONS: Record<
  BOOSTER_HEALTH_INDICATORS_IDS,
  SensorBoosterHealthConfigurationDetails
> = {
  [BOOSTER_HEALTH_INDICATORS_IDS.ROOT]: { ...DEFAULT_HEALTH_CONFIGURATION_BOOSTER },
  [BOOSTER_HEALTH_INDICATORS_IDS.FUNCTION]: { ...DEFAULT_HEALTH_CONFIGURATION_BOOSTER },
  [BOOSTER_HEALTH_INDICATORS_IDS.DATABASE]: { ...DEFAULT_HEALTH_CONFIGURATION_BOOSTER },
  [BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_EVENTS]: { ...DEFAULT_HEALTH_CONFIGURATION_BOOSTER },
  [BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_READ_MODELS]: { ...DEFAULT_HEALTH_CONFIGURATION_BOOSTER },
}

export type SensorBoosterHealthConfigurationDetails = HealthIndicatorConfigurationBase

export interface SensorBoosterHealthConfiguration {
  globalAuthorizer: HealthRoleAccess
  booster: {
    [BOOSTER_HEALTH_INDICATORS_IDS.ROOT]: SensorBoosterHealthConfigurationDetails
    [BOOSTER_HEALTH_INDICATORS_IDS.FUNCTION]: SensorBoosterHealthConfigurationDetails
    [BOOSTER_HEALTH_INDICATORS_IDS.DATABASE]: SensorBoosterHealthConfigurationDetails
    [BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_EVENTS]: SensorBoosterHealthConfigurationDetails
    [BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_READ_MODELS]: SensorBoosterHealthConfigurationDetails
  }
}

export interface SensorConfiguration {
  health: SensorBoosterHealthConfiguration
}

export interface HealthIndicatorInterface {
  health: (config: BoosterConfig, healthIndicatorMetadata: HealthIndicatorMetadata) => Promise<HealthIndicatorResult>
}

export interface HealthIndicatorConfigurationBase {
  enabled: boolean
  details: boolean
  showChildren?: boolean
}

export interface HealthIndicatorConfiguration extends HealthIndicatorConfigurationBase {
  id: string
  name: string
}

export interface HealthIndicatorMetadata {
  readonly class: Class<HealthIndicatorInterface>
  readonly healthIndicatorConfiguration: HealthIndicatorConfiguration
}
