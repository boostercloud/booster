/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { expect } from '../../expect'
import { BoosterConfig, UUID } from '@boostercloud/framework-types'
import { App } from '@aws-cdk/core'
import { Function } from '@aws-cdk/aws-lambda'
import { CfnUserPool, CfnUserPoolDomain, UserPoolClient } from '@aws-cdk/aws-cognito'
import { RestApi } from '@aws-cdk/aws-apigateway'
import { CfnApi } from '@aws-cdk/aws-apigatewayv2'
import { InfrastructureRocket } from '../../../src/rockets/infrastructure-rocket'
import { fake } from 'sinon'
import { StackProps } from '@aws-cdk/core'
import { Stack } from '@aws-cdk/core'

const rewire = require('rewire')
const applicationStack = rewire('../../../src/infrastructure/stacks/application-stack')

describe('the application stack builder', () => {
  class TestReadModel1 {
    public id: UUID = ''
  }
  class TestReadModel2 {
    public id: UUID = ''
  }
  const readModels = [TestReadModel1, TestReadModel2]

  const config = new BoosterConfig('test')
  config.appName = 'testing-app'
  // eslint-disable-next-line prettier/prettier
  readModels.forEach((readModel) => {
    config.readModels[readModel.name] = {
      class: readModel,
      authorizedRoles: 'all',
      properties: [],
    }
  })
  config.env['A_CUSTOM_ENV_VARIABLE'] = 'important-value'

  it('builds the application stack of a simple app correctly', () => {
    const boosterApp = new App()

    new applicationStack.ApplicationStackBuilder(config).buildOn(boosterApp)

    const appStackName = config.resourceNames.applicationStack
    const appStack = boosterApp.node.findChild(appStackName).node

    const restAPIName = appStackName + '-rest-api'
    const websocketAPIName = appStackName + '-websocket-api'
    const eventsStore = 'events-store'
    const eventsLambda = 'events-main'
    const graphQLLambda = 'graphql-handler'
    const subscriptionsNotifierLambda = 'subscriptions-notifier'
    const subscriptionsStore = appStackName + '-subscriptions-store'
    const connectionsStore = appStackName + '-connections-store'
    const websocketRoutes = ['route-$connect', 'route-$disconnect', 'route-$default']

    const restAPI = appStack.tryFindChild(restAPIName) as RestApi
    const websocketAPI = appStack.tryFindChild(websocketAPIName) as CfnApi
    const numberOfLambdas = appStack.children.filter((child) => child instanceof Function).length

    // First check for all the constructs that must be created
    // REST API-related
    expect(restAPI).not.to.be.undefined
    expect(restAPI.root.getResource('graphql')).not.to.be.undefined
    // Websocket API-related
    expect(websocketAPI).not.to.be.undefined
    expect(websocketAPI.protocolType).to.be.eq('WEBSOCKET')
    websocketRoutes.forEach((route) => expect(appStack.tryFindChild(route)).not.to.be.undefined)

    expect(numberOfLambdas).to.equal(3)

    // GraphQL related
    expect(appStack.tryFindChild(graphQLLambda)).not.to.be.undefined
    expect(appStack.tryFindChild(subscriptionsNotifierLambda)).not.to.be.undefined
    expect(appStack.tryFindChild(subscriptionsStore)).not.to.be.undefined
    expect(appStack.tryFindChild(connectionsStore)).not.to.be.undefined
    // Events-related
    expect(appStack.tryFindChild(eventsLambda)).not.to.be.undefined
    expect(appStack.tryFindChild(eventsStore)).not.to.be.undefined
    // ReadModels
    readModels.forEach(({ name }) => expect(appStack.tryFindChild(name)).not.to.be.undefined)

    // Now, check all the construct that must NOT be created (related to roles)
    expect(restAPI.root.getResource('auth')).to.be.undefined
    // None of the Cognito constructs should be created
    appStack.children.forEach((child) => {
      expect(child.constructor.name).not.to.be.oneOf([CfnUserPool.name, CfnUserPoolDomain.name, UserPoolClient.name])
    })
  })

  it('builds the application stack of an app with roles correctly', () => {
    config.roles['Admin'] = {
      auth: {
        signUpMethods: [],
      },
    }

    const boosterApp = new App()
    new applicationStack.ApplicationStackBuilder(config).buildOn(boosterApp)
    const appStackName = config.resourceNames.applicationStack
    const appStack = boosterApp.node.findChild(appStackName).node

    const apiName = appStackName + '-rest-api'
    const preSignUpValidator = 'pre-sign-up-validator'
    const userPool = 'user-pool'
    const userPoolDomain = 'user-pool-domain'
    const userPoolClient = 'user-pool-client'
    const clientID = 'clientID'
    const api = appStack.tryFindChild(apiName) as RestApi
    const lambdas = appStack.children.filter((child) => child instanceof Function)
    const numberOfLambdas = lambdas.length

    // Just check for all the EXTRA constructs that must be created to support roles
    // API-related
    expect(api).not.to.be.undefined
    expect(api.root.getResource('auth')).not.to.be.undefined
    // Lambdas
    expect(numberOfLambdas).to.equal(4)
    expect(appStack.tryFindChild(preSignUpValidator)).not.to.be.undefined
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    lambdas.forEach((lambda: any) => {
      expect(lambda.environment.BOOSTER_ENV.value).to.equal('test')
      expect(lambda.environment.A_CUSTOM_ENV_VARIABLE.value).to.equal('important-value')
    })

    // UserPool-related
    expect(appStack.tryFindChild(userPool)).not.to.be.undefined
    expect(appStack.tryFindChild(userPoolDomain)).not.to.be.undefined
    expect(appStack.tryFindChild(userPoolClient)).not.to.be.undefined
    expect(appStack.tryFindChild(clientID)).not.to.be.undefined
    // Check all read models
    readModels.forEach(({ name }) => expect(appStack.tryFindChild(name)).not.to.be.undefined)
  })

  it('allows rockets to extend the stack', () => {
    const boosterApp = new App()

    const fakeBuildStack = fake(
      (app: App, applicationStack: string, props?: StackProps): Stack => new Stack(app, applicationStack, props)
    )

    const restoreBuildStack = applicationStack.__set__('buildStack', fakeBuildStack)

    const fakeRocket: InfrastructureRocket = {
      mountStack: fake(),
      unmountStack: fake(),
    }

    new applicationStack.ApplicationStackBuilder(config).buildOn(boosterApp, [fakeRocket])

    const stack = fakeBuildStack.returnValues[0]

    console.log(stack)

    expect(fakeRocket.mountStack).to.have.been.calledWith(stack)
    restoreBuildStack()
  })
})
