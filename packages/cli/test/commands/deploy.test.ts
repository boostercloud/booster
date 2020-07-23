/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { expect } from '../expect'
import { fancy } from 'fancy-test'
import { restore, fake } from 'sinon'
import { Observable, Observer } from 'rxjs'
import rewire = require('rewire')
import { ProviderLibrary } from '@boostercloud/framework-types'
import { test } from '@oclif/test'

// With this trick we can test non exported symbols
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
      fancy.stdout().it('fails gracefully showing the error message', async (ctx) => {
        const fakeLoader = Promise.reject(new Error('weird exception'))
        const fakeDeployer = fake()

        await expect(runTasks('test-env', fakeLoader, fakeDeployer)).to.eventually.be.rejectedWith()
        expect(ctx.stdout).to.include('weird exception')
        expect(fakeDeployer).not.to.have.been.called
      })
    })

    context('when index.ts structure is not correct', () => {
      fancy.stdout().it('fails gracefully', async (ctx) => {
        const fakeLoader = Promise.reject(new Error('An error when loading project'))
        const fakeDeployer = fake()

        await expect(runTasks('test-env', fakeLoader, fakeDeployer)).to.eventually.be.rejectedWith()
        expect(ctx.stdout).to.include('An error when loading project')
        expect(fakeDeployer).not.to.have.been.called
      })
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

        const fakeDeployer = fake.returns(
          Observable.create((obs: Observer<string>) => {
            obs.next('this is a progress update')
            obs.complete()
          })
        )

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
