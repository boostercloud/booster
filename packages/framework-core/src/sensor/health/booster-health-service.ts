import {
  BOOSTER_HEALTH_INDICATORS_IDS,
  BoosterConfig,
  HealthAuthorizer,
  HealthEnvelope,
  HealthIndicatorMetadata,
  HealthIndicatorResult,
  HealthIndicatorsResult,
  HealthStatus,
  UserEnvelope,
} from '@boostercloud/framework-types'
import { childHealthProviders, isEnabled, metadataFromId, rootHealthProviders } from './health-utils'
import { createInstance } from '@boostercloud/framework-common-helpers'
import { defaultBoosterHealthIndicators } from './health-indicators'
import { BoosterTokenVerifier } from '../../booster-token-verifier'
import { BoosterAuthorizer } from '../../booster-authorizer'

/**
 * This class is in charge of handling the health check requests
 * and dispatching the health checks to the corresponding health indicators
 */
export class BoosterHealthService {
  constructor(readonly config: BoosterConfig) {}

  public async boosterHealth(request: unknown): Promise<unknown> {
    try {
      const healthEnvelope: HealthEnvelope = this.config.provider.sensor.rawRequestToHealthEnvelope(request)
      await this.validate(healthEnvelope)
      const healthProviders = this.getHealthProviders()
      const parents = this.parentsHealthProviders(healthEnvelope, healthProviders)
      const healthIndicatorResults = await this.boosterHealthProviderResolver(parents, healthProviders)

      // Check if all components are healthy (considering UNKNOWN rockets as healthy)
      const isHealthy = this.isOverallHealthy(healthIndicatorResults)

      // Use the new health specific response handler
      return await this.config.provider.api.healthRequestResult(healthIndicatorResults, isHealthy)
    } catch (e) {
      return await this.config.provider.api.requestFailed(e)
    }
  }

  private async validate(healthEnvelope: HealthEnvelope): Promise<void> {
    const userEnvelope = await this.verify(healthEnvelope)
    const authorizer = BoosterAuthorizer.build(
      this.config.sensorConfiguration.health.globalAuthorizer
    ) as HealthAuthorizer
    await authorizer(userEnvelope, healthEnvelope)
  }

  private async boosterHealthProviderResolver(
    healthIndicatorsMetadata: Array<HealthIndicatorMetadata>,
    healthProviders: Record<string, HealthIndicatorMetadata>
  ): Promise<Array<HealthIndicatorsResult>> {
    const result: Array<HealthIndicatorsResult> = []
    for (const current of healthIndicatorsMetadata) {
      const indicatorResult = await this.enabledIndicatorHealth(current, healthProviders)
      if (!indicatorResult) {
        continue
      }
      const children = childHealthProviders(current, healthProviders)

      // Check if the result is already a HealthIndicatorsResult (has name and id)
      const isHealthIndicatorsResult = 'name' in indicatorResult && 'id' in indicatorResult

      const newResult: HealthIndicatorsResult = {
        ...indicatorResult,
        // Only use the configuration name if we don't already have a name (for individual rocket checks)
        name: isHealthIndicatorsResult
          ? (indicatorResult as HealthIndicatorsResult).name
          : current.healthIndicatorConfiguration.name,
        id: current.healthIndicatorConfiguration.id,
      }
      if (children && children?.length > 0) {
        newResult.components = await this.boosterHealthProviderResolver(children, healthProviders)
      }
      result.push(newResult)
    }
    return result
  }

  private async enabledIndicatorHealth(
    current: HealthIndicatorMetadata,
    healthProviders: Record<string, HealthIndicatorMetadata>
  ): Promise<HealthIndicatorResult | undefined> {
    if (isEnabled(current, healthProviders)) {
      return await this.indicatorHealth(current)
    }
    return
  }

  private async indicatorHealth(metadata: HealthIndicatorMetadata): Promise<HealthIndicatorResult> {
    const rootClass = metadata.class
    const instance = createInstance(rootClass, {})
    const healthIndicatorResult = await instance.health(this.config, metadata)
    if (!metadata.healthIndicatorConfiguration.details) {
      healthIndicatorResult.details = undefined
    }
    return healthIndicatorResult
  }

  /**
   * If there is not any indicator configured, then we will use only the Booster indicators.
   * @private
   */
  private getHealthProviders(): Record<string, HealthIndicatorMetadata> {
    return Object.keys(this.config.userHealthIndicators).length !== 0
      ? this.config.userHealthIndicators
      : defaultBoosterHealthIndicators(this.config)
  }

  private parentsHealthProviders(
    envelope: HealthEnvelope,
    healthProviders: Record<string, HealthIndicatorMetadata>
  ): Array<HealthIndicatorMetadata> {
    const componentPath = envelope.componentPath
    if (!componentPath || componentPath.length === 0) {
      return rootHealthProviders(healthProviders)
    }

    // Special handling for rockets - always use the root rockets provider
    if (componentPath.startsWith('rockets/')) {
      const rocketsProvider = healthProviders[BOOSTER_HEALTH_INDICATORS_IDS.ROCKETS]
      if (!rocketsProvider) {
        throw new Error('Rockets health provider not found')
      }
      // Pass the full path in the configuration so RocketsHealthIndicator can handle it
      return [
        {
          ...rocketsProvider,
          healthIndicatorConfiguration: {
            ...rocketsProvider.healthIndicatorConfiguration,
            id: componentPath,
          },
        },
      ]
    }

    // Normal handling for other health providers
    return [metadataFromId(healthProviders, componentPath)]
  }

  private async verify(envelope: HealthEnvelope): Promise<UserEnvelope | undefined> {
    const boosterTokenVerifier = new BoosterTokenVerifier(this.config)
    const token = envelope.token
    if (!token) {
      return
    }
    return await boosterTokenVerifier.verify(token)
  }

  private isOverallHealthy(results: Array<HealthIndicatorsResult>): boolean {
    for (const result of results) {
      // Special case: UNKNOWN status for rockets is considered healthy
      if (result.id === BOOSTER_HEALTH_INDICATORS_IDS.ROCKETS && result.status === HealthStatus.UNKNOWN) {
        continue
      }

      // Check current component's status
      if (result.status !== HealthStatus.UP) {
        return false
      }

      // Recursively check child components if they exist
      if (result.components && result.components.length > 0) {
        if (!this.isOverallHealthy(result.components)) {
          return false
        }
      }
    }
    return true
  }
}
