import { StreamViewType } from '@aws-cdk/aws-dynamodb'
import { InfrastructureRocket } from '../../src'
import { BoosterConfig, UUID } from '@boostercloud/framework-types'
import { fake, replace, restore, spy } from 'sinon'
import { expect } from '../expect'
import { ApplicationStackBuilder } from '../../src/infrastructure/stacks/application-stack'
import { SdkProvider } from 'aws-cdk'

const rewire = require('rewire')
const StackTools = rewire('../../src/infrastructure/stack-tools')

const testEnvironment = {
  account: 'testAccount',
  region: 'testRegion',
}

describe('the `stack-tools` module', () => {
  afterEach(() => {
    restore()
  })

  describe('the `getStackServiceConfiguration` method', () => {
    it('builds the configuration using the `assemble` method', async () => {
      replace(SdkProvider.prototype, 'forEnvironment', fake())
      const fakeAssemble = fake()
      const revertAssemble = StackTools.__set__('assemble', fakeAssemble)
      const revertGetEnvironment = StackTools.__set__('getEnvironment', fake.returns(Promise.resolve(testEnvironment)))

      const config = {} as BoosterConfig

      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const { cdkToolkit } = (await StackTools.getStackServiceConfiguration(config)) as any
      // We're hacking the CDK to make it believe it's deploying the app
      cdkToolkit.props.cloudExecutable.props.synthesizer()

      // Even with no parameters, `assemble` should receive the config via closure
      expect(fakeAssemble).to.have.been.calledWithMatch(config)
      revertAssemble()
      revertGetEnvironment()
    })

    context('with rockets', () => {
      it('forwards the rocket list to the `assemble` method for initialization', async () => {
        replace(SdkProvider.prototype, 'forEnvironment', fake())
        const fakeAssemble = fake()
        const revertRewire = StackTools.__set__('assemble', fakeAssemble)
        const revertGetEnvironment = StackTools.__set__(
          'getEnvironment',
          fake.returns(Promise.resolve(testEnvironment))
        )

        const config = {} as BoosterConfig

        const fakeRocket: InfrastructureRocket = {
          mountStack: fake(),
          unmountStack: fake(),
        }

        const { cdkToolkit } = (await StackTools.getStackServiceConfiguration(config, [
          fakeRocket,
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
        ])) as any
        // We're hacking the CDK to make it believe it's deploying the app
        cdkToolkit.props.cloudExecutable.props.synthesizer()

        expect(fakeAssemble).to.have.been.calledWithMatch(config, [fakeRocket])
        revertRewire()
        revertGetEnvironment()
      })
    })
  })

  describe('the `assemble` method', () => {
    const assemble = StackTools.__get__('assemble')

    it('generates the CloudAssembly correctly for a simple configuration', () => {
      class EmptyEntity {
        public id: UUID = ''
      }

      const config = new BoosterConfig('test')
      config.userProjectRootPath = '.'
      config.appName = 'testing-app'
      config.entities[EmptyEntity.name] = {
        class: EmptyEntity,
        authorizeReadEvents: [],
      }

      // Just checks that the assemble method does not fail,
      // meaning that the stack is built correctly according to the
      // AWS validations
      expect(() => assemble(config)).not.to.throw()
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
      config.userProjectRootPath = '.'
      config.appName = 'testing-app'
      config.readModels[SomeReadModel.name] = {
        class: SomeReadModel,
        authorizedRoles: 'all',
        properties: [],
      }
      const cloudAssembly = assemble(config)

      it('generates cloudformation for a DynamoDB table to store its state', () => {
        const stackResources = cloudAssembly.getStackByName('testing-app-app').template['Resources']
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
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
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const fun: any = Object.values(stackResources).find((obj: any) => {
          return obj.Properties.FunctionName == 'testing-app-app-graphql-handler'
        })
        expect(fun.Properties.Handler).to.equal('dist/index.boosterServeGraphQL')
      })
    })

    context('with rockets', () => {
      it('initializes the `ApplicationStackBuilder` with the list of rockets', async () => {
        spy(ApplicationStackBuilder.prototype, 'buildOn')

        class EmptyEntity {
          public id: UUID = ''
        }

        const config = new BoosterConfig('test')
        config.userProjectRootPath = '.'
        config.appName = 'testing-app'
        config.entities[EmptyEntity.name] = {
          class: EmptyEntity,
          authorizeReadEvents: [],
        }

        const fakeRocket: InfrastructureRocket = {
          mountStack: fake(),
          unmountStack: fake(),
        }

        assemble(config, [fakeRocket])

        expect(ApplicationStackBuilder.prototype.buildOn).to.have.been.calledWithMatch({}, [fakeRocket])
      })
    })
  })

  describe('the `getStackNames` method', () => {
    const fakeGetStackNames = fake(StackTools.__get__('getStackNames'))

    const config = {
      resourceNames: {
        applicationStack: 'fake-stack',
      },
    }

    fakeGetStackNames(config)

    expect(fakeGetStackNames).to.have.returned([config.resourceNames.applicationStack])
  })

  describe('the `getStackToolkitName` method', () => {
    const fakeGetStackToolkitName = fake(StackTools.__get__('getStackToolkitName'))

    const config = {
      appName: 'fake-app-name',
    }

    fakeGetStackToolkitName(config)

    expect(fakeGetStackToolkitName).to.have.returned(config.appName + '-toolkit')
  })

  describe('the `getStackToolkitBucketName` method', () => {
    const fakeGetStackToolkitBucketName = fake(StackTools.__get__('getStackToolkitBucketName'))

    const config = {
      appName: 'fake-app-name',
    }

    fakeGetStackToolkitBucketName(config)

    expect(fakeGetStackToolkitBucketName).to.have.returned(config.appName + '-toolkit-bucket')
  })

  describe('the `getEnvironments` method', () => {
    it('returns default account and region', async () => {
      const fakeGetEnvironment = fake(StackTools.__get__('getEnvironment'))

      const fakeSDKProvider = {
        defaultAccount: fake.resolves({ accountId: 'default-account' }),
        defaultRegion: 'default-region',
      }

      await expect(fakeGetEnvironment(fakeSDKProvider)).to.eventually.become({
        name: 'Default environment',
        account: 'default-account',
        region: 'default-region',
      })
    })

    it("throws an error if it can't load the default account", async () => {
      const fakeGetEnvironment = fake(StackTools.__get__('getEnvironment'))

      const fakeSDKProvider = {
        defaultAccount: fake.resolves(undefined),
        defaultRegion: 'default-region',
      }

      await expect(fakeGetEnvironment(fakeSDKProvider)).to.eventually.be.rejectedWith(
        /Unable to load default AWS account/
      )
    })

    it("throws an error if it can't load the default region", async () => {
      const fakeGetEnvironment = fake(StackTools.__get__('getEnvironment'))

      const fakeSDKProvider = {
        defaultAccount: fake.resolves({ accountId: 'default-accoung' }),
      }

      await expect(fakeGetEnvironment(fakeSDKProvider)).to.eventually.be.rejectedWith(
        /Unable to determine default region/
      )
    })
  })
})
