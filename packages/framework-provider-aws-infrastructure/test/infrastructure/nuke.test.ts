import { expect } from '@boostercloud/framework-provider-aws-infrastructure/test/expect'
import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import { CdkToolkit } from 'aws-cdk/lib/cdk-toolkit'
import { fake, replace, restore } from 'sinon'
import * as StackServiceConfiguration from '../../src/infrastructure/stack-service-configuration'

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
      const fakeStackServiceConfiguration = { aws: 'here goes the SDK', appStacks: '', cdkToolkit: '' }
      replace(StackServiceConfiguration, 'getStackServiceConfiguration', fake.resolves(fakeStackServiceConfiguration))

      const config = ({ hello: 'world' } as unknown) as BoosterConfig
      const logger = ({
        info: fake(),
      } as unknown) as Logger

      await nukeModule.nuke(config, logger)

      expect(fakeNukeToolkit).to.have.been.calledWithMatch(config, logger, fakeStackServiceConfiguration.aws)

      revertNukeToolkit()
      revertNukeApp()
    })

    it('nukes the application stack', async () => {
      const fakeNukeApplication = fake()
      const revertNukeToolkit = nukeModule.__set__('nukeToolkit', fake())
      const revertNukeApp = nukeModule.__set__('nukeApplication', fakeNukeApplication)
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

      expect(fakeNukeApplication).to.have.been.calledWithMatch(
        fakeStackServiceConfiguration.aws,
        fakeStackServiceConfiguration.appStacks,
        fakeStackServiceConfiguration.cdkToolkit,
        logger
      )

      revertNukeToolkit()
      revertNukeApp()
    })

    xit('calls the plugins unmount method to clean plugin-initialized resources')
    xit('logs progress calling to the passed `logger`')
    xit('logs errors thrown by `getStackServiceConfiguration`')
    xit('logs errors thrown by the toolkit nuke process')
    xit('logs errors thrown by the application nuke process')
  })

  describe('the `nukeToolkit` method', () => {
    xit('empties the toolkit bucket')
    xit('deletes the toolkit stack')
  })

  describe('the `nukeApplication` method', () => {
    xit('destroys the application stack')
  })
})
