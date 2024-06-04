/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { BoosterEventDispatcher } from '../src/booster-event-dispatcher'
import { fake, replace, restore, SinonSpy } from 'sinon'
import { BoosterConfig, ProviderLibrary, UUID } from '@boostercloud/framework-types'
import { expect } from './expect'
import { RawEventsParser } from '../src/services/raw-events-parser'
import { BoosterEventProcessor } from '../src/booster-event-processor'

class SomeEvent {
  public constructor(readonly id: UUID) {}

  public entityID(): UUID {
    return this.id
  }
  public getPrefixedId(prefix: string): string {
    return `${prefix}-${this.id}`
  }
}

class SomeNotification {
  public constructor() {}
}

describe('BoosterEventDispatcher', () => {
  afterEach(() => {
    restore()
  })

  const config = new BoosterConfig('test')
  config.provider = {} as ProviderLibrary
  config.events[SomeEvent.name] = { class: SomeEvent }
  config.notifications[SomeNotification.name] = { class: SomeNotification }
  config.logger = {
    info: fake(),
    error: fake(),
    debug: fake(),
    warn: fake(),
  }
  const rawEvents = [{ some: 'raw event' }, { some: 'other raw event' }]
  const events = [{ some: 'raw event' }, { some: 'other raw event' }]
  const fakeRawToEnvelopes: SinonSpy = fake.returns(events)
  config.provider = {
    events: {
      rawToEnvelopes: fakeRawToEnvelopes,
    },
  } as unknown as ProviderLibrary

  context('with a configured provider', () => {
    describe('the `dispatch` method', () => {
      it('calls the raw events parser once and processes all messages', async () => {
        replace(RawEventsParser, 'streamPerEntityEvents', fake())

        await BoosterEventDispatcher.dispatch(rawEvents, config)

        expect(RawEventsParser.streamPerEntityEvents).to.have.been.calledWithMatch(
          config,
          events,
          (BoosterEventProcessor as any).eventProcessor
        )
      })

      it('logs and ignores errors thrown by `streamPerEntityEvents`', async () => {
        const error = new Error('some error')
        replace(RawEventsParser, 'streamPerEntityEvents', fake.rejects(error))

        const rawEvents = [{ some: 'raw event' }, { some: 'other raw event' }]
        await expect(BoosterEventDispatcher.dispatch(rawEvents, config)).not.to.be.rejected

        expect(config.logger?.error).to.have.been.calledWith(
          '[Booster]|BoosterEventDispatcher#dispatch: ',
          'Unhandled error while dispatching event: ',
          error
        )
      })
    })
  })
})
