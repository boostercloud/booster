import { ProviderLibrary } from '@boostercloud/framework-types'
import { expect } from '@boostercloud/framework-provider-aws-infrastructure/test/expect'
import { fake } from 'sinon'

const rewire = require('rewire')
const providerPackage = rewire('../src/index')
const fakeInfrastructure = fake.returns({})
providerPackage.__set__('loadInfrastructurePackage', () => ({
  Infrastructure: fakeInfrastructure,
}))

describe('the `framework-provider-aws` package', () => {
  describe('the `AWSProvider` function', () => {
    context('with no plugins', () => {
      const providerLibrary: ProviderLibrary = providerPackage.AWSProvider()

      it('returns a `ProviderLibrary` object', () => {
        expect(providerLibrary).to.be.an('object')
        expect(providerLibrary.api).to.be.an('object')
        expect(providerLibrary.auth).to.be.an('object')
        expect(providerLibrary.connections).to.be.an('object')
        expect(providerLibrary.events).to.be.an('object')
        expect(providerLibrary.graphQL).to.be.an('object')
        expect(providerLibrary.infrastructure).to.be.a('function')
        expect(providerLibrary.readModels).to.be.an('object')
      })

      describe('infrastructure', () => {
        it('is loaded with no parameters', () => {
          providerLibrary.infrastructure()

          expect(fakeInfrastructure).to.have.been.calledWith()
        })
      })
    })

    context('with a list of plugins', () => {
      const listOfPlugins = [
        {
          packageName: 'some-package-name',
          parameters: {
            whatever: true,
          },
        },
      ]

      const providerLibrary: ProviderLibrary = providerPackage.AWSProvider(listOfPlugins)

      it('returns a `ProviderLibrary` object', () => {
        expect(providerLibrary).to.be.an('object')
        expect(providerLibrary.api).to.be.an('object')
        expect(providerLibrary.auth).to.be.an('object')
        expect(providerLibrary.connections).to.be.an('object')
        expect(providerLibrary.events).to.be.an('object')
        expect(providerLibrary.graphQL).to.be.an('object')
        expect(providerLibrary.infrastructure).to.be.a('function')
        expect(providerLibrary.readModels).to.be.an('object')
      })

      describe('infrastructure', () => {
        it('is loaded with a list of plugins', () => {
          providerLibrary.infrastructure()

          expect(fakeInfrastructure).to.have.been.calledWith(listOfPlugins)
        })
      })
    })
  })
})
