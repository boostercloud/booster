/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
/* eslint-disable @typescript-eslint/no-magic-numbers */
import { expect } from '../expect'
import * as Infrastructure from '../../src/infrastructure/index'
import { replace, restore, fake, match } from 'sinon'
import { BoosterConfig, UUID } from '@boostercloud/framework-types'
import * as CDK from 'aws-cdk'
import { CdkToolkit } from 'aws-cdk/lib/cdk-toolkit'
import { StreamViewType } from '@aws-cdk/aws-dynamodb'

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
    /* FIXME: This test tends to fail because the `deploy` dependencies are not properly mocked,
     * the deployment code is being run and the process is truncated because of a timeout before
     * being able to emit the first log message.
     */
    it(
      'notifies about progress through the observer'
    ) /*, async () => {
      const config = new BoosterConfig('test')
      const fakeBootstrapEnvironment = fake.returns({ noOp: true })
      replace(CDK, 'bootstrapEnvironment', fakeBootstrapEnvironment)
      replace(CdkToolkit.prototype, 'deploy', fake())

      const observable = Infrastructure.deploy(config)

      let progressMessage = ''
      observable.subscribe((message) => {
        progressMessage = message
      })

      await observable.toPromise()

      expect(progressMessage).not.to.be.empty
    })
    */

    it('notifies about an error through the observer when an error is thrown', async () => {
      const config = new BoosterConfig('test')
      const fakeBootstrapEnvironment = fake.returns({ noOp: true })
      const fakeCdkDeployThatThrows = fake.throws(new Error('Testing error'))

      replace(CDK, 'bootstrapEnvironment', fakeBootstrapEnvironment)
      replace(CdkToolkit.prototype, 'deploy', fakeCdkDeployThatThrows)

      return expect(Infrastructure.deploy(config).toPromise()).to.eventually.be.rejected
    })

    it('builds the AppStack calling to the `getStackServiceConfiguration`') // TODO

    it('calls the CDK bootstrap with the default environment parameters', async () => {
      const config = new BoosterConfig('test')
      const fakeBootstrapEnvironment = fake.returns({ noOp: true })

      replace(CDK, 'bootstrapEnvironment', fakeBootstrapEnvironment)
      replace(CdkToolkit.prototype, 'deploy', fake())

      const observable = Infrastructure.deploy(config)
      await observable.toPromise()

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

      const observable = Infrastructure.deploy(config)
      await observable.toPromise()

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

      // Just checks that the assemble method does not fail, meaning that the stack is build correctly according to the
      // AWS validations
      expect(() => Infrastructure.assemble(config)).not.to.throw()
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
    const cloudAssembly = Infrastructure.assemble(config)

    it('generates cloudformation for a DynamoDB table to store its state', () => {
      const stackResources = cloudAssembly.getStackByName('testing-app-application-stack').template['Resources']
      const table = Object.values(stackResources).find((obj: any) => {
        return obj.Properties.TableName == 'testing-app-application-stack-SomeReadModel'
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
          TableName: 'testing-app-application-stack-SomeReadModel',
        },
        Type: 'AWS::DynamoDB::Table',
        UpdateReplacePolicy: 'Delete',
      })
    })

    it('generates cloudformation for an API endpoint for graphQL', () => {
      const stackResources = cloudAssembly.getStackByName('testing-app-application-stack').template['Resources']
      const fun: any = Object.values(stackResources).find((obj: any) => {
        return obj.Properties.FunctionName == 'testing-app-application-stack-graphql-handler'
      })
      expect(fun.Properties.Handler).to.equal('dist/index.boosterServeGraphQL')
    })
  })
})
