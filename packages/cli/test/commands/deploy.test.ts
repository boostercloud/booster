/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { expect } from 'chai'
import { fancy } from 'fancy-test'
import { Provider } from '@boostercloud/framework-types'
import { stub, restore } from 'sinon'
import { Observable, Observer } from 'rxjs'
import rewire = require('rewire')

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
        const fakeDeployer = stub()

        await runTasks(fakeLoader, fakeDeployer)

        expect(ctx.stdout).to.include('weird exception')

        expect(fakeDeployer.called).to.equal(false)
      })
    })

    context('when index.ts structure is not correct', () => {
      fancy.stdout().it('fails gracefully', async (ctx) => {
        const fakeLoader = Promise.reject(new Error('An error when loading project'))
        const fakeDeployer = stub()

        await runTasks(fakeLoader, fakeDeployer)

        expect(ctx.stdout).to.include('An error when loading project')

        expect(fakeDeployer.called).to.equal(false)
      })
    })

    context('when there is a valid index.ts', () => {
      fancy.stdout().it('Starts deployment', async (ctx) => {
        const fakeLoader = Promise.resolve({
          provider: Provider.AWS,
          appName: 'fake app',
          region: 'tunte',
          entities: {},
        })

        const fakeDeployer = stub().returns(
          Observable.create((obs: Observer<string>) => {
            obs.next('this is a progress update')
            obs.complete()
          })
        )

        await runTasks(fakeLoader, fakeDeployer)

        expect(ctx.stdout).to.include('Deployment complete')

        expect(fakeDeployer.called).to.equal(true)
      })
    })
  })
})
