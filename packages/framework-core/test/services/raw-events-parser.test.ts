import { describe } from 'mocha'
import { fake, restore } from 'sinon'
import { ProviderLibrary, BoosterConfig } from '@boostercloud/framework-types'
import { RawEventsParser } from '../../src/services/raw-events-parser'
import { expect } from '../expect'

describe('RawEventsParser', () => {
  afterEach(() => {
    restore()
  })

  describe('streamEvents', () => {
    it('parses in order a list of events encoded in a provider-specific raw message', async () => {
      const anEvent = {
        id: 1,
      }
      const anotherEvent = {
        id: 2,
      }
      const providerLibrary = ({
        events: {
          rawToEnvelopes: fake.returns([anEvent, anotherEvent]),
        },
      } as unknown) as ProviderLibrary
      const callbackFn = fake()

      const config = new BoosterConfig('test')
      config.provider = providerLibrary

      await RawEventsParser.streamEvents(config, { some: 'raw message' }, callbackFn)

      expect(callbackFn.firstCall.args[0]).to.deep.equal(anEvent)
      expect(callbackFn.secondCall.args[0]).to.deep.equal(anotherEvent)
      expect(callbackFn).to.have.been.calledTwice
    })
  })
})
