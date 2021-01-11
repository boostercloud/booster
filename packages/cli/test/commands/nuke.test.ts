/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { expect } from '../expect'
import { fancy } from 'fancy-test'
import { restore, replace, fake } from 'sinon'
import Prompter from '../../src/services/user-prompt'
import { ProviderLibrary, Logger } from '@boostercloud/framework-types'
import * as Nuke from '../../src/commands/nuke'
import * as providerService from '../../src/services/provider-service'
import { oraLogger } from '../../src/services/logger'
import { IConfig } from '@oclif/config'
import * as childProcessPromise from 'child-process-promise'
import * as path from 'path'
import { test } from '@oclif/test'
import * as environment from '../../src/services/environment'

const rewire = require('rewire')
const nuke = rewire('../../src/commands/nuke')
const runTasks = nuke.__get__('runTasks')
const loader = nuke.__get__('askToConfirmRemoval')

describe('nuke', () => {
  beforeEach(() => {
    delete process.env.BOOSTER_ENV
  })

  afterEach(() => {
    restore()
  })

  describe('runTasks function', () => {
    context('when an unexpected problem happens', () => {
      fancy.stdout().it('fails gracefully showing the error message', async () => {
        const msg = 'weird exception'
        const fakeLoader = Promise.reject(new Error(msg))
        const fakeNuke = fake()
        replace(environment, 'currentEnvironment', fake.returns('test-env'))

        await expect(runTasks(fakeLoader, fakeNuke)).to.eventually.be.rejectedWith(msg)
        expect(fakeNuke).not.to.have.been.called
      })
    })

    context('when a wrong application name is provided', () => {
      fancy.stdout().it('fails gracefully showing the error message', async () => {
        const fakeProvider = {} as ProviderLibrary

        const fakeConfig = Promise.resolve({
          provider: fakeProvider,
          appName: 'fake app',
          region: 'tunte',
          entities: {},
        })

        const prompter = new Prompter()
        const fakePrompter = fake.resolves('fake app 2') // The user entered wrong app name
        replace(prompter, 'defaultOrPrompt', fakePrompter)
        const fakeNuke = fake()
        const errorMsg = 'Wrong app name, stopping nuke!'

        replace(environment, 'currentEnvironment', fake.returns('test-env'))

        await expect(runTasks(loader(prompter, false, fakeConfig), fakeNuke)).to.eventually.be.rejectedWith(errorMsg)
        expect(fakeNuke).not.to.have.been.called
      })
    })

    context('when the --force flag is provided', () => {
      fancy.stdout().it('continues without asking for the application name', async () => {
        const fakeProvider = {} as ProviderLibrary

        const fakeConfig = Promise.resolve({
          provider: fakeProvider,
          appName: 'fake app',
          region: 'tunte',
          entities: {},
        })

        const prompter = new Prompter()
        const fakePrompter = fake.resolves('fake app 2') // The user entered wrong app name
        replace(prompter, 'defaultOrPrompt', fakePrompter)
        const fakeNuke = fake()

        replace(environment, 'currentEnvironment', fake.returns('test-env'))

        await expect(runTasks(loader(prompter, true, fakeConfig), fakeNuke)).to.eventually.be.fulfilled
        expect(prompter.defaultOrPrompt).not.to.have.been.called
        expect(fakeNuke).to.have.been.calledOnce
      })
    })

    context('when a valid application name is provided', () => {
      fancy.stdout().it('starts removal', async (ctx) => {
        const fakeProvider = {} as ProviderLibrary

        const fakeConfig = Promise.resolve({
          provider: fakeProvider,
          appName: 'fake app',
          region: 'tunte',
          entities: {},
        })

        const prompter = new Prompter()
        const fakePrompter = fake.resolves('fake app')
        replace(prompter, 'defaultOrPrompt', fakePrompter)
        const fakeNuke = fake((_config: unknown, logger: Logger) => {
          logger.info('this is a progress update')
        })

        replace(environment, 'currentEnvironment', fake.returns('test-env'))

        await runTasks(loader(prompter, false, fakeConfig), fakeNuke)

        expect(ctx.stdout).to.include('Removal complete!')
        expect(fakeNuke).to.have.been.calledOnce
      })
    })
  })

  describe('run', () => {
    context('when no environment provided', async () => {
      test
        .stdout()
        .command(['nuke'])
        .it('shows no environment provided error', (ctx) => {
          expect(ctx.stdout).to.match(/No environment set/)
        })
    })
  })

  describe('command class', () => {
    beforeEach(() => {
        replace(childProcessPromise, 'exec', fake.resolves({}))
        replace(providerService,'nukeCloudProviderResources', fake.resolves({}))
        replace(oraLogger,'fail', fake.resolves({}))
        replace(oraLogger, 'info', fake.resolves({}))
        replace(oraLogger, 'start', fake.resolves({}))
        replace(oraLogger, 'succeed', fake.resolves({}))
    })

    it('without flags', async () => {
      await new Nuke.default([], {} as IConfig).run()

      expect(childProcessPromise.exec).to.have.not.been.calledWithMatch('npx yarn clean && npx yarn compile')
      expect(providerService.nukeCloudProviderResources).to.have.not.been.calledWith()
      expect(oraLogger.fail).to.have.been.calledWithMatch('Error: No environment set. Use the flag `-e` or set the environment variable BOOSTER_ENV to set it before running this command. Example usage: `boost deploy -e <environment>`.')
    })

    it('with -e flag incomplete', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new Nuke.default(['-e'], {} as IConfig).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.be.equal('Flag --environment expects a value')
        expect(childProcessPromise.exec).to.have.not.been.calledWithMatch('npx yarn clean && npx yarn compile')
        expect(providerService.nukeCloudProviderResources).to.have.not.been.calledWith()
    })

    it('with --environment flag incomplete', async () => {
      let exceptionThrown = false
      let exceptionMessage = ''
      try {
        await new Nuke.default(['--environment'], {} as IConfig).run()
      } catch (e) {
        exceptionThrown = true
        exceptionMessage = e.message
      }
      expect(exceptionThrown).to.be.equal(true)
      expect(exceptionMessage).to.be.equal('Flag --environment expects a value')
      expect(childProcessPromise.exec).to.have.not.been.calledWithMatch('npx yarn clean && npx yarn compile')
      expect(providerService.nukeCloudProviderResources).to.have.not.been.calledWith()
    })

    it('outside a booster project', async () => {
      let exceptionThrown = false
      let exceptionMessage = ''
      try {
        await new Nuke.default(['--environment','fake_environment'], {} as IConfig).run()
      } catch (e) {
        exceptionThrown = true
        exceptionMessage = e.message
      }
      expect(exceptionThrown).to.be.equal(true)
      expect(exceptionMessage).to.contain('Error: There was an error when recognizing the application. Make sure you are in the root path of a Booster project')
      expect(childProcessPromise.exec).to.have.not.been.calledWithMatch('npx yarn clean && npx yarn compile')
      expect(providerService.nukeCloudProviderResources).to.have.not.been.calledWith()
    })

    describe('inside a booster project', () => {

      beforeEach(() => {
        replace(process,'cwd', fake.returns(path.join(process.cwd(),'resources', 'mock_project')))
      })

      it('entering correct environment and application name', async () => {
        replace(Prompter.prototype,'defaultOrPrompt', fake.resolves('boosted-blog-fake'))
        await new Nuke.default(['-e','fake_environment'], {} as IConfig).run()
  
        expect(childProcessPromise.exec).to.have.been.calledWithMatch('npx yarn clean && npx yarn compile')
        expect(providerService.nukeCloudProviderResources).to.have.been.calledWith()
        expect(oraLogger.info).to.have.been.calledWithMatch('Removal complete!')
      })

      it('entering correct environment and --force flag', async () => {
        await new Nuke.default(['-e','fake_environment','--force'], {} as IConfig).run()
  
        expect(childProcessPromise.exec).to.have.been.calledWithMatch('npx yarn clean && npx yarn compile')
        expect(providerService.nukeCloudProviderResources).to.have.been.calledWith()
        expect(oraLogger.info).to.have.been.calledWithMatch('Removal complete!')
      })

      it('entering correct environment and -f flag', async () => {
        await new Nuke.default(['-e','fake_environment','-f'], {} as IConfig).run()
  
        expect(childProcessPromise.exec).to.have.been.calledWithMatch('npx yarn clean && npx yarn compile')
        expect(providerService.nukeCloudProviderResources).to.have.been.calledWith()
        expect(oraLogger.info).to.have.been.calledWithMatch('Removal complete!')
      })

      it('entering correct environment but a wrong application name', async () => {
        replace(Prompter.prototype,'defaultOrPrompt', fake.resolves('fake app 2'))
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new Nuke.default(['-e','fake_environment'], {} as IConfig).run()
        } catch(e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('Wrong app name, stopping nuke!')
        expect(childProcessPromise.exec).to.have.been.calledWithMatch('npx yarn clean && npx yarn compile')
        expect(providerService.nukeCloudProviderResources).to.have.not.been.calledWith()
        expect(oraLogger.info).to.have.not.been.calledWithMatch('Removal complete!')
      })

      it('entering nonexisting environment', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new Nuke.default(['-e','nonexisting_environment'], {} as IConfig).run()
        } catch(e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('The environment \'nonexisting_environment\' does not match any of the environments you used to configure your Booster project')
        expect(childProcessPromise.exec).to.have.been.calledWithMatch('npx yarn clean && npx yarn compile')
        expect(providerService.nukeCloudProviderResources).to.have.not.been.calledWith()
        expect(oraLogger.info).to.have.not.been.calledWithMatch('Removal complete!')
      })

      it('entering nonexisting environment and --force flag', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new Nuke.default(['-e','nonexisting_environment','--force'], {} as IConfig).run()
        } catch(e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('The environment \'nonexisting_environment\' does not match any of the environments you used to configure your Booster project')
        expect(childProcessPromise.exec).to.have.been.calledWithMatch('npx yarn clean && npx yarn compile')
        expect(providerService.nukeCloudProviderResources).to.have.not.been.calledWith()
        expect(oraLogger.info).to.have.not.been.calledWithMatch('Removal complete!')
      })

      it('without defining environment', async () => {
        await new Nuke.default([], {} as IConfig).run()
  
        expect(childProcessPromise.exec).to.have.not.been.calledWithMatch('npx yarn clean && npx yarn compile')
        expect(providerService.nukeCloudProviderResources).to.have.not.been.calledWith()
        expect(oraLogger.fail).to.have.been.calledWithMatch('Error: No environment set. Use the flag `-e` or set the environment variable BOOSTER_ENV to set it before running this command. Example usage: `boost deploy -e <environment>`.')
      })

      it('without defining environment and --force', async () => {
        await new Nuke.default(['--force'], {} as IConfig).run()
  
        expect(childProcessPromise.exec).to.have.not.been.calledWithMatch('npx yarn clean && npx yarn compile')
        expect(providerService.nukeCloudProviderResources).to.have.not.been.calledWith()
        expect(oraLogger.fail).to.have.been.calledWithMatch('Error: No environment set. Use the flag `-e` or set the environment variable BOOSTER_ENV to set it before running this command. Example usage: `boost deploy -e <environment>`.')
      })
    })
  })
})
