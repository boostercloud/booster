import { ProviderLibrary } from '@boostercloud/framework-types'
import { expect } from '../test/expect'
import { fake, replace, restore } from 'sinon'
import * as GetInstalledPath from 'get-installed-path'

const rewire = require('rewire')
const providerPackage = rewire('../src/index')
const fakeInfrastructure = fake.returns({})
providerPackage.__set__('loadInfrastructurePackage', () => ({
  Infrastructure: fakeInfrastructure,
}))

describe('the `framework-provider-aws` package', () => {
  afterEach(() => {
    restore()
  })

  describe('the `Provider` function', () => {
    context('with no rockets', () => {
      const providerLibrary: ProviderLibrary = providerPackage.Provider()

      it('returns a `ProviderLibrary` object', () => {
        replace(GetInstalledPath, 'getInstalledPathSync', fake.returns(''))

        expect(providerLibrary).to.be.an('object')
        expect(providerLibrary.api).to.be.an('object')
        expect(providerLibrary.connections).to.be.an('object')
        expect(providerLibrary.events).to.be.an('object')
        expect(providerLibrary.graphQL).to.be.an('object')
        expect(providerLibrary.infrastructure).to.be.a('function')
        expect(providerLibrary.readModels).to.be.an('object')
      })

      describe('infrastructure', () => {
        it('is loaded with no parameters', () => {
          replace(GetInstalledPath, 'getInstalledPathSync', fake.returns(''))

          providerLibrary.infrastructure()

          expect(fakeInfrastructure).to.have.been.calledWith()
        })

        context('when the infrastructure package is not installed', () => {
          it('throws an error', () => {
            replace(GetInstalledPath, 'getInstalledPathSync', fake.throws('Not good...'))

            expect(providerLibrary.infrastructure).to.throw(/infrastructure package must be installed/)
          })
        })
      })
    })

    context('with a list of rockets', () => {
      const rockets = [
        {
          packageName: 'some-package-name',
          parameters: {
            whatever: true,
          },
        },
      ]

      const providerLibrary: ProviderLibrary = providerPackage.Provider(rockets)

      it('returns a `ProviderLibrary` object', () => {
        replace(GetInstalledPath, 'getInstalledPathSync', fake.returns(''))

        expect(providerLibrary).to.be.an('object')
        expect(providerLibrary.api).to.be.an('object')
        expect(providerLibrary.connections).to.be.an('object')
        expect(providerLibrary.events).to.be.an('object')
        expect(providerLibrary.graphQL).to.be.an('object')
        expect(providerLibrary.infrastructure).to.be.a('function')
        expect(providerLibrary.readModels).to.be.an('object')
      })

      describe('infrastructure', () => {
        it('is loaded with a list of rockets', () => {
          replace(GetInstalledPath, 'getInstalledPathSync', fake.returns(''))

          providerLibrary.infrastructure()

          expect(fakeInfrastructure).to.have.been.calledWith(rockets)
        })
      })
    })
  })
})
