import { expect } from '../../helper/expect'
import { request } from '@boostercloud/framework-common-helpers'
import { applicationUnderTest } from './setup'
import { before } from 'mocha'

describe('Health end-to-end tests', () => {
  if (process.env.TESTED_PROVIDER === 'AWS') {
    console.log('****************** Warning **********************')
    console.log('AWS provider does not support sensor health so these tests are skipped for AWS')
    console.log('*************************************************')
    return
  }

  let url = ''
  before(async () => {
    url = applicationUnderTest.http.getHealthUrl()
  })

  it('root health returns all indicators', async () => {
    const jsonResult = await getHealth(url)

    const boosterResult = jsonResult.find((element: any) => element.id === 'booster')
    expectBooster(boosterResult)
    const boosterFunction = boosterResult.components.find((element: any) => element.id === 'booster/function')
    expectBoosterFunction(boosterFunction)
    const boosterDatabase = boosterResult.components.find((element: any) => element.id === 'booster/database')
    expectBoosterDatabase(boosterDatabase)
    const databaseEvents = boosterDatabase.components.find((element: any) => element.id === 'booster/database/events')
    expectDatabaseEvents(databaseEvents)
    const databaseReadModels = boosterDatabase.components.find(
      (element: any) => element.id === 'booster/database/readmodels'
    )
    expectDatabaseReadModels(databaseReadModels)
    const myApplicationDatabase = boosterDatabase.components.find(
      (element: any) => element.id === 'booster/database/myApplication'
    )
    expectApplicationAddDatabase(myApplicationDatabase)
    const myApplication2Database = boosterDatabase.components.find(
      (element: any) => element.id === 'booster/database/myApplication2'
    )
    expectApplication2AddDatabase(myApplication2Database)

    const appResult = jsonResult.find((element: any) => element.id === 'myApplication')
    expectApplication(appResult)
    const appChildResult = appResult.components.find((element: any) => element.id === 'myApplication/child')
    expectApplicationChild(appChildResult)
  })

  it('function health returns the indicator', async () => {
    const boosterFunction = (await getHealth(url, 'function'))[0]
    expectBoosterFunction(boosterFunction)
  })

  it('database health returns the indicator', async () => {
    const boosterDatabase = (await getHealth(url, 'database'))[0]
    expectBoosterDatabase(boosterDatabase)
    const databaseEvents = boosterDatabase.components.find((element: any) => element.id === 'booster/database/events')
    expectDatabaseEvents(databaseEvents)
    const databaseReadModels = boosterDatabase.components.find(
      (element: any) => element.id === 'booster/database/readmodels'
    )
    expectDatabaseReadModels(databaseReadModels)
    const myApplicationDatabase = boosterDatabase.components.find(
      (element: any) => element.id === 'booster/database/myApplication'
    )
    expectApplicationAddDatabase(myApplicationDatabase)
    const myApplication2Database = boosterDatabase.components.find(
      (element: any) => element.id === 'booster/database/myApplication2'
    )
    expectApplication2AddDatabase(myApplication2Database)
  })

  it('events database health returns the indicator', async () => {
    const databaseEvents = (await getHealth(url, 'database/events'))[0]
    expectDatabaseEvents(databaseEvents)
  })

  it('readmodels database health returns the indicator', async () => {
    const databaseReadModels = (await getHealth(url, 'database/readmodels'))[0]
    expectDatabaseReadModels(databaseReadModels)
  })
})

function expectBooster(boosterResult: any): void {
  expect(boosterResult.id).to.be.eq('booster')
  expect(boosterResult.status).to.be.eq('UP')
  expect(boosterResult.name).to.be.eq('Booster')
  expect(boosterResult.details.boosterVersion.length).to.be.gt(0)
  expect(boosterResult.components.length).to.be.eq(2)
}

function expectBoosterFunction(boosterFunction: any) {
  expect(boosterFunction.id).to.be.eq('booster/function')
  expect(boosterFunction.status).to.be.eq('UP')
  expect(boosterFunction.name).to.be.eq('Booster Function')
  expect(boosterFunction.details.cpus.length).to.be.gt(0)
  expect(boosterFunction.details.cpus[0].timesPercentages.length).to.be.gt(0)
  expect(boosterFunction.details.memory.totalBytes).to.be.gt(0)
  expect(boosterFunction.details.memory.freeBytes).to.be.gt(0)
  expect((boosterFunction.details.graphQL_url as string).endsWith('/graphql')).to.be.true
  expect(boosterFunction.components).to.be.undefined
}

function expectBoosterDatabase(boosterDatabase: any): void {
  expect(boosterDatabase.id).to.be.eq('booster/database')
  expect(boosterDatabase.status).to.be.eq('UP')
  expect(boosterDatabase.name).to.be.eq('Booster Database')
  expect(boosterDatabase.details).to.not.be.undefined
  expect(boosterDatabase.components.length).to.be.eq(4)
}

function expectDatabaseEvents(databaseEvent: any): void {
  expect(databaseEvent.id).to.be.eq('booster/database/events')
  expect(databaseEvent.status).to.be.eq('UP')
  expect(databaseEvent.name).to.be.eq('Booster Database Events')
  expect(databaseEvent.details).to.not.be.undefined
  expect(databaseEvent.components).to.be.undefined
}

function expectDatabaseReadModels(databaseReadModels: any): void {
  expect(databaseReadModels.id).to.be.eq('booster/database/readmodels')
  expect(databaseReadModels.status).to.be.eq('UP')
  expect(databaseReadModels.name).to.be.eq('Booster Database ReadModels')
  expect(databaseReadModels.details).to.not.be.undefined
  expect(databaseReadModels.components).to.be.undefined
}

function expectApplicationAddDatabase(applicationDatabase: any): void {
  expect(applicationDatabase.id).to.be.eq('booster/database/myApplication')
  expect(applicationDatabase.status).to.be.eq('UNKNOWN')
  expect(applicationDatabase.name).to.be.eq('Indicator added to the Booster Database indicator through My Application')
  expect(applicationDatabase.details).to.be.undefined
  expect(applicationDatabase.components).to.be.undefined
}

function expectApplication2AddDatabase(databaseApplication2: any): void {
  expect(databaseApplication2.id).to.be.eq('booster/database/myApplication2')
  expect(databaseApplication2.status).to.be.eq('UNKNOWN')
  expect(databaseApplication2.name).to.be.eq(
    'A second indicator added to the Booster Database indicator through My Application'
  )
  expect(databaseApplication2.details).to.be.undefined
  expect(databaseApplication2.components).to.be.undefined
}

function expectApplication(boosterResult: any): void {
  expect(boosterResult.id).to.be.eq('myApplication')
  expect(boosterResult.status).to.be.eq('UP')
  expect(boosterResult.name).to.be.eq('my-application')
  expect(boosterResult.details).to.be.undefined
  expect(boosterResult.components.length).to.be.eq(1)
}

function expectApplicationChild(boosterResult: any): void {
  expect(boosterResult.id).to.be.eq('myApplication/child')
  expect(boosterResult.status).to.be.eq('OUT_OF_SERVICE')
  expect(boosterResult.name).to.be.eq('My Application child')
  expect(boosterResult.details).to.be.undefined
  expect(boosterResult.components).to.be.undefined
}

async function getHealth(url: string, componentUrl?: string): Promise<any> {
  const path = componentUrl ? `${url}booster/${componentUrl}` : url
  console.log(path)
  const result = await request(path)
  expect(result).to.not.be.undefined
  expect(result.status).to.be.eq(200)
  return JSON.parse(result.body as any)
}
