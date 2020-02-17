/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { expect } from 'chai'
import { BoosterConfig, UUID } from '@boostercloud/framework-types'
import { ApplicationStackBuilder } from '../../../src/infrastructure/stacks/application-stack'
import { App } from '@aws-cdk/core'
import { Function } from '@aws-cdk/aws-lambda'
import { CfnUserPool, CfnUserPoolDomain, UserPoolClient } from '@aws-cdk/aws-cognito'
import { RestApi } from '@aws-cdk/aws-apigateway'

describe('the application stack builder', () => {
  class TestReadModel1 {
    public id: UUID = ''
  }
  class TestReadModel2 {
    public id: UUID = ''
  }
  const readModels = [TestReadModel1, TestReadModel2]

  const config = new BoosterConfig()
  config.appName = 'testing-app'
  // eslint-disable-next-line prettier/prettier
  readModels.forEach((readModel) => {
    config.readModels[readModel.name] = {
      class: readModel,
    }
  })

  it('builds the application stack of a simple app correctly', () => {
    const boosterApp = new App()
    new ApplicationStackBuilder(config).buildOn(boosterApp)

    const appStackName = config.resourceNames.applicationStack
    const appStack = boosterApp.node.findChild(appStackName).node

    const apiName = appStackName + '-api'
    const commandsLambdaName = 'commands-main'
    const eventsStreamName = 'events-stream'
    const eventsStore = 'events-store'
    const eventsLambda = 'events-main'
    const readModelFetcherLambda = 'read-model-fetcher'
    const api = appStack.tryFindChild(apiName) as RestApi
    const numberOfLambdas = appStack.children.filter((child) => child instanceof Function).length

    // First check for all the constructs that must be created
    // API-related
    expect(api).not.to.be.undefined
    expect(api.root.getResource('commands')).not.to.be.undefined
    expect(api.root.getResource('readmodels')).not.to.be.undefined
    // Lambdas
    expect(numberOfLambdas).to.equal(3)
    expect(appStack.tryFindChild(commandsLambdaName)).not.to.be.undefined
    expect(appStack.tryFindChild(eventsLambda)).not.to.be.undefined
    expect(appStack.tryFindChild(readModelFetcherLambda)).not.to.be.undefined
    // Events-related
    expect(appStack.tryFindChild(eventsStreamName)).not.to.be.undefined
    expect(appStack.tryFindChild(eventsStore)).not.to.be.undefined
    // ReadModels
    readModels.forEach(({ name }) => expect(appStack.tryFindChild(name)).not.to.be.undefined)

    // Now, check all the construct that must NOT be created (related to roles)
    expect(api.root.getResource('auth')).to.be.undefined
    // None of the Cognito constructs should be created
    appStack.children.forEach((child) => {
      expect(child.constructor.name).not.to.be.oneOf([CfnUserPool.name, CfnUserPoolDomain.name, UserPoolClient.name])
    })
  })

  it('builds the application stack of an app with roles correctly', () => {
    config.roles['Admin'] = {
      allowSelfSignUp: false,
    }

    const boosterApp = new App()
    new ApplicationStackBuilder(config).buildOn(boosterApp)
    const appStackName = config.resourceNames.applicationStack
    const appStack = boosterApp.node.findChild(appStackName).node

    const apiName = appStackName + '-api'
    const preSignUpValidator = 'pre-sign-up-validator'
    const userPool = 'user-pool'
    const userPoolDomain = 'user-pool-domain'
    const userPoolClient = 'user-pool-client'
    const clientID = 'clientID'
    const api = appStack.tryFindChild(apiName) as RestApi
    const numberOfLambdas = appStack.children.filter((child) => child instanceof Function).length

    // Just check for all the EXTRA constructs that must be created to support roles
    // API-related
    expect(api).not.to.be.undefined
    expect(api.root.getResource('auth')).not.to.be.undefined
    // Lambdas
    expect(numberOfLambdas).to.equal(4)
    expect(appStack.tryFindChild(preSignUpValidator)).not.to.be.undefined
    // UserPool-related
    expect(appStack.tryFindChild(userPool)).not.to.be.undefined
    expect(appStack.tryFindChild(userPoolDomain)).not.to.be.undefined
    expect(appStack.tryFindChild(userPoolClient)).not.to.be.undefined
    expect(appStack.tryFindChild(clientID)).not.to.be.undefined
    // Check all read models
    readModels.forEach(({ name }) => expect(appStack.tryFindChild(name)).not.to.be.undefined)
  })
})
