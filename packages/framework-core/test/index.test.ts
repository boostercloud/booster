import { describe } from 'mocha'
import { expect } from './expect'
import * as BoosterCore from '../src/index'
import { BoosterEventDispatcher } from '../src/booster-event-dispatcher'
import { replace, fake, restore } from 'sinon'
import { BoosterAuth } from '../src/booster-auth'
import { BoosterGraphQLDispatcher } from '../src/booster-graphql-dispatcher'
import { BoosterScheduledCommandDispatcher } from '../src/booster-scheduled-command-dispatcher'
import { BoosterSubscribersNotifier } from '../src/booster-subscribers-notifier'

describe('framework-core package', () => {
  afterEach(() => {
    restore()
  })

  beforeEach(() => {
    BoosterCore.Booster.configure('test', (config) => {
      config.appName = 'test-app'
    })
  })

  it('exports the `boosterEventDispatcher` function', async () => {
    replace(BoosterEventDispatcher, 'dispatch', fake())

    const anEvent = { an: 'event' }

    await BoosterCore.boosterEventDispatcher(anEvent)

    expect(BoosterEventDispatcher.dispatch).to.have.been.calledOnceWith(
      anEvent,
      BoosterCore.Booster.config,
      BoosterCore.Booster.logger
    )
  })

  it('exports the `boosterPreSignUpChecker` function', async () => {
    replace(BoosterAuth, 'checkSignUp', fake())

    const aSignupRequest = { a: 'signup request' }

    await BoosterCore.boosterPreSignUpChecker(aSignupRequest)

    expect(BoosterAuth.checkSignUp).to.have.been.calledOnceWith(
      aSignupRequest,
      BoosterCore.Booster.config,
      BoosterCore.Booster.logger
    )
  })

  it('exports the `boosterServeGraphQL` function', async () => {
    const fakeDispatch = fake()
    replace(BoosterGraphQLDispatcher.prototype, 'dispatch', fakeDispatch)

    const aRequest = { a: 'request' }

    await BoosterCore.boosterServeGraphQL(aRequest)

    expect(fakeDispatch).to.have.been.calledOnceWith(aRequest)
  })

  it('exports the `boosterTriggerScheduledCommand` function', async () => {
    const fakeDispatch = fake()
    replace(BoosterScheduledCommandDispatcher.prototype, 'dispatch', fakeDispatch)

    const aRequest = { a: 'scheduled command request' }

    await BoosterCore.boosterTriggerScheduledCommand(aRequest)

    expect(fakeDispatch).to.have.been.calledOnceWith(aRequest)
  })

  it('exports the `boosterNotifySubscribers` function', async () => {
    const fakeDispatch = fake()
    replace(BoosterSubscribersNotifier.prototype, 'dispatch', fakeDispatch)

    const aRequest = { a: 'notify subscribers event' }

    await BoosterCore.boosterNotifySubscribers(aRequest)

    expect(fakeDispatch).to.have.been.calledOnceWith(aRequest)
  })

  it('exports the BoosterApp class and the Booster alias', () => {
    expect(BoosterCore.Booster).not.to.be.null
  })
})
