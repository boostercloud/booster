import { expect } from './expect'
import {
  boosterEventDispatcher,
  boosterNotifySubscribers,
  boosterRocketDispatcher,
  boosterServeGraphQL,
  boosterTriggerScheduledCommands,
} from '../src/'
import { fake, replace, restore } from 'sinon'
import { BoosterConfig } from '@boostercloud/framework-types'
import { BoosterEventDispatcher } from '../src/booster-event-dispatcher'
import { BoosterGraphQLDispatcher } from '../src/booster-graphql-dispatcher'
import { BoosterScheduledCommandDispatcher } from '../src/booster-scheduled-command-dispatcher'
import { BoosterSubscribersNotifier } from '../src/booster-subscribers-notifier'

describe('framework-core package', () => {
  afterEach(() => {
    restore()
  })

  context('`boosterEventDispatcher` function', () => {
    it('calls the `dispatch` method of the `BoosterEventDispatcher` class', async () => {
      const fakeDispatch = fake.resolves(undefined)
      const config = new BoosterConfig('test')
      const fakeRawEvents = { some: 'events' }
      replace(BoosterEventDispatcher, 'dispatch', fakeDispatch)
      await boosterEventDispatcher(fakeRawEvents)
      expect(fakeDispatch).to.have.been.calledOnceWithExactly(fakeRawEvents, config)
    })
  })

  context('`boosterServeGraphQL` function', () => {
    it('calls the `dispatch` method of the `BoosterGraphQLDispatcher` class', async () => {
      const fakeDispatch = fake.resolves(undefined)
      const fakeRawRequest = { some: 'request' }
      replace(BoosterGraphQLDispatcher.prototype, 'dispatch', fakeDispatch)
      await boosterServeGraphQL(fakeRawRequest)
      expect(fakeDispatch).to.have.been.calledOnceWithExactly(fakeRawRequest)
    })
  })

  context('`boosterTriggerScheduledCommands` function', () => {
    it('calls the `dispatch` method of the `BoosterScheduledCommandDispatcher` class', async () => {
      const fakeDispatch = fake.resolves(undefined)
      const fakeRawRequest = { some: 'request' }
      replace(BoosterScheduledCommandDispatcher.prototype, 'dispatch', fakeDispatch)
      await boosterTriggerScheduledCommands(fakeRawRequest)
      expect(fakeDispatch).to.have.been.calledOnceWithExactly(fakeRawRequest)
    })
  })

  context('`boosterNotifySubscribers` function', () => {
    it('calls the `dispatch` method of the `BoosterSubscribersNotifier` class', async () => {
      const fakeDispatch = fake.resolves(undefined)
      const fakeRawRequest = { some: 'request' }
      replace(BoosterSubscribersNotifier.prototype, 'dispatch', fakeDispatch)
      await boosterNotifySubscribers(fakeRawRequest)
      expect(fakeDispatch).to.have.been.calledOnceWithExactly(fakeRawRequest)
    })
  })

  context('`boosterRocketDispatcher` function', () => {
    it('calls the `dispatch` method of the `BoosterRocketDispatcher` class', async () => {
      const fakeDispatch = fake.resolves(undefined)
      const fakeRawRequest = { some: 'request' }
      replace(BoosterSubscribersNotifier.prototype, 'dispatch', fakeDispatch)
      await boosterRocketDispatcher(fakeRawRequest)
      expect(fakeDispatch).to.have.been.calledOnceWithExactly(fakeRawRequest)
    })
  })

  it('exports the `boosterHealth` function', () => {
    expect(BoosterCore.boosterHealth).not.to.be.null
    expect(BoosterCore.boosterHealth).to.equal(Booster.boosterHealth)
  })
})
