import { dynamicLoad } from '../../src/services/dynamic-loader'
import { expect } from '../expect'

describe('dynamic-loader service', () => {
  describe('dynamicLoad', () => {
    it('behaves as require', () => {
      const packageJson = dynamicLoad('../../package.json')

      expect(packageJson.name).to.equal('@boostercloud/cli')
    })
  })
})
