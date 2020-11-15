import { expect } from '../expect'
import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import { CdkToolkit } from 'aws-cdk/lib/cdk-toolkit'
import { fake, replace, restore } from 'sinon'
import * as StackServiceConfiguration from '../../src/infrastructure/stack-tools'
import * as S3Tools from '../../src/infrastructure/s3utils'
import * as rocketUtils from '../../src/rockets/rocket-utils'

const rewire = require('rewire')
const nukeModule = rewire('../../src/infrastructure/nuke')

describe('the nuke module', () => {
  afterEach(() => {
    restore()
  })

  describe('the `nuke` method', () => {
    it('calls to `getStackServiceConfiguration` to get the stack configuration', async () => {
      const revertNukeToolkit = nukeModule.__set__('nukeToolkit', fake())
      const revertNukeApp = nukeModule.__set__('nukeApplication', fake())
      replace(
        StackServiceConfiguration,
        'getStackServiceConfiguration',
        fake.resolves({ aws: '', appStacks: '', cdkToolkit: '' })
      )
      replace(CdkToolkit.prototype, 'destroy', fake())

      const config = ({ hello: 'world' } as unknown) as BoosterConfig
      const logger = ({
        info: fake(),
        error: console.error,
      } as unknown) as Logger

      await nukeModule.nuke(config, logger)

      expect(StackServiceConfiguration.getStackServiceConfiguration).to.have.been.calledWithMatch(config)

      revertNukeToolkit()
      revertNukeApp()
    })

    it('nukes the toolkit stack', async () => {
      const fakeNukeToolkit = fake()
      const revertNukeToolkit = nukeModule.__set__('nukeToolkit', fakeNukeToolkit)
      const revertNukeApp = nukeModule.__set__('nukeApplication', fake())
      const fakeStackServiceConfiguration = { sdk: 'here goes the SDK', appStacks: '', cdkToolkit: '' }
      replace(StackServiceConfiguration, 'getStackServiceConfiguration', fake.resolves(fakeStackServiceConfiguration))

      const config = ({ hello: 'world' } as unknown) as BoosterConfig
      const logger = ({
        info: fake(),
      } as unknown) as Logger

      await nukeModule.nuke(config, logger)

      expect(fakeNukeToolkit).to.have.been.calledWithMatch(logger, config, fakeStackServiceConfiguration.sdk)

      revertNukeToolkit()
      revertNukeApp()
    })

    it('nukes the application stack', async () => {
      const fakeNukeApplication = fake()
      const revertNukeToolkit = nukeModule.__set__('nukeToolkit', fake())
      const revertNukeApp = nukeModule.__set__('nukeApplication', fakeNukeApplication)
      const fakeStackServiceConfiguration = {
        cdkToolkit: 'and here the cdkToolkit',
      }
      replace(StackServiceConfiguration, 'getStackServiceConfiguration', fake.resolves(fakeStackServiceConfiguration))

      const config = ({ hello: 'world' } as unknown) as BoosterConfig
      const logger = ({
        info: fake(),
      } as unknown) as Logger

      await nukeModule.nuke(config, logger)

      expect(fakeNukeApplication).to.have.been.calledWithMatch(logger, config, fakeStackServiceConfiguration.cdkToolkit)

      revertNukeToolkit()
      revertNukeApp()
    })

    it('logs progress calling to the passed `logger`', async () => {
      const revertNukeToolkit = nukeModule.__set__('nukeToolkit', fake())
      const revertNukeApp = nukeModule.__set__('nukeApplication', fake())
      const fakeStackServiceConfiguration = {
        aws: 'here goes the SDK',
        appStacks: 'and here the appStacks',
        cdkToolkit: 'and here the cdkToolkit',
      }
      replace(StackServiceConfiguration, 'getStackServiceConfiguration', fake.resolves(fakeStackServiceConfiguration))

      const config = ({ hello: 'world' } as unknown) as BoosterConfig
      const logger = ({
        info: fake(),
      } as unknown) as Logger

      await nukeModule.nuke(config, logger)

      expect(logger.info).to.have.been.calledWithMatch(/Destroying application/)

      revertNukeToolkit()
      revertNukeApp()
    })

    it('logs errors thrown by `getStackServiceConfiguration`', async () => {
      const revertNukeToolkit = nukeModule.__set__('nukeToolkit', fake())
      const revertNukeApp = nukeModule.__set__('nukeApplication', fake())
      const error = new Error('things gone bad')
      replace(StackServiceConfiguration, 'getStackServiceConfiguration', fake.rejects(error))
      replace(CdkToolkit.prototype, 'destroy', fake())

      const config = ({ hello: 'world' } as unknown) as BoosterConfig
      const logger = ({
        info: fake(),
        error: console.error,
      } as unknown) as Logger

      await expect(nukeModule.nuke(config, logger)).to.be.eventually.rejectedWith(error)

      revertNukeToolkit()
      revertNukeApp()
    })

    it('logs errors thrown by the toolkit nuke process', async () => {
      const error = new Error('things gone bad')
      const revertNukeToolkit = nukeModule.__set__('nukeToolkit', fake.rejects(error))
      const revertNukeApp = nukeModule.__set__('nukeApplication', fake())
      const fakeStackServiceConfiguration = {
        aws: 'here goes the SDK',
        appStacks: 'and here the appStacks',
        cdkToolkit: 'and here the cdkToolkit',
      }
      replace(StackServiceConfiguration, 'getStackServiceConfiguration', fake.resolves(fakeStackServiceConfiguration))
      replace(CdkToolkit.prototype, 'destroy', fake())

      const config = ({ hello: 'world' } as unknown) as BoosterConfig
      const logger = ({
        info: fake(),
        error: console.error,
      } as unknown) as Logger

      await expect(nukeModule.nuke(config, logger)).to.be.eventually.rejectedWith(error)

      revertNukeToolkit()
      revertNukeApp()
    })

    it('logs errors thrown by the application nuke process', async () => {
      const error = new Error('things gone bad')
      const revertNukeToolkit = nukeModule.__set__('nukeToolkit', fake())
      const revertNukeApp = nukeModule.__set__('nukeApplication', fake.rejects(error))
      const fakeStackServiceConfiguration = {
        aws: 'here goes the SDK',
        appStacks: 'and here the appStacks',
        cdkToolkit: 'and here the cdkToolkit',
      }
      replace(StackServiceConfiguration, 'getStackServiceConfiguration', fake.resolves(fakeStackServiceConfiguration))
      replace(CdkToolkit.prototype, 'destroy', fake())

      const config = ({ hello: 'world' } as unknown) as BoosterConfig
      const logger = ({
        info: fake(),
        error: console.error,
      } as unknown) as Logger

      await expect(nukeModule.nuke(config, logger)).to.be.eventually.rejectedWith(error)

      revertNukeToolkit()
      revertNukeApp()
    })

    context('with rockets', () => {
      it('cleans rocket-initialized resources', async () => {
        const fakeNukeRockets = fake()
        const revertNukeToolkit = nukeModule.__set__('nukeToolkit', fake())
        const revertNukeApp = nukeModule.__set__('nukeApplication', fake())
        const revertNukeRockets = nukeModule.__set__('nukeRockets', fakeNukeRockets)
        const fakeStackServiceConfiguration = {
          sdk: 'here goes the SDK',
          appStacks: 'and here the appStacks',
          cdkToolkit: 'and here the cdkToolkit',
        }
        replace(StackServiceConfiguration, 'getStackServiceConfiguration', fake.resolves(fakeStackServiceConfiguration))

        const config = ({ hello: 'world' } as unknown) as BoosterConfig
        const logger = ({
          info: fake(),
        } as unknown) as Logger

        const fakeRockets = [
          {
            mountStack: fake(),
            unmountStack: fake(),
          },
        ]

        await nukeModule.nuke(config, logger, fakeRockets)

        expect(fakeNukeRockets).to.have.been.calledWithMatch(logger, fakeStackServiceConfiguration.sdk, fakeRockets)

        revertNukeToolkit()
        revertNukeApp()
        revertNukeRockets()
      })
    })
  })

  describe('the `nukeToolkit` method', () => {
    it('empties the toolkit bucket', async () => {
      const nukeToolkit = nukeModule.__get__('nukeToolkit')
      replace(S3Tools, 'emptyS3Bucket', fake())

      const config = ({ appName: 'test-app' } as unknown) as BoosterConfig
      const logger = ({
        info: fake(),
      } as unknown) as Logger

      const fakeCloudformation = { deleteStack: fake.returns({ promise: fake.resolves(true) }) }
      const fakeSDK = {
        cloudFormation: fake.returns(fakeCloudformation),
      }

      await nukeToolkit(logger, config, fakeSDK)

      expect(S3Tools.emptyS3Bucket).to.have.been.calledWithMatch(fakeSDK, logger, 'test-app-toolkit-bucket')
    })

    it('deletes the toolkit stack', async () => {
      const nukeToolkit = nukeModule.__get__('nukeToolkit')
      replace(S3Tools, 'emptyS3Bucket', fake())

      const config = ({ appName: 'test-app' } as unknown) as BoosterConfig
      const logger = ({
        info: fake(),
      } as unknown) as Logger

      const fakeCloudformation = { deleteStack: fake.returns({ promise: fake.resolves(true) }) }
      const fakeSDK = {
        cloudFormation: fake.returns(fakeCloudformation),
      }

      await nukeToolkit(logger, config, fakeSDK)

      expect(fakeSDK.cloudFormation).to.have.been.called
      expect(fakeCloudformation.deleteStack).to.have.been.calledWithMatch({
        StackName: 'test-app-toolkit',
      })
    })
  })

  describe('the `nukeRockets` method', () => {
    it('builds a rocketUtils object and passes it to all the rockets', async () => {
      const nukeRockets = nukeModule.__get__('nukeRockets')

      const fakeRocketUtils = { rocket: 'utils' }
      const fakeBuildRocketUtils = fake.returns(fakeRocketUtils)
      replace(rocketUtils, 'buildRocketUtils', fakeBuildRocketUtils)

      const logger = ({
        info: fake(),
      } as unknown) as Logger

      const fakeSDK = { fake: 'aws' }

      const fakeUnmountStack = fake()
      const fakeRockets = [
        {
          unmountStack: fakeUnmountStack,
        },
      ]

      await nukeRockets(logger, fakeSDK, fakeRockets)

      expect(fakeBuildRocketUtils).to.have.been.calledWithMatch(fakeSDK, logger)
      expect(fakeUnmountStack).to.have.been.calledWithMatch(fakeRocketUtils)
    })
  })

  describe('the `nukeApplication` method', () => {
    it('destroys the application stack', async () => {
      const nukeApplication = nukeModule.__get__('nukeApplication')

      const config = ({
        appName: 'test-app',
        resourceNames: {
          applicationStack: 'stack-name',
        },
      } as unknown) as BoosterConfig

      const cdkToolkit = {
        destroy: fake(),
      }

      const logger = ({
        info: fake(),
      } as unknown) as Logger

      await nukeApplication(logger, config, cdkToolkit)

      expect(cdkToolkit.destroy).to.have.been.calledWithMatch({
        stackNames: ['stack-name'],
        exclusively: false,
        force: true,
      })
    })
  })
})
