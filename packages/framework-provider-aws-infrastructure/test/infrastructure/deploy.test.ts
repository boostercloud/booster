/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
/* eslint-disable @typescript-eslint/no-magic-numbers */
import { expect } from '../expect'
import { replace, restore, fake, match } from 'sinon'
import { BoosterConfig, Logger, UUID } from '@boostercloud/framework-types'
import * as CDK from 'aws-cdk'
import { CdkToolkit } from 'aws-cdk/lib/cdk-toolkit'
import { StreamViewType } from '@aws-cdk/aws-dynamodb'

const rewire = require('rewire')
const Infrastructure = rewire('../../src/infrastructure/index')
const privateAssemble = Infrastructure.__get__('assemble')

const testEnvironment = {
  account: 'testAccount',
  region: 'testRegion',
}

describe('the deployment process', () => {
  beforeEach(() => {
    replace(CDK.SDK.prototype, 'defaultAccount', fake.returns(testEnvironment.account))
    replace(CDK.SDK.prototype, 'defaultRegion', fake.returns(testEnvironment.region))
  })

  afterEach(() => {
    restore()
  })

  describe('the `deploy` method', () => {
    it('logs progress through the passed logger', async () => {
      const config = new BoosterConfig('test')
      const fakeBootstrapEnvironment = fake.returns({ noOp: true })
      replace(CDK, 'bootstrapEnvironment', fakeBootstrapEnvironment)
      replace(CdkToolkit.prototype, 'deploy', fake())

      const logger = ({
        info: fake(),
      } as unknown) as Logger

      await Infrastructure.deploy(config, logger)

      expect(logger.info).to.have.been.called
    })

    it('logs an error through the passed logger when an error is thrown', async () => {
      const config = new BoosterConfig('test')
      const fakeBootstrapEnvironment = fake.returns({ noOp: true })
      const errorMessage = 'Testing error'
      const fakeCdkDeployThatThrows = fake.throws(errorMessage)
      replace(CDK, 'bootstrapEnvironment', fakeBootstrapEnvironment)
      replace(CdkToolkit.prototype, 'deploy', fakeCdkDeployThatThrows)

      const logger: Logger = {
        info: fake(),
        error: fake(),
        debug: fake(),
      }

      await expect(Infrastructure.deploy(config, logger)).not.to.eventually.be.rejected
      // It receives the thrown Error object, not just the message
      expect(logger.error).to.have.been.calledWithMatch({ message: errorMessage })
    })

    it('builds the AppStack calling to the `getStackServiceConfiguration`', async () => {
      const config = new BoosterConfig('test')
      const fakeBootstrapEnvironment = fake.returns({ noOp: true })
      replace(CDK, 'bootstrapEnvironment', fakeBootstrapEnvironment)
      replace(CdkToolkit.prototype, 'deploy', fake())
      // Monkey patching this private function to be able to spy it
      const fakeGetStackServiceConfiguration = fake(Infrastructure.__get__('getStackServiceConfiguration'))
      Infrastructure.__set__('getStackServiceConfiguration', fakeGetStackServiceConfiguration)

      const logger = ({
        info: fake(),
      } as unknown) as Logger

      await Infrastructure.deploy(config, logger)

      expect(fakeGetStackServiceConfiguration).to.have.been.calledOnceWith(config)
    })

    it('calls the CDK bootstrap with the default environment parameters', async () => {
      const config = new BoosterConfig('test')
      const fakeBootstrapEnvironment = fake.returns({ noOp: true })

      replace(CDK, 'bootstrapEnvironment', fakeBootstrapEnvironment)
      replace(CdkToolkit.prototype, 'deploy', fake())

      const logger = ({
        info: fake(),
      } as unknown) as Logger

      await Infrastructure.deploy(config, logger)

      expect(fakeBootstrapEnvironment).to.have.been.calledOnce
      expect(fakeBootstrapEnvironment).to.be.calledWith(match(testEnvironment))
    })

    it('calls the CDK bootstrap with the environment parameters overridden by the configuration') //TODO

    it('calls the CDK bootstrap with the right config parameters', async () => {
      const config = new BoosterConfig('test')
      const testAppName = 'testing'
      config.appName = testAppName

      const fakeBootstrapEnvironment = fake.returns({ noOp: true })

      replace(CDK, 'bootstrapEnvironment', fakeBootstrapEnvironment)
      replace(CdkToolkit.prototype, 'deploy', fake())

      const logger = ({
        info: fake(),
      } as unknown) as Logger

      await Infrastructure.deploy(config, logger)

      const appNamePrefixRegExp = new RegExp('^' + testAppName + '-')
      expect(fakeBootstrapEnvironment).to.have.been.calledOnce
      expect(fakeBootstrapEnvironment).to.be.calledWith(
        match.any,
        match.any,
        match(appNamePrefixRegExp),
        match.any,
        match.has('bucketName', match(appNamePrefixRegExp))
      )
    })
  })

  describe('the `assemble` method', () => {
    it('generates the CloudAssembly correctly for a simple configuration', () => {
      class EmptyEntity {
        public id: UUID = ''
      }

      const config = new BoosterConfig('test')
      config.appName = 'testing-app'
      config.entities[EmptyEntity.name] = {
        class: EmptyEntity,
      }

      // Just checks that the assemble method does not fail,
      // meaning that the stack is build correctly according to the
      // AWS validations
      expect(() => privateAssemble(config, [])).not.to.throw()
    })
  })

  context('when roles and permissions have been defined', () => {
    it('generates the auth endpoints') // TODO
    it('generates a lambda to check authorization') // TODO
  })

  context('when there is a configured command', () => {
    it('generates an API endpoint to submit it') // TODO
    it('generates a lambda to dispatch the commands') // TODO
  })

  context('when there is a configured event', () => {
    it('generates a DynamoDB table to store the events') // TODO
    it('generates a lambda to dispatch the events') // TODO
  })

  context('for a configured read model', () => {
    class SomeReadModel {
      public id: UUID = ''
    }

    const config = new BoosterConfig('test')
    config.appName = 'testing-app'
    config.readModels[SomeReadModel.name] = {
      class: SomeReadModel,
      authorizedRoles: 'all',
      properties: [],
    }
    const cloudAssembly = privateAssemble(config, [])

    it('generates cloudformation for a DynamoDB table to store its state', () => {
      const stackResources = cloudAssembly.getStackByName('testing-app-app').template['Resources']
      const table = Object.values(stackResources).find((obj: any) => {
        return obj.Properties.TableName == 'testing-app-app-SomeReadModel'
      })
      expect(table).to.deep.equal({
        DeletionPolicy: 'Delete',
        Properties: {
          AttributeDefinitions: [
            {
              AttributeName: 'id',
              AttributeType: 'S',
            },
          ],
          BillingMode: 'PAY_PER_REQUEST',
          KeySchema: [
            {
              AttributeName: 'id',
              KeyType: 'HASH',
            },
          ],
          StreamSpecification: {
            StreamViewType: StreamViewType.NEW_IMAGE,
          },
          TableName: 'testing-app-app-SomeReadModel',
        },
        Type: 'AWS::DynamoDB::Table',
        UpdateReplacePolicy: 'Delete',
      })
    })

    it('generates cloudformation for an API endpoint for graphQL', () => {
      const stackResources = cloudAssembly.getStackByName('testing-app-app').template['Resources']
      const fun: any = Object.values(stackResources).find((obj: any) => {
        return obj.Properties.FunctionName == 'testing-app-app-graphql-handler'
      })
      expect(fun.Properties.Handler).to.equal('dist/index.boosterServeGraphQL')
    })
  })
})
