/* eslint-disable @typescript-eslint/ban-types */
import {
  Class,
  HealthIndicatorConfiguration,
  HealthIndicatorInterface,
  HealthIndicatorMetadata,
} from '@boostercloud/framework-types'
import { Booster } from '../booster'
import { defaultBoosterHealthIndicators } from '../sensor/health/health-indicators'

/**
 *
 * @param {Object} attributes
 * @param {string} attributes.id - Unique indicator identifier
 * @param {string} attributes.name - Indicator description
 * @param {boolean} attributes.enabled - If false, this indicator and the components of this indicator will be skipped
 * @param {boolean} attributes.details - If false, the indicator will not include the details
 * @param {boolean} [attributes.showChildren] - If false, this indicator will not include children components in the tree.
 *      Children components will be shown through children urls
 * @constructor
 */
export function HealthSensor(
  attributes: HealthIndicatorConfiguration
): <TIndicator extends HealthIndicatorInterface>(healthIndicator: Class<TIndicator>) => void {
  return (healthIndicator) => {
    Booster.configureCurrentEnv((config): void => {
      if (Object.keys(config.userHealthIndicators).length === 0) {
        config.userHealthIndicators = defaultBoosterHealthIndicators(config)
      }
      const path = attributes.id
      config.userHealthIndicators[path] = {
        class: healthIndicator,
        healthIndicatorConfiguration: {
          id: attributes.id,
          name: attributes.name,
          enabled: attributes.enabled,
          details: attributes.details,
          showChildren: attributes.showChildren ?? true,
        },
      } as HealthIndicatorMetadata
    })
  }
}
