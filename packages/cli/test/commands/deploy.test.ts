/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { expect } from '../expect'
import { fancy } from 'fancy-test'
import { restore, fake, replace } from 'sinon'
import { ProviderLibrary, Logger } from '@boostercloud/framework-types'
import { test } from '@oclif/test'
import * as environment from '../../src/services/environment'
import * as dependencies from '../../src/services/dependencies'

// With this trick we can test non exported symbols
const rewire = require('rewire')
const deploy = rewire('../../src/commands/deploy')
const runTasks = deploy.__get__('runTasks')

describe('deploy', () => {
  afterEach(() => {
    // Restore the default sinon sandbox here
    restore()
  })

  // TODO: Check if I can test that `runTasks` is called from the Command `run` method using `sinon.replace(...)`

  describe('runTasks function', () => {
    context('when an unexpected problem happens', () => {
      fancy.stdout().it('fails gracefully showing the error message', async () => {
        replace(dependencies, 'installDependencies', fake())
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
        replace(dependencies, 'installDependencies', fake())
        const msg = 'An error when loading project'
        const fakeLoader = Promise.reject(new Error(msg))
        const fakeDeployer = fake()
        replace(environment, 'currentEnvironment', fake.returns('test-env'))

        await expect(runTasks(fakeLoader, fakeDeployer)).to.eventually.be.rejectedWith(msg)
        expect(fakeDeployer).not.to.have.been.called
      })
    })

    fancy.stdout().it('installs dependencies before deploy', async (ctx) => {
      const fakeInstallDependencies = fake()
      replace(dependencies, 'installDependencies', fakeInstallDependencies)

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

      expect(fakeInstallDependencies).to.have.been.called

      expect(ctx.stdout).to.include('Deployment complete')
      expect(fakeDeployer).to.have.been.calledOnce
    })

    context('when there is a valid index.ts', () => {
      fancy.stdout().it('Starts deployment', async (ctx) => {
        replace(dependencies, 'installDependencies', fake())

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
})
