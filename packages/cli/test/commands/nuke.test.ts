import { expect } from '../expect'
import { fancy } from 'fancy-test'
import { restore, replace, fake } from 'sinon'
import Prompter from '../../src/services/user-prompt'
import { ProviderLibrary, BoosterConfig } from '@boostercloud/framework-types'
import * as Nuke from '../../src/commands/nuke'
import * as providerService from '../../src/services/provider-service'
import { oraLogger } from '../../src/services/logger'
import { Config } from '@oclif/core'
import { test } from '@oclif/test'
import * as environment from '../../src/services/environment'
import * as configService from '../../src/services/config-service'
import * as projectChecker from '../../src/services/project-checker'
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
        const fakeNuke = fake((config: BoosterConfig) => {
          config.logger?.info('this is a progress update')
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
        .loadConfig({ root: __dirname })
        .stdout()
        .command(['nuke'])
        .it('shows no environment provided error', (ctx) => {
          expect(ctx.stdout).to.match(/No environment set/)
        })
    })
  })

  describe('command class', () => {
    beforeEach(() => {
      const config = new BoosterConfig('fake_environment')
      replace(configService, 'compileProjectAndLoadConfig', fake.resolves(config))
      replace(providerService, 'nukeCloudProviderResources', fake.resolves({}))
      replace(projectChecker, 'checkCurrentDirBoosterVersion', fake.resolves({}))
      replace(oraLogger, 'fail', fake.resolves({}))
      replace(oraLogger, 'info', fake.resolves({}))
      replace(oraLogger, 'start', fake.resolves({}))
      replace(oraLogger, 'succeed', fake.resolves({}))
    })

    it('init calls checkCurrentDirBoosterVersion', async () => {
      await new Nuke.default([], {} as Config).init()
      expect(projectChecker.checkCurrentDirBoosterVersion).to.have.been.called
    })

    it('without flags', async () => {
      await new Nuke.default([], {} as Config).run()

      expect(configService.compileProjectAndLoadConfig).to.have.not.been.called
      expect(providerService.nukeCloudProviderResources).to.have.not.been.called
      expect(oraLogger.fail).to.have.been.calledWithMatch(/No environment set/)
    })

    it('with -e flag incomplete', async () => {
      let exceptionThrown = false
      let exceptionMessage = ''
      try {
        await new Nuke.default(['-e'], {} as Config).run()
      } catch (e) {
        exceptionThrown = true
        exceptionMessage = e.message
      }
      expect(exceptionThrown).to.be.equal(true)
      expect(exceptionMessage).to.to.contain('--environment expects a value')
      expect(configService.compileProjectAndLoadConfig).to.have.not.been.called
      expect(providerService.nukeCloudProviderResources).to.have.not.been.called
    })

    it('with --environment flag incomplete', async () => {
      let exceptionThrown = false
      let exceptionMessage = ''
      try {
        await new Nuke.default(['--environment'], {} as Config).run()
      } catch (e) {
        exceptionThrown = true
        exceptionMessage = e.message
      }
      expect(exceptionThrown).to.be.equal(true)
      expect(exceptionMessage).to.to.contain('--environment expects a value')
      expect(configService.compileProjectAndLoadConfig).to.have.not.been.called
      expect(providerService.nukeCloudProviderResources).to.have.not.been.called
    })

    describe('inside a booster project', () => {
      it('entering correct environment and application name', async () => {
        replace(Prompter.prototype, 'defaultOrPrompt', fake.resolves('new-booster-app'))
        await new Nuke.default(['-e', 'fake_environment'], {} as Config).run()

        expect(configService.compileProjectAndLoadConfig).to.have.been.called
        expect(providerService.nukeCloudProviderResources).to.have.been.called
        expect(oraLogger.info).to.have.been.calledWithMatch('Removal complete!')
      })

      it('entering correct environment and --force flag', async () => {
        await new Nuke.default(['-e', 'fake_environment', '--force'], {} as Config).run()

        expect(configService.compileProjectAndLoadConfig).to.have.been.called
        expect(providerService.nukeCloudProviderResources).to.have.been.called
        expect(oraLogger.info).to.have.been.calledWithMatch('Removal complete!')
      })

      it('entering correct environment and -f flag', async () => {
        await new Nuke.default(['-e', 'fake_environment', '-f'], {} as Config).run()

        expect(configService.compileProjectAndLoadConfig).to.have.been.called
        expect(providerService.nukeCloudProviderResources).to.have.been.called
        expect(oraLogger.info).to.have.been.calledWithMatch('Removal complete!')
      })

      it('entering correct environment but a wrong application name', async () => {
        replace(Prompter.prototype, 'defaultOrPrompt', fake.resolves('fake app 2'))
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new Nuke.default(['-e', 'fake_environment'], {} as Config).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('Wrong app name, stopping nuke!')
        expect(configService.compileProjectAndLoadConfig).to.have.been.called
        expect(providerService.nukeCloudProviderResources).to.have.not.been.called
        expect(oraLogger.info).to.have.not.been.calledWithMatch('Removal complete!')
      })

      it('entering correct environment and nonexisting flag', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new Nuke.default(['-e', 'fake_environment', '--nonexistingoption'], {} as Config).run()
        } catch (e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('Nonexistent flag: --nonexistingoption')
        expect(providerService.nukeCloudProviderResources).to.have.not.been.called
        expect(oraLogger.info).to.have.not.been.calledWithMatch('Removal complete!')
      })

      it('without defining environment and --force', async () => {
        await new Nuke.default(['--force'], {} as Config).run()

        expect(providerService.nukeCloudProviderResources).to.have.not.been.called
        expect(oraLogger.fail).to.have.been.calledWithMatch(/No environment set/)
      })
    })
  })
})
