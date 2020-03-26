/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { expect } from 'chai'
import { fancy } from 'fancy-test'
import { restore, replace, fake } from 'sinon'
import { Observable, Observer } from 'rxjs'
import rewire = require('rewire')
import Prompter from '../../src/services/user-prompt'
import { ProviderLibrary } from '@boostercloud/framework-types'

const nuke = rewire('../../src/commands/nuke')
const runTasks = nuke.__get__('runTasks')
const loader = nuke.__get__('askToConfirmRemoval')

describe('nuke', () => {
  afterEach(() => {
    restore()
  })

  describe('runTasks function', () => {
    context('when an unexpected problem happens', () => {
      fancy.stdout().it('fails gracefully showing the error message', async (ctx) => {
        const fakeLoader = Promise.reject(new Error('weird exception'))
        const fakeNuke = fake()

        await runTasks('test-env', fakeLoader, fakeNuke)

        expect(ctx.stdout).to.include('weird exception')

        expect(fakeNuke.called).to.equal(false)
      })
    })

    context('when a wrong application name is provided', () => {
      fancy.stdout().it('fails gracefully showing the error message', async (ctx) => {
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

        await runTasks('test-env', loader(prompter, fakeConfig), fakeNuke)

        expect(ctx.stdout).to.include('Wrong app name')

        expect(fakeNuke.called).to.equal(false)
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
        const fakeNuke = fake.returns(
          Observable.create((obs: Observer<string>) => {
            obs.next('this is a progress update')
            obs.complete()
          })
        )

        await runTasks('test-env', loader(prompter, fakeConfig), fakeNuke)

        expect(ctx.stdout).to.include('Removal complete!')

        expect(fakeNuke.called).to.equal(true)
      })
    })
  })
})
