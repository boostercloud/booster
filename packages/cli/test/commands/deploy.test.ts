/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { expect } from '../expect'
import { fancy } from 'fancy-test'
import { restore, fake, replace } from 'sinon'
import { ProviderLibrary, Logger, BoosterConfig } from '@boostercloud/framework-types'
import { test } from '@oclif/test'
import * as Deploy from '../../src/commands/deploy'
import * as providerService from '../../src/services/provider-service'
import { oraLogger } from '../../src/services/logger'
import { IConfig } from '@oclif/config'
import * as environment from '../../src/services/environment'
import * as dependencies from '../../src/services/dependencies'
import * as configService from '../../src/services/config-service'
import * as projectChecker from '../../src/services/project-checker'

// With this trick we can test non exported symbols
const rewire = require('rewire')
const deploy = rewire('../../src/commands/deploy')
const runTasks = deploy.__get__('runTasks')

describe('deploy', () => {
  beforeEach(() => {
    delete process.env.BOOSTER_ENV
  })

  afterEach(() => {
    // Restore the default sinon sandbox here
    restore()
  })

  // TODO: Check if I can test that `runTasks` is called from the Command `run` method using `sinon.replace(...)`

  describe('runTasks function', () => {
    context('when an unexpected problem happens', () => {
      fancy.stdout().it('fails gracefully showing the error message', async () => {
        const msg = 'weird exception'
        const fakeLoader = Promise.reject(new Error(msg))
        const fakeDeployer = fake()
        replace(environment, 'currentEnvironment', fake.returns('test-env'))

        await expect(runTasks(fakeLoader, fakeDeployer)).to.eventually.be.rejectedWith(msg)
        expect(fakeDeployer).not.to.have.been.called
      })
    })

    context('when index.ts structure is not correct', () => {
      fancy.stdout().it('fails gracefully', async () => {
        const msg = 'An error when loading project'
        const fakeLoader = Promise.reject(new Error(msg))
        const fakeDeployer = fake()
        replace(environment, 'currentEnvironment', fake.returns('test-env'))

        await expect(runTasks(fakeLoader, fakeDeployer)).to.eventually.be.rejectedWith(msg)
        expect(fakeDeployer).not.to.have.been.called
      })
    })

    context('when there is a valid index.ts', () => {
      fancy.stdout().it('Starts deployment', async (ctx) => {
        replace(dependencies, 'installAllDependencies', fake())

        const fakeProvider = {} as ProviderLibrary

        const fakeLoader = fake.resolves({
          provider: fakeProvider,
          appName: 'fake app',
          region: 'tunte',
          entities: {},
        })

        const fakeDeployer = fake((_config: unknown, logger: Logger) => {
          logger.info('this is a progress update')
        })

        replace(environment, 'currentEnvironment', fake.returns('test-env'))

        await runTasks(fakeLoader, fakeDeployer)

        expect(ctx.stdout).to.include('Deployment complete')

        expect(fakeDeployer).to.have.been.calledOnce
      })
    })
  })

  describe('run', () => {
    context('when no environment provided', async () => {
      test
        .stdout()
        .command(['deploy'])
        .it('shows no environment provided error', (ctx) => {
          expect(ctx.stdout).to.match(/No environment set/)
        })
    })
  })

  describe('deploy class', () => {
    beforeEach(() => {
      const config = new BoosterConfig('fake_environment')
      replace(configService,'compileProjectAndLoadConfig', fake.resolves(config))
      replace(providerService,'deployToCloudProvider', fake.resolves({}))
      replace(configService,'createDeploymentSandbox', fake.returns('fake/path'))
      replace(configService,'cleanDeploymentSandbox', fake.resolves({}))
      replace(projectChecker,'checkCurrentDirBoosterVersion', fake.resolves({}))
      replace(oraLogger,'fail', fake.resolves({}))
      replace(oraLogger, 'info', fake.resolves({}))
      replace(oraLogger, 'start', fake.resolves({}))
      replace(oraLogger, 'succeed', fake.resolves({}))
    })

    it('without flags', async () => {
      await new Deploy.default([], {} as IConfig).run()

      expect(projectChecker.checkCurrentDirBoosterVersion).to.have.been.called
      expect(configService.compileProjectAndLoadConfig).to.have.not.been.called
      expect(providerService.deployToCloudProvider).to.have.not.been.called
      expect(oraLogger.fail).to.have.been.calledWithMatch(/No environment set/)
    })

    it('with -e flag incomplete', async () => {
      let exceptionThrown = false
      let exceptionMessage = ''
      try {
        await new Deploy.default(['-e'], {} as IConfig).run()
      } catch (e) {
        exceptionThrown = true
        exceptionMessage = e.message
      }
      expect(exceptionThrown).to.be.equal(true)
      expect(exceptionMessage).to.contain('--environment expects a value')
      expect(configService.compileProjectAndLoadConfig).to.have.not.been.called
      expect(providerService.deployToCloudProvider).to.have.not.been.called
    })

    it('with --environment flag incomplete', async () => {
      let exceptionThrown = false
      let exceptionMessage = ''
      try {
        await new Deploy.default(['--environment'], {} as IConfig).run()
      } catch (e) {
        exceptionThrown = true
        exceptionMessage = e.message
      }
      expect(exceptionThrown).to.be.equal(true)
      expect(exceptionMessage).to.to.contain('--environment expects a value')
      expect(configService.compileProjectAndLoadConfig).to.have.not.been.called
      expect(providerService.deployToCloudProvider).to.have.not.been.called
    })

    describe('inside a booster project', () => {

      it('entering correct environment', async () => {
        await new Deploy.default(['-e','fake_environment'], {} as IConfig).run()
  
        expect(projectChecker.checkCurrentDirBoosterVersion).to.have.been.called
        expect(configService.compileProjectAndLoadConfig).to.have.been.called
        expect(providerService.deployToCloudProvider).to.have.been.called
        expect(oraLogger.info).to.have.been.calledWithMatch('Deployment complete!')
      })

      it('entering correct environment and nonexisting flag', async () => {
        let exceptionThrown = false
        let exceptionMessage = ''
        try {
          await new Deploy.default(['-e','fake_environment','--nonexistingoption'], {} as IConfig).run()
        } catch(e) {
          exceptionThrown = true
          exceptionMessage = e.message
        }
        expect(exceptionThrown).to.be.equal(true)
        expect(exceptionMessage).to.contain('Unexpected argument: --nonexistingoption')
        expect(projectChecker.checkCurrentDirBoosterVersion).to.have.not.been.called
        expect(configService.compileProjectAndLoadConfig).to.have.not.been.called
        expect(providerService.deployToCloudProvider).to.have.not.been.called
        expect(oraLogger.info).to.have.not.been.calledWithMatch('Deployment complete!')
      })

    })
  })
})
