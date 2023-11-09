import {
  BoosterConfig,
  HealthAuthorizer,
  HealthEnvelope,
  HealthIndicatorMetadata,
  HealthIndicatorResult,
  HealthIndicatorsResult,
  UserEnvelope,
} from '@boostercloud/framework-types'
import { childrenHealthProviders, isEnabled, metadataFromId, rootHealthProviders } from './health-utils'
import { createInstance } from '@boostercloud/framework-common-helpers'
import { defaultBoosterHealthIndicators } from './health-indicators'
import { BoosterTokenVerifier } from '../../booster-token-verifier'
import { BoosterAuthorizer } from '../../booster-authorizer'

export class BoosterHealthService {
  constructor(readonly config: BoosterConfig) {}

  public async boosterHealth(request: any): Promise<unknown> {
    try {
      const healthEnvelope: HealthEnvelope = this.config.provider.sensor.rawRequestToHealthEnvelope(request)
      await this.validate(healthEnvelope)
      const healthProviders = this.getHealthProviders()
      const parents = this.parentsHealthProviders(healthEnvelope, healthProviders)
      const healthIndicatorResults = await this.boosterHealthProviderResolver(parents, healthProviders)
      return await this.config.provider.api.requestSucceeded(healthIndicatorResults)
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
      const childrens = childrenHealthProviders(current, healthProviders)
      const newResult: HealthIndicatorsResult = {
        ...indicatorResult,
        name: current.healthIndicatorConfiguration.name,
        id: current.healthIndicatorConfiguration.id,
      }
      if (childrens && childrens?.length > 0) {
        newResult.components = await this.boosterHealthProviderResolver(childrens, healthProviders)
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
    return componentPath && componentPath.length > 0
      ? [metadataFromId(healthProviders, componentPath)]
      : rootHealthProviders(healthProviders)
  }

  private async verify(envelope: HealthEnvelope): Promise<UserEnvelope | undefined> {
    const boosterTokenVerifier = new BoosterTokenVerifier(this.config)
    const token = envelope.token
    if (!token) {
      return
    }
    return await boosterTokenVerifier.verify(token)
  }
}
