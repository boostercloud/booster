import { StreamViewType } from '@aws-cdk/aws-dynamodb'
import { InfrastructurePlugin } from '../../src/infrastructure-plugin'
import { BoosterConfig, UUID } from '@boostercloud/framework-types'
import { fake, restore, spy } from 'sinon'
import { expect } from '../expect'
import { ApplicationStackBuilder } from '../../src/infrastructure/stacks/application-stack'

const rewire = require('rewire')
const stackServiceConfigurationModule = rewire('../../src/infrastructure/stack-service-configuration')

describe('the `stack-service-configuration` module', () => {
  afterEach(() => {
    restore()
  })

  describe('the `getStackServiceConfiguration` method', () => {
    it('builds the configuration using the `assemble` method', async () => {
      const fakeAssemble = fake()
      const revertRewire = stackServiceConfigurationModule.__set__('assemble', fakeAssemble)

      const config = {} as BoosterConfig

      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const stackConfig = (await stackServiceConfigurationModule.getStackServiceConfiguration(config)) as any
      const appStacks = stackConfig.appStacks.props
      await appStacks.synthesizer(appStacks.aws, appStacks.configuration)

      expect(fakeAssemble).to.have.been.calledWithMatch(config)
      revertRewire()
    })

    context('with plugins', () => {
      it('forwards the plugin list to the `assemble` method for initialization', async () => {
        const fakeAssemble = fake()
        const revertRewire = stackServiceConfigurationModule.__set__('assemble', fakeAssemble)

        const config = {} as BoosterConfig

        const fakePlugin: InfrastructurePlugin = {
          mountStack: fake(),
        }

        const stackConfig = (await stackServiceConfigurationModule.getStackServiceConfiguration(config, [
          fakePlugin,
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
        ])) as any
        const appStacks = stackConfig.appStacks.props
        await appStacks.synthesizer(appStacks.aws, appStacks.configuration)

        expect(fakeAssemble).to.have.been.calledWithMatch(config, [fakePlugin])
        revertRewire()
      })
    })
  })

  describe('the `assemble` method', () => {
    const assemble = stackServiceConfigurationModule.__get__('assemble')

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

    context('with plugins', () => {
      it('initializes the `ApplicationStackBuilder` with the list of plugins', async () => {
        spy(ApplicationStackBuilder.prototype, 'buildOn')

        class EmptyEntity {
          public id: UUID = ''
        }

        const config = new BoosterConfig('test')
        config.appName = 'testing-app'
        config.entities[EmptyEntity.name] = {
          class: EmptyEntity,
        }

        const fakePlugin: InfrastructurePlugin = {
          mountStack: fake(),
        }

        assemble(config, [fakePlugin])

        expect(ApplicationStackBuilder.prototype.buildOn).to.have.been.calledWithMatch({}, [fakePlugin])
      })
    })
  })
})
