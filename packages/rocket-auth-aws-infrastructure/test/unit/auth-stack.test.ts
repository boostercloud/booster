import { BoosterConfig } from '@boostercloud/framework-types'
import { App, CfnOutput, Stack, StackProps } from '@aws-cdk/core'
import { AuthStack, AWSAuthRocketParams } from '../../src/auth-stack'
import * as lambda from '@aws-cdk/aws-lambda'

import * as chai from 'chai'
import { UserPool, UserPoolClient, UserPoolDomain } from '@aws-cdk/aws-cognito'
import { RestApi } from '@aws-cdk/aws-apigateway'

chai.use(require('sinon-chai'))
chai.use(require('chai-as-promised'))

const expect = chai.expect

describe('Auth api creation', () => {
  context('when no roles defined', () => {
    it('nothing should be built', () => {
      const config = new BoosterConfig('test')
      config.appName = 'testing-app'
      const appStack = new Stack(new App(), config.resourceNames.applicationStack, {} as StackProps)
      const params: AWSAuthRocketParams = {
        mode: 'UserPassword',
      }
      const userPoolID = `${AuthStack.rocketArtifactsPrefix(config)}-user-pool`

      AuthStack.mountStack(params, appStack, config)

      const userPool = appStack.node.tryFindChild(userPoolID) as UserPool

      expect(userPool).to.be.undefined
    })
  })

  context('when roles defined', () => {
    const initAppStack = (mode: 'UserPassword' | 'Passwordless'): { appStack: Stack; basePrefix: string } => {
      const config = new BoosterConfig('test')
      config.appName = 'testing-app'
      config.roles['User'] = {
        auth: {
          signUpMethods: ['email'],
          skipConfirmation: false,
        },
      }
      config.roles['Admin'] = {
        auth: {
          signUpMethods: ['email', 'phone'],
          skipConfirmation: false,
        },
      }
      const appStack = new Stack(new App(), config.resourceNames.applicationStack, {} as StackProps)
      const params: AWSAuthRocketParams = {
        mode,
      }
      AuthStack.mountStack(params, appStack, config)
      const basePrefix = AuthStack.rocketArtifactsPrefix(config)
      return {
        appStack,
        basePrefix,
      }
    }

    const verifyUserPool = (appStack: Stack, basePrefix: string): void => {
      const userPoolID = `${basePrefix}-user-pool`
      const localUserPoolDomainID = `${basePrefix}-user-pool-domain`

      const userPool = appStack.node.tryFindChild(userPoolID) as UserPool
      expect(userPool).not.to.be.undefined

      const userPoolDomain = appStack.node.tryFindChild(localUserPoolDomainID) as UserPoolDomain
      expect(userPoolDomain).not.to.be.undefined
    }

    const verifyPreSignUp = (appStack: Stack, basePrefix: string): void => {
      const functionId = `${basePrefix}-pre-sign-up`
      const lambda = appStack.node.tryFindChild(functionId) as lambda.Function
      expect(lambda).not.to.be.undefined
    }

    const verifyLambdaTriggers = (appStack: Stack, basePrefix: string, expected: boolean): void => {
      const lambdaTriggers = [
        `${basePrefix}-create-auth-challenge`,
        `${basePrefix}-define-auth-challenge`,
        `${basePrefix}-verify-auth-challenge`,
      ]
      lambdaTriggers.forEach((item) => {
        const lambda = appStack.node.tryFindChild(item) as lambda.Function
        expected ? expect(lambda).not.to.be.undefined : expect(lambda).to.be.undefined
      })
    }

    const verifyUserPoolClient = (appStack: Stack, basePrefix: string): void => {
      const userPoolClientID = `${basePrefix}-user-pool-client`
      const userPoolClient = appStack.node.tryFindChild(userPoolClientID) as UserPoolClient
      expect(userPoolClient).not.to.be.undefined
    }

    const verifyOutputs = (appStack: Stack): void => {
      const outputs = ['AuthApiEndpoint', 'AuthApiIssuer', 'AuthApiJwksUri']
      outputs.forEach((item) => {
        const output = appStack.node.tryFindChild(item) as CfnOutput
        expect(output).not.to.be.undefined
      })
    }

    describe('user password mode', () => {
      const config = new BoosterConfig('test')
      config.appName = 'testing-app'
      config.roles['User'] = {
        auth: {
          signUpMethods: ['email'],
          skipConfirmation: false,
        },
      }
      config.roles['Admin'] = {
        auth: {
          signUpMethods: ['email', 'phone'],
          skipConfirmation: false,
        },
      }
      const { appStack, basePrefix } = initAppStack('UserPassword')

      it('it should create a user pool and user pool domain', () => {
        verifyUserPool(appStack, basePrefix)
      })

      it('it should create a pre-signUp trigger', () => {
        verifyPreSignUp(appStack, basePrefix)
      })

      it("it shouldn't create lambda auth challenges", () => {
        verifyLambdaTriggers(appStack, basePrefix, false)
      })

      it('it should create a user pool client', () => {
        verifyUserPoolClient(appStack, basePrefix)
      })

      it('it should create a rest api', () => {
        const apiId = `${basePrefix}-api`
        const api = appStack.node.tryFindChild(apiId) as RestApi
        expect(api).not.to.be.undefined
        expect(api.root?.getResource('auth')).not.to.be.undefined
        expect(api.root?.getResource('auth')?.getResource('sign-in')).not.to.be.undefined
        expect(
          api.root
            ?.getResource('auth')
            ?.getResource('sign-up')
            ?.getResource('confirm')
        ).not.to.be.undefined
        expect(
          api.root
            ?.getResource('auth')
            ?.getResource('sign-up')
            ?.getResource('resend-code')
        ).not.to.be.undefined

        expect(
          api.root
            ?.getResource('auth')
            ?.getResource('token')
            ?.getResource('refresh')
        ).not.to.be.undefined
        expect(
          api.root
            ?.getResource('auth')
            ?.getResource('token')
            ?.getResource('revoke')
        ).not.to.be.undefined
        expect(
          api.root
            ?.getResource('auth')
            ?.getResource('password')
            ?.getResource('forgot')
        ).not.to.be.undefined
        expect(
          api.root
            ?.getResource('auth')
            ?.getResource('password')
            ?.getResource('change')
        ).not.to.be.undefined
      })

      it('it should create cloud formation outputs', () => {
        verifyOutputs(appStack)
      })
    })

    describe('passwordless mode', () => {
      const { appStack, basePrefix } = initAppStack('Passwordless')

      it('it should create a user pool and user pool domain', () => {
        verifyUserPool(appStack, basePrefix)
      })

      it('it should create a pre-signUp trigger', () => {
        verifyPreSignUp(appStack, basePrefix)
      })

      it('it should create lambda auth challenges', () => {
        verifyLambdaTriggers(appStack, basePrefix, true)
      })

      it('it should create a user pool client', () => {
        verifyUserPoolClient(appStack, basePrefix)
      })

      it('it should create a rest api', () => {
        const apiId = `${basePrefix}-api`
        const api = appStack.node.tryFindChild(apiId) as RestApi
        expect(api).not.to.be.undefined
        expect(api.root?.getResource('auth')).not.to.be.undefined
        expect(api.root?.getResource('auth')?.getResource('sign-in')).not.to.be.undefined
        expect(
          api.root
            ?.getResource('auth')
            ?.getResource('sign-up')
            ?.getResource('confirm')
        ).not.to.be.undefined
        expect(
          api.root
            ?.getResource('auth')
            ?.getResource('sign-up')
            ?.getResource('resend-code')
        ).not.to.be.undefined

        expect(
          api.root
            ?.getResource('auth')
            ?.getResource('token')
            ?.getResource('refresh')
        ).not.to.be.undefined
        expect(
          api.root
            ?.getResource('auth')
            ?.getResource('token')
            ?.getResource('revoke')
        ).not.to.be.undefined
        expect(
          api.root
            ?.getResource('auth')
            ?.getResource('password')
            ?.getResource('forgot')
        ).to.be.undefined
        expect(
          api.root
            ?.getResource('auth')
            ?.getResource('password')
            ?.getResource('change')
        ).to.be.undefined
      })

      it('it should create cloud formation outputs', () => {
        verifyOutputs(appStack)
      })
    })
  })
})
