import { ProviderLibrary } from '@boostercloud/framework-types'
import { expect } from '../test/expect'
import { fake, replace, restore } from 'sinon'
import * as GetInstalledPath from 'get-installed-path'
import * as providerPackage from '../src/index'

describe('the `framework-provider-aws` package', () => {
  afterEach(() => {
    restore()
  })

  describe('the `Provider` function', () => {
    const providerLibrary: ProviderLibrary = providerPackage.Provider

    it('returns a `ProviderLibrary` object', () => {
      replace(GetInstalledPath, 'getInstalledPathSync', fake.returns(''))

      expect(providerLibrary).to.be.an('object')
      expect(providerLibrary.api).to.be.an('object')
      expect(providerLibrary.auth).to.be.an('object')
      expect(providerLibrary.connections).to.be.an('object')
      expect(providerLibrary.events).to.be.an('object')
      expect(providerLibrary.graphQL).to.be.an('object')
      expect(providerLibrary.readModels).to.be.an('object')
      expect(providerLibrary.packageDescription).to.be.a('function')
    })

    describe('packageDescription', () => {
      it('provides info about ', () => {
        const packageDescription = providerLibrary.packageDescription()

        expect(packageDescription.name).to.be.equal('@boostercloud/framework-provider-aws')
        expect(packageDescription.version).to.be.equal(require('../package.json').version)
      })
    })
  })
})
