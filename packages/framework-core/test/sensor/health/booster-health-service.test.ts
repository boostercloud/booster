import { expect } from '../../expect'
import { BoosterHealthService } from '../../../src/sensor'
import { BOOSTER_HEALTH_INDICATORS_IDS, BoosterConfig, ProviderLibrary } from '@boostercloud/framework-types'
import { fake } from 'sinon'
import createJWKSMock from 'mock-jwks'
import { internet, phone, random } from 'faker'
import { JwksUriTokenVerifier } from '../../../src'

const jwksUri = 'https://myauth0app.auth0.com/' + '.well-known/jwks.json'
const issuer = 'auth0'

describe('BoosterHealthService', () => {
  const config = new BoosterConfig('test')
  before(() => {
    config.provider = {
      api: {
        requestSucceeded: fake((request: any) => request),
        requestFailed: fake((error: any) => error),
      },
    } as unknown as ProviderLibrary
  })

  beforeEach(() => {
    Object.values(config.sensorConfiguration.health.booster).forEach((indicator) => {
      indicator.enabled = true
    })
    config.sensorConfiguration.health.globalAuthorizer = {
      authorize: 'all',
    }
  })

  it('All indicators are UP', async () => {
    config.provider.sensor = defaultSensor()
    const boosterResult = await boosterHealth(config)
    const boosterFunction = getBoosterFunction(boosterResult)
    const boosterDatabase = getBoosterDatabase(boosterResult)
    const databaseEvents = getEventDatabase(boosterDatabase)
    const databaseReadModels = getReadModelsDatabase(boosterDatabase)
    const expectedStatus = 'UP'
    expectBooster(boosterResult, '', expectedStatus)
    expectBoosterFunction(boosterFunction, '', expectedStatus)
    expectBoosterDatabase(boosterDatabase, expectedStatus)
    expectDatabaseEvents(databaseEvents, expectedStatus)
    expectDatabaseReadModels(databaseReadModels, expectedStatus)
  })

  it('All indicators are DOWN', async () => {
    config.provider.sensor = defaultSensor()
    config.provider.sensor.isGraphQLFunctionUp = fake(() => false)
    config.provider.sensor.isDatabaseEventUp = fake(() => false)
    config.provider.sensor.areDatabaseReadModelsUp = fake(() => false)
    const expectedStatus = 'DOWN'
    const boosterResult = await boosterHealth(config)
    const boosterFunction = getBoosterFunction(boosterResult)
    const boosterDatabase = getBoosterDatabase(boosterResult)
    const databaseEvents = getEventDatabase(boosterDatabase)
    const databaseReadModels = getReadModelsDatabase(boosterDatabase)
    expectBooster(boosterResult, '', expectedStatus)
    expectBoosterFunction(boosterFunction, '', expectedStatus)
    expectBoosterDatabase(boosterDatabase, expectedStatus)
    expectDatabaseEvents(databaseEvents, expectedStatus)
    expectDatabaseReadModels(databaseReadModels, expectedStatus)
  })

  it('Details are processed', async () => {
    config.provider.sensor = defaultSensor()
    config.provider.sensor.databaseEventsHealthDetails = fake(() => ({
      test: true,
    }))
    config.provider.sensor.databaseReadModelsHealthDetails = fake(() => ({
      test: true,
    }))
    const boosterResult = await boosterHealth(config)
    const boosterFunction = getBoosterFunction(boosterResult)
    const boosterDatabase = getBoosterDatabase(boosterResult)
    const databaseEvents = getEventDatabase(boosterDatabase)
    const databaseReadModels = getReadModelsDatabase(boosterDatabase)
    const expectedStatus = 'UP'
    expectBooster(boosterResult, '', expectedStatus)
    expectBoosterFunction(boosterFunction, '', expectedStatus)
    expectBoosterDatabase(boosterDatabase, expectedStatus)
    expectDatabaseEventsWithDetails(databaseEvents, expectedStatus, {
      test: true,
    })
    expectDatabaseReadModelsWithDetails(databaseReadModels, expectedStatus, {
      test: true,
    })
  })

  it('Validates with the expected Role', async () => {
    const jwks = createJWKSMock('https://myauth0app.auth0.com/')
    jwks.start()
    const token = jwks.token({
      sub: random.uuid(),
      iss: issuer,
      'custom:role': 'UserRole',
      extraParam: 'claims',
      anotherParam: 111,
      email: internet.email(),
      phoneNumber: phone.phoneNumber(),
    })
    config.provider.sensor = defaultSensor(token)
    config.sensorConfiguration.health.globalAuthorizer = {
      authorize: [UserRole],
    }
    config.tokenVerifiers = [
      new JwksUriTokenVerifier(issuer, 'https://myauth0app.auth0.com/' + '.well-known/jwks.json'),
    ]
    const boosterResult = await boosterHealth(config)
    expectBooster(boosterResult, '', 'UP')
  })

  it('Validates fails with wrong role', async () => {
    const jwks = createJWKSMock('https://myauth0app.auth0.com/')
    jwks.start()
    const token = jwks.token({
      sub: random.uuid(),
      iss: issuer,
      'custom:role': 'UserRole1',
      extraParam: 'claims',
      anotherParam: 111,
      email: internet.email(),
      phoneNumber: phone.phoneNumber(),
    })

    config.provider.sensor = defaultSensor(token)
    config.sensorConfiguration.health.globalAuthorizer = {
      authorize: [UserRole],
    }
    config.tokenVerifiers = [new JwksUriTokenVerifier(issuer, jwksUri)]
    const boosterHealthService = new BoosterHealthService(config)
    const boosterResult = (await boosterHealthService.boosterHealth(undefined)) as any
    await jwks.stop()
    expect(boosterResult.code).to.be.eq('NotAuthorizedError')
  })

  it('Only root enabled and without children and details', async () => {
    config.provider.sensor = defaultSensor()
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.ROOT].enabled = true
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.DATABASE].enabled = false
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_EVENTS].enabled = false
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_READ_MODELS].enabled = false
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.FUNCTION].enabled = false
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.ROOT].details = false
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.DATABASE].details = false
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_EVENTS].details = false
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_READ_MODELS].details = false
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.FUNCTION].details = false
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.ROOT].showChildren = false
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.DATABASE].showChildren = false
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_EVENTS].showChildren = false
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_READ_MODELS].showChildren = false
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.FUNCTION].showChildren = false

    // get root
    const boosterResult = await boosterHealth(config)

    // root without children and details
    expectDefaultResult(boosterResult, 'UP', 'booster', 'Booster', 0)
    expect(boosterResult.details).to.be.undefined

    // other indicators are undefined
    expect(getBoosterDatabase(boosterResult)).to.be.undefined
    expect(getEventDatabase(boosterResult)).to.be.undefined
    expect(getBoosterFunction(boosterResult)).to.be.undefined
    expect(getReadModelsDatabase(boosterResult)).to.be.undefined
  })

  it('if parent disabled then children are disabled', async () => {
    config.provider.sensor = defaultSensor('', 'booster/database/readmodels')
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.ROOT].enabled = false
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.DATABASE].enabled = false
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_EVENTS].enabled = false
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_READ_MODELS].enabled = true
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.FUNCTION].enabled = false
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.ROOT].details = false
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.DATABASE].details = false
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_EVENTS].details = false
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_READ_MODELS].details = false
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.FUNCTION].details = false
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.ROOT].showChildren = false
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.DATABASE].showChildren = false
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_EVENTS].showChildren = false
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_READ_MODELS].showChildren = false
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.FUNCTION].showChildren = false

    const readModelsResult = await boosterHealth(config)
    expect(readModelsResult).to.be.undefined
  })

  it('Only ReadModels enabled and without children and details', async () => {
    config.provider.sensor = defaultSensor('', 'booster/database/readmodels')
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.ROOT].enabled = true
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.DATABASE].enabled = true
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_EVENTS].enabled = false
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_READ_MODELS].enabled = true
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.FUNCTION].enabled = false
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.ROOT].details = false
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.DATABASE].details = false
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_EVENTS].details = false
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_READ_MODELS].details = false
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.FUNCTION].details = false
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.ROOT].showChildren = false
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.DATABASE].showChildren = false
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_EVENTS].showChildren = false
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.DATABASE_READ_MODELS].showChildren = false
    config.sensorConfiguration.health.booster[BOOSTER_HEALTH_INDICATORS_IDS.FUNCTION].showChildren = false

    const readModelsResult = await boosterHealth(config)
    expectDatabaseReadModels(readModelsResult, 'UP')
  })
})

function defaultSensor(token?: string, url?: string) {
  return {
    databaseEventsHealthDetails: fake(() => {}),
    databaseReadModelsHealthDetails: fake(() => {}),
    isGraphQLFunctionUp: fake(() => true),
    isDatabaseEventUp: fake(() => true),
    areDatabaseReadModelsUp: fake(() => true),
    databaseUrls: fake(() => []),
    graphQLFunctionUrl: fake(() => ''),
    rawRequestToHealthEnvelope: fake(() => {
      return { token: token, componentPath: url }
    }),
  }
}

async function boosterHealth(config: BoosterConfig): Promise<any> {
  const boosterHealthService = new BoosterHealthService(config)
  const result = (await boosterHealthService.boosterHealth(undefined)) as any
  return result[0]
}

function getBoosterFunction(boosterResult: any) {
  return boosterResult.components?.find((element: any) => element.id === 'booster/function')
}

function getBoosterDatabase(boosterResult: any) {
  return boosterResult.components?.find((element: any) => element.id === 'booster/database')
}

function getEventDatabase(boosterDatabase: any) {
  return boosterDatabase.components?.find((element: any) => element.id === 'booster/database/events')
}

function getReadModelsDatabase(boosterDatabase: any) {
  return boosterDatabase.components?.find((element: any) => element.id === 'booster/database/readmodels')
}

function expectDefaultResult(result: any, status: string, id: string, name: string, componentsLength: number) {
  expect(result.id).to.be.eq(id)
  expect(result.status).to.be.eq(status)
  expect(result.name).to.be.eq(name)
  if (componentsLength === 0) {
    expect(result.components).to.be.undefined
  } else {
    expect(result.components.length).to.be.eq(componentsLength)
  }
}

function expectBooster(boosterResult: any, version: string, status: string): void {
  expectDefaultResult(boosterResult, status, 'booster', 'Booster', 2)
  expect(boosterResult.details.boosterVersion).to.be.eq(version)
}

function expectBoosterFunction(boosterFunction: any, url: string, status: string) {
  expectDefaultResult(boosterFunction, status, 'booster/function', 'Booster Function', 0)
  expect(boosterFunction.details.cpus.length).to.be.gt(0)
  expect(boosterFunction.details.cpus[0].timesPercentages.length).to.be.gt(0)
  expect(boosterFunction.details.memory.totalBytes).to.be.gt(0)
  expect(boosterFunction.details.memory.freeBytes).to.be.gt(0)
  expect(boosterFunction.details.graphQL_url as string).to.be.eq(url)
}

function expectBoosterDatabase(boosterDatabase: any, status: string): void {
  expectDefaultResult(boosterDatabase, status, 'booster/database', 'Booster Database', 2)
  expect(boosterDatabase.details).to.not.be.undefined
}

function expectDatabaseEvents(databaseEvents: any, status: string): void {
  expectDefaultResult(databaseEvents, status, 'booster/database/events', 'Booster Database Events', 0)
  expect(databaseEvents.details).to.be.undefined
}

function expectDatabaseEventsWithDetails(databaseEvents: any, status: string, details: any): void {
  expectDefaultResult(databaseEvents, status, 'booster/database/events', 'Booster Database Events', 0)
  expect(databaseEvents.details).to.be.deep.eq(details)
}

function expectDatabaseReadModels(databaseReadModels: any, status: string): void {
  expectDefaultResult(
    databaseReadModels,
    status,
    'booster/database/readmodels',
    'Booster Database ReadModels',
    0
  )
  expect(databaseReadModels.details).to.be.undefined
}

function expectDatabaseReadModelsWithDetails(databaseReadModels: any, status: string, details: any): void {
  expectDefaultResult(
    databaseReadModels,
    status,
    'booster/database/readmodels',
    'Booster Database ReadModels',
    0
  )
  expect(databaseReadModels.details).to.be.deep.eq(details)
}

class UserRole {}
