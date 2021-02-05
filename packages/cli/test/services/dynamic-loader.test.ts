import * as DynamicLoader from '../../src/services/dynamic-loader'
import { expect } from '../expect'
import { restore } from 'sinon'

describe('dynamic-loader service', () => {
  afterEach(() => {
    restore()
  })

  describe('dynamicLoadModule', () => {
    it('can load a specific file using a relative path', async () => {
      const packageJson = await DynamicLoader.dynamicLoadModule('../../package.json')

      expect(packageJson.name).to.equal('@boostercloud/cli')
    })

    it('can load a package', async () => {
      const packageJson = await DynamicLoader.dynamicLoadModule('@boostercloud/framework-types')

      expect(packageJson.BoosterConfig).not.to.be.null
    })
  })
})
