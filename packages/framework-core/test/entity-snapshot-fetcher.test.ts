import { describe } from 'mocha'
import { fake, replace, restore } from 'sinon'
import { expect } from 'chai'
import { BoosterConfig, Level, ProviderLibrary } from '@boostercloud/framework-types'
import { buildLogger } from '../src/booster-logger'
import { fetchEntitySnapshot } from '../src/entity-snapshot-fetcher'
import { EventStore } from '../src/services/event-store'

describe('entitySnapshotFetcher', () => {
  afterEach(() => {
    restore()
  })

  context('given a BoosterConfig and a Logger', () => {
    const config = new BoosterConfig()
    config.provider = {} as ProviderLibrary
    const logger = buildLogger(Level.debug)

    it('the `fetchEntitySnapshot` function calls to the `fetchEntitySnapshot` method in the EventStore', async () => {
      replace(EventStore.prototype, 'fetchEntitySnapshot', fake.returns({ value: { an: 'object' } }))

      const snapshot = await fetchEntitySnapshot(config, logger, 'SomeEntity', '42')

      expect(snapshot).to.be.deep.equal({ an: 'object' })
      expect(EventStore.prototype.fetchEntitySnapshot).to.have.been.calledOnceWith('SomeEntity', '42')
    })
  })
})
