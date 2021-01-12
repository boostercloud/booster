import { expect } from '../expect'
import { restore, fake, replace } from 'sinon'
import rewire = require('rewire')
import { ProviderLibrary } from '@boostercloud/framework-types'
import * as Start from '../../src/commands/start'
import * as providerService from '../../src/services/provider-service'
import { oraLogger } from '../../src/services/logger'
import { IConfig } from '@oclif/config'
import * as childProcessPromise from 'child-process-promise'
import * as path from 'path'
import { test } from '@oclif/test'
import * as environment from '../../src/services/environment'

const start = rewire('../../src/commands/start')
const runTasks = start.__get__('runTasks')

describe('start', () => {

  beforeEach(() => {
    delete process.env.BOOSTER_ENV
  })

  afterEach(() => {
    restore()
  })

  describe('runTasks function', () => {
    it('calls the runner for the local server', async () => {
      const fakeProvider = {} as ProviderLibrary
      const fakeConfig = {
        provider: fakeProvider,
        appName: 'fake-app',
      }

      const fakeLoader = fake.resolves(fakeConfig)
      const fakeRunner = fake()
      replace(environment, 'currentEnvironment', fake.returns('test-env'))

      await runTasks(3000, fakeLoader, fakeRunner)

      expect(fakeRunner).to.have.been.calledOnce
    })
  })

  describe('run', () => {
    context('when no environment provided', async () => {
      test
        .stdout()
        .command(['start'])
        .it('shows no environment provided error', (ctx) => {
          expect(ctx.stdout).to.match(/No environment set/)
        })
    })
  })

  describe('deploy class', () => {
    beforeEach(() => {
      replace(childProcessPromise, 'exec', fake.resolves({}))
      replace(providerService,'startProvider', fake.resolves({}))
      replace(oraLogger,'fail', fake.resolves({}))
      replace(oraLogger, 'info', fake.resolves({}))
      replace(oraLogger, 'start', fake.resolves({}))
      replace(oraLogger, 'succeed', fake.resolves({}))
    })

    it('without flags', async () => {
      await new Start.default([], {} as IConfig).run()

      expect(childProcessPromise.exec).to.have.not.been.calledWithMatch('npx yarn clean && npx yarn compile')
      expect(providerService.startProvider).to.have.not.been.calledWith()
      expect(oraLogger.fail).to.have.been.calledWithMatch('Error: No environment set. Use the flag `-e` or set the environment variable BOOSTER_ENV to set it before running this command. Example usage: `boost deploy -e <environment>`.')
    })

    it('with -e flag incomplete', async () => {
      let exceptionThrown = false
      let exceptionMessage = ''
      try {
        await new Start.default(['-e'], {} as IConfig).run()
      } catch (e) {
        exceptionThrown = true
        exceptionMessage = e.message
      }
      expect(exceptionThrown).to.be.equal(true)
      expect(exceptionMessage).to.be.equal('Flag --environment expects a value')
      expect(childProcessPromise.exec).to.have.not.been.calledWithMatch('npx yarn clean && npx yarn compile')
      expect(providerService.startProvider).to.have.not.been.calledWith()
    })

    it('with --environment flag incomplete', async () => {
      let exceptionThrown = false
      let exceptionMessage = ''
      try {
        await new Start.default(['--environment'], {} as IConfig).run()
      } catch (e) {
        exceptionThrown = true
        exceptionMessage = e.message
      }
      expect(exceptionThrown).to.be.equal(true)
      expect(exceptionMessage).to.be.equal('Flag --environment expects a value')
      expect(childProcessPromise.exec).to.have.not.been.calledWithMatch('npx yarn clean && npx yarn compile')
      expect(providerService.startProvider).to.have.not.been.calledWith()
    })

    it('outside a booster project', async () => {
      let exceptionThrown = false
      let exceptionMessage = ''
      try {
        await new Start.default(['--environment','fake_environment'], {} as IConfig).run()
      } catch (e) {
        exceptionThrown = true
        exceptionMessage = e.message
      }
      expect(exceptionThrown).to.be.equal(true)
      expect(exceptionMessage).to.contain('Error: There was an error when recognizing the application. Make sure you are in the root path of a Booster project')
      expect(childProcessPromise.exec).to.have.not.been.calledWithMatch('npx yarn clean && npx yarn compile')
      expect(providerService.startProvider).to.have.not.been.calledWith()
    })

    describe('inside a booster project', () => {

      beforeEach(() => {
        replace(process,'cwd', fake.returns(path.join(process.cwd(),'resources', 'mock_project')))
      })

      it('entering correct environment', async () => {
        await new Start.default(['-e','fake_environment'], {} as IConfig).run()
  
        expect(childProcessPromise.exec).to.have.not.been.calledWithMatch('npx yarn install --production --no-bin-links')
        expect(childProcessPromise.exec).to.have.been.calledWithMatch('npx yarn clean && npx yarn compile')
        expect(providerService.startProvider).to.have.been.calledWith()
        expect(oraLogger.start).to.have.been.calledWithMatch('Starting debug server on port')
      })

      it('entering correct environment and --port flag', async () => {
        await new Start.default(['-e','fake_environment','--port','5000'], {} as IConfig).run()
  
        expect(childProcessPromise.exec).to.have.not.been.calledWithMatch('npx yarn install --production --no-bin-links')
        expect(childProcessPromise.exec).to.have.been.calledWithMatch('npx yarn clean && npx yarn compile')
        expect(providerService.startProvider).to.have.been.calledWith()
        expect(oraLogger.start).to.have.been.calledWithMatch('Starting debug server on port 5000')
      })

      it('entering correct environment and -p flag', async () => {
        await new Start.default(['-e','fake_environment','-p','5000'], {} as IConfig).run()
  
        expect(childProcessPromise.exec).to.have.not.been.calledWithMatch('npx yarn install --production --no-bin-links')
        expect(childProcessPromise.exec).to.have.been.calledWithMatch('npx yarn clean && npx yarn compile')
        expect(providerService.startProvider).to.have.been.calledWith()
        expect(oraLogger.start).to.have.been.calledWithMatch('Starting debug server on port 5000')
      })

      it('entering correct environment and nonexisting flag', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new Start.default(['-e','fake_environment','--nonexistingoption'], {} as IConfig).run()
        } catch(e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('Unexpected argument: --nonexistingoption')
        expect(childProcessPromise.exec).to.have.not.been.calledWithMatch('npx yarn install --production --no-bin-links')
        expect(childProcessPromise.exec).to.have.not.been.calledWithMatch('npx yarn clean && npx yarn compile')
        expect(providerService.startProvider).to.have.not.been.calledWith()
        expect(oraLogger.start).to.have.not.been.calledWithMatch('Starting debug server on port')
      })

      it('entering correct environment and --port with incomplete port number', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new Start.default(['-e','fake_environment','--port'], {} as IConfig).run()
        } catch(e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('Flag --port expects a value')
        expect(childProcessPromise.exec).to.have.not.been.calledWithMatch('npx yarn clean && npx yarn compile')
        expect(providerService.startProvider).to.have.not.been.calledWith()
        expect(oraLogger.start).to.have.not.been.calledWithMatch('Starting debug server on port')
      })

      it('entering correct environment and -p with incomplete port number', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new Start.default(['-e','fake_environment','-p'], {} as IConfig).run()
        } catch(e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('Flag --port expects a value')
        expect(childProcessPromise.exec).to.have.not.been.calledWithMatch('npx yarn clean && npx yarn compile')
        expect(providerService.startProvider).to.have.not.been.calledWith()
        expect(oraLogger.start).to.have.not.been.calledWithMatch('Starting debug server on port')
      })

      it('entering nonexisting environment', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new Start.default(['-e','nonexisting_environment'], {} as IConfig).run()
        } catch(e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('The environment \'nonexisting_environment\' does not match any of the environments you used to configure your Booster project')
        expect(childProcessPromise.exec).to.have.been.calledWithMatch('npx yarn clean && npx yarn compile')
        expect(providerService.startProvider).to.have.not.been.calledWith()
        expect(oraLogger.start).to.have.not.been.calledWithMatch('Starting debug server on port')
      })

      it('entering nonexisting environment and -p flag', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new Start.default(['-e','nonexisting_environment','-p','5000'], {} as IConfig).run()
        } catch(e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('The environment \'nonexisting_environment\' does not match any of the environments you used to configure your Booster project')
        expect(childProcessPromise.exec).to.have.been.calledWithMatch('npx yarn clean && npx yarn compile')
        expect(providerService.startProvider).to.have.not.been.calledWith()
        expect(oraLogger.start).to.have.not.been.calledWithMatch('Starting debug server on port')
      })

      it('without defining environment', async () => {
        await new Start.default([], {} as IConfig).run()
  
        expect(childProcessPromise.exec).to.have.not.been.calledWithMatch('npx yarn clean && npx yarn compile')
        expect(providerService.startProvider).to.have.not.been.calledWith()
        expect(oraLogger.fail).to.have.been.calledWithMatch('Error: No environment set. Use the flag `-e` or set the environment variable BOOSTER_ENV to set it before running this command. Example usage: `boost deploy -e <environment>`.')
      })

      it('without defining environment and -p', async () => {
        await new Start.default(['-p','5000'], {} as IConfig).run()
  
        expect(childProcessPromise.exec).to.have.not.been.calledWithMatch('npx yarn clean && npx yarn compile')
        expect(providerService.startProvider).to.have.not.been.calledWith()
        expect(oraLogger.fail).to.have.been.calledWithMatch('Error: No environment set. Use the flag `-e` or set the environment variable BOOSTER_ENV to set it before running this command. Example usage: `boost deploy -e <environment>`.')
      })
    })
  })
})
