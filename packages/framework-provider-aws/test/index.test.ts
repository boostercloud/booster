import { ProviderLibrary } from '@boostercloud/framework-types'
import { expect } from './expect'
import { fake, restore, stub } from 'sinon'

const rewire = require('rewire')
const awsSdk = require('aws-sdk')
const providerPackage = rewire('../src/index')
const providerPackageSetup = rewire('../src/setup')
const fakeInfrastructure = fake.returns({})

providerPackageSetup.__set__('loadInfrastructurePackage', () => ({
  Infrastructure: fakeInfrastructure,
}))

describe('the `framework-provider-aws` package', (): void => {
  describe('the `Provider` function', () => {
    afterEach(() => {
      restore()
    })

    context('with no rockets', () => {
      it('returns an empty `ProviderLibrary` when DynamoDB is undefined', () => {
        stub(awsSdk, 'DynamoDB').returns(undefined)
        const providerLibrary: ProviderLibrary = providerPackage.Provider()
        expect(providerLibrary).to.be.an('object')
        expect(providerLibrary).to.deep.equal({})
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

      const providerLibrary: ProviderLibrary = providerPackageSetup.Provider(rockets)

      it('returns a `ProviderLibrary` object', () => {
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
          providerLibrary.infrastructure()
          expect(fakeInfrastructure).to.have.been.calledWith(rockets)
        })
      })
    })
  })
})
