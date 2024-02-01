import { expect } from './expect'
import {
  Booster,
  boosterEventDispatcher,
  boosterNotifySubscribers,
  boosterRocketDispatcher,
  boosterServeGraphQL,
  boosterTriggerScheduledCommands,
  boosterConsumeEventStream,
  boosterProduceEventStream,
  boosterHealth,
} from '../src/'
import { fake, replace, restore } from 'sinon'
import { BoosterEventDispatcher } from '../src/booster-event-dispatcher'
import { BoosterGraphQLDispatcher } from '../src/booster-graphql-dispatcher'
import { BoosterScheduledCommandDispatcher } from '../src/booster-scheduled-command-dispatcher'
import { BoosterSubscribersNotifier } from '../src/booster-subscribers-notifier'
import { BoosterRocketDispatcher } from '../src/booster-rocket-dispatcher'
import { BoosterEventStreamConsumer } from '../src/booster-event-stream-consumer'
import { BoosterEventStreamProducer } from '../src/booster-event-stream-producer'
import { BoosterHealthService } from '../src/sensor'

describe('framework-core package', () => {
  afterEach(() => {
    restore()
  })

  context('`boosterEventDispatcher` function', () => {
    it('calls the `dispatch` method of the `BoosterEventDispatcher` class', async () => {
      const fakeDispatch = fake.resolves(undefined)
      const fakeRawEvents = { some: 'events' }
      replace(BoosterEventDispatcher, 'dispatch', fakeDispatch)
      await boosterEventDispatcher(fakeRawEvents)
      expect(fakeDispatch).to.have.been.calledOnceWithExactly(fakeRawEvents, Booster.config)
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
      replace(BoosterRocketDispatcher.prototype, 'dispatch', fakeDispatch)
      await boosterRocketDispatcher(fakeRawRequest)
      expect(fakeDispatch).to.have.been.calledOnceWithExactly(fakeRawRequest)
    })
  })

  context('`boosterConsumeEventStream` function', () => {
    it('calls the `consume` method of the `BoosterEventStreamConsumer` class', async () => {
      const fakeConsume = fake.resolves(undefined)
      const fakeRawEvent = { some: 'event' }
      replace(BoosterEventStreamConsumer, 'consume', fakeConsume)
      await boosterConsumeEventStream(fakeRawEvent)
      expect(fakeConsume).to.have.been.calledOnceWithExactly(fakeRawEvent, Booster.config)
    })
  })

  context('`boosterProduceEventStream` function', () => {
    it('calls the `produce` method of the `BoosterEventStreamProducer` class', async () => {
      const fakeProduce = fake.resolves(undefined)
      const fakeRawEvent = { some: 'event' }
      replace(BoosterEventStreamProducer, 'produce', fakeProduce)
      await boosterProduceEventStream(fakeRawEvent)
      expect(fakeProduce).to.have.been.calledOnceWithExactly(fakeRawEvent, Booster.config)
    })
  })

  context('`boosterHealth` function', () => {
    it('calls the `boosterHealth` method of the `BoosterHealthService` class', async () => {
      const fakeHealth = fake.resolves(undefined)
      const fakeRawRequest = { some: 'request' }
      replace(BoosterHealthService.prototype, 'boosterHealth', fakeHealth)
      await boosterHealth(fakeRawRequest)
      expect(fakeHealth).to.have.been.calledOnceWithExactly(fakeRawRequest)
    })
  })
})
