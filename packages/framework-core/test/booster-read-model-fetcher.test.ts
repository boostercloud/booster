import { describe } from 'mocha'
import { expect } from 'chai'
import { BoosterConfig, Provider } from '@boostercloud/framework-types'
import { replace, fake } from 'sinon'
import { Library } from '@boostercloud/framework-provider-aws'
import { BoosterReadModelFetcher } from '../src/booster-read-model-fetcher'

describe('BoosterReadModelFetcher', () => {
  describe('the public static method `fetch`', () => {
    const config = new BoosterConfig()
    config.provider = Provider.AWS

    it('calls to the `processReadModelAPICall` method of the configured provider', async () => {
      replace(Library, 'processReadModelAPICall', fake())
      const rawMessage = { some: 'Message' }

      await BoosterReadModelFetcher.fetch(rawMessage, config)

      expect(Library.processReadModelAPICall).to.have.been.calledOnceWith(config, rawMessage)
    })
  })
})
