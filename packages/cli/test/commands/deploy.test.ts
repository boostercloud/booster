/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { expect } from '../expect'
import { fancy } from 'fancy-test'
import { restore, fake, spy } from 'sinon'
// import { restore, fake } from 'sinon'

import { ProviderLibrary, Logger } from '@boostercloud/framework-types'
import { test } from '@oclif/test'
// import * as deploy from '../../src/commands/deploy'

// With this trick we can test non exported symbols
const rewire = require('rewire')
const deploy = rewire('../../src/commands/deploy')
const runTasks = deploy.__get__('runTasks')
const pruneDependencies = deploy.__get__('pruneDependencies')
const reinstallDependencies = deploy.__get__('reinstallDependencies')

describe('deploy', () => {
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

        await expect(runTasks('test-env', fakeLoader, fakeDeployer)).to.eventually.be.rejectedWith(msg)
        expect(fakeDeployer).not.to.have.been.called
      })
    })

    context('when index.ts structure is not correct', () => {
      fancy.stdout().it('fails gracefully', async () => {
        const msg = 'An error when loading project'
        const fakeLoader = Promise.reject(new Error(msg))
        const fakeDeployer = fake()

        await expect(runTasks('test-env', fakeLoader, fakeDeployer)).to.eventually.be.rejectedWith(msg)
        expect(fakeDeployer).not.to.have.been.called
      })
    })

    fancy.stdout().it('calls pruneDependencies function', async (ctx) => {
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

      const pruneDependenciesSpy = spy(pruneDependencies)
      deploy.__set__('pruneDependencies', pruneDependenciesSpy)
      await runTasks('test-env', fakeLoader, fakeDeployer)
        
      expect(pruneDependenciesSpy).to.have.been.calledOnce

      expect(ctx.stdout).to.include('Deployment complete')
      expect(fakeDeployer).to.have.been.calledOnce
    })

    fancy.stdout().it('calls reinstallDependencies function', async (ctx) => {
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

      const reinstallDependenciesSpy = spy(reinstallDependencies)
      deploy.__set__('reinstallDependencies', reinstallDependenciesSpy)
      await runTasks('test-env', fakeLoader, fakeDeployer)
        
      expect(reinstallDependenciesSpy).to.have.been.calledOnce

      expect(ctx.stdout).to.include('Deployment complete')
      expect(fakeDeployer).to.have.been.calledOnce
    })

    context('when there is a valid index.ts', () => {
      fancy.stdout().it('Starts deployment', async (ctx) => {
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

        await runTasks('test-env', fakeLoader, fakeDeployer)

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
          expect(ctx.stdout).to.equal('Error: no environment name provided. Usage: `boost deploy -e <environment>`.\n')
        })
    })
  })
})
