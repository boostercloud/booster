import {
  BOOSTER_HEALTH_INDICATORS_IDS,
  BoosterConfig,
  HealthIndicatorMetadata,
  HealthStatus,
  ProviderLibrary,
} from '@boostercloud/framework-types'
import { fake } from 'sinon'
import { RocketsHealthIndicator } from '../../../src/sensor/health/health-indicators/rockets-health-indicator'
import { expect } from '../../expect'

describe('RocketsHealthIndicator', () => {
  let config: BoosterConfig
  let healthIndicatorMetadata: HealthIndicatorMetadata

  beforeEach(() => {
    config = new BoosterConfig('test')
    healthIndicatorMetadata = {
      class: RocketsHealthIndicator,
      healthIndicatorConfiguration: {
        id: BOOSTER_HEALTH_INDICATORS_IDS.ROCKETS,
        name: 'Rockets',
        enabled: true,
        details: true,
        showChildren: true,
      },
    }
    config.provider = {
      sensor: {
        areRocketFunctionsUp: fake(),
      },
    } as unknown as ProviderLibrary
  })

  describe('when no rockets are found', () => {
    beforeEach(() => {
      config.provider.sensor.areRocketFunctionsUp = fake.returns({})
    })

    it('returns UNKNOWN status with reason', async () => {
      const indicator = new RocketsHealthIndicator()
      const result = await indicator.health(config, healthIndicatorMetadata)

      expect(result).to.deep.equal({
        name: 'Rockets',
        id: BOOSTER_HEALTH_INDICATORS_IDS.ROCKETS,
        status: 'UNKNOWN',
        details: {
          reason: 'No Rockets found',
        },
      })
    })
  })

  describe('when checking all rockets', () => {
    it('returns UP status when all rockets are up', async () => {
      config.provider.sensor.areRocketFunctionsUp = fake.returns({
        'rocket1-func': true,
        'rocket2-func': true,
      })

      const indicator = new RocketsHealthIndicator()
      const result = await indicator.health(config, healthIndicatorMetadata)

      expect(result.status).to.equal(HealthStatus.UP)
      expect(result.components).to.have.lengthOf(2)
      const components = result.components as Array<typeof result>
      expect(components[0].status).to.equal(HealthStatus.UP)
      expect(components[1].status).to.equal(HealthStatus.UP)
    })

    it('returns DOWN status when any rocket is down', async () => {
      config.provider.sensor.areRocketFunctionsUp = fake.returns({
        'rocket1-func': false,
        'rocket2-func': false,
      })

      const indicator = new RocketsHealthIndicator()
      const result = await indicator.health(config, healthIndicatorMetadata)

      expect(result.status).to.equal(HealthStatus.DOWN)
      expect(result.components).to.have.lengthOf(2)
      const downComponents = result.components as Array<typeof result>
      expect(downComponents[0].status).to.equal(HealthStatus.DOWN)
      expect(downComponents[1].status).to.equal(HealthStatus.DOWN)
    })

    it('returns PARTIALLY_UP status when some rockets are up and some are down', async () => {
      config.provider.sensor.areRocketFunctionsUp = fake.returns({
        'rocket1-func': true,
        'rocket2-func': false,
      })

      const indicator = new RocketsHealthIndicator()
      const result = await indicator.health(config, healthIndicatorMetadata)

      expect(result.status).to.equal(HealthStatus.PARTIALLY_UP)
      expect(result.components).to.have.lengthOf(2)
      const mixedComponents = result.components as Array<typeof result>
      expect(mixedComponents[0].status).to.equal(HealthStatus.UP)
      expect(mixedComponents[1].status).to.equal(HealthStatus.DOWN)
    })
  })

  describe('when checking a specific rocket', () => {
    beforeEach(() => {
      healthIndicatorMetadata.healthIndicatorConfiguration.id = `${BOOSTER_HEALTH_INDICATORS_IDS.ROCKETS}/rocket1-func`
    })

    it('returns UP status when the rocket is up', async () => {
      config.provider.sensor.areRocketFunctionsUp = fake.returns({
        'rocket1-func': true,
        'rocket2-func': false,
      })

      const indicator = new RocketsHealthIndicator()
      const result = await indicator.health(config, healthIndicatorMetadata)

      expect(result).to.deep.equal({
        name: 'rocket1-func',
        id: `${BOOSTER_HEALTH_INDICATORS_IDS.ROCKETS}/rocket1-func`,
        status: HealthStatus.UP,
      })
    })

    it('returns DOWN status when the rocket is down', async () => {
      config.provider.sensor.areRocketFunctionsUp = fake.returns({
        'rocket1-func': false,
        'rocket2-func': true,
      })

      const indicator = new RocketsHealthIndicator()
      const result = await indicator.health(config, healthIndicatorMetadata)

      expect(result).to.deep.equal({
        name: 'rocket1-func',
        id: `${BOOSTER_HEALTH_INDICATORS_IDS.ROCKETS}/rocket1-func`,
        status: HealthStatus.DOWN,
      })
    })

    it('throws error when the rocket does not exist', async () => {
      config.provider.sensor.areRocketFunctionsUp = fake.returns({
        'rocket2-func': true,
      })

      const indicator = new RocketsHealthIndicator()
      await expect(indicator.health(config, healthIndicatorMetadata)).to.be.rejectedWith(
        'Rocket "rocket1-func" not found'
      )
    })
  })
})
