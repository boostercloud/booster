import { describe } from 'mocha'
import { expect } from 'chai'
import { BoosterConfig, ProviderLibrary } from '@boostercloud/framework-types'
import { replace, fake } from 'sinon'
import { BoosterReadModelFetcher } from '../src/booster-read-model-fetcher'

describe('BoosterReadModelFetcher', () => {
  describe('the public static method `fetch`', () => {
    const config = new BoosterConfig()
    config.provider = ({
      processReadModelAPICall: () => {},
    } as unknown) as ProviderLibrary

    it('calls to the `processReadModelAPICall` method of the configured provider', async () => {
      replace(config.provider, 'processReadModelAPICall', fake())
      const rawMessage = { some: 'Message' }

      await BoosterReadModelFetcher.fetch(rawMessage, config)

      expect(config.provider.processReadModelAPICall).to.have.been.calledOnceWith(config, rawMessage)
    })
  })
})
