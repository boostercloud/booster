import { describe } from 'mocha'
import { replace, fake, restore } from 'sinon'
import { Providers } from '../../src/providers'
import { ProviderLibrary, BoosterConfig } from '@boostercloud/framework-types'
import { RawEventsParser } from '../../src/services/raw-events-parser'
import { Library } from '@boostercloud/framework-provider-aws'
import { expect } from 'chai'

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
      const providerLibrary: ProviderLibrary = {
        ...Library,
        rawEventsToEnvelopes: fake.returns([anEvent, anotherEvent]),
      }
      replace(Providers, 'getLibrary', fake.returns(providerLibrary))
      const callbackFn = fake()

      const config = new BoosterConfig()

      await RawEventsParser.streamEvents(config, { some: 'raw message' }, callbackFn)

      expect(callbackFn.firstCall.args[0]).to.deep.equal(anEvent)
      expect(callbackFn.secondCall.args[0]).to.deep.equal(anotherEvent)
      expect(callbackFn).to.have.been.calledTwice
    })
  })
})
