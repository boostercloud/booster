import { ProviderInfrastructure } from '@boostercloud/framework-types'
import { fake, replace, restore } from 'sinon'
import { expect } from './expect'
import * as infra from '../src/infrastructure'
import { Infrastructure } from '../src/index'
import { RocketLoader } from '@boostercloud/framework-common-helpers'

describe('the `framework-provider-aws-infrastructure` package', () => {
  // eslint-disable-next-line @typescript-eslint/ban-types
  afterEach(() => {
    restore()
  })

  describe('the `Infrastructure` function', () => {
    context('with no rockets', () => {
      const providerInfrastructure: ProviderInfrastructure = Infrastructure()

      it('returns a `ProviderInfrastructure` object', () => {
        expect(providerInfrastructure).to.be.an('object')
        expect(providerInfrastructure.deploy).to.be.a('function')
        expect(providerInfrastructure.nuke).to.be.a('function')
      })

      describe('deploy', () => {
        it('is called with no rockets', async () => {
          replace(infra, 'deploy', fake())

          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const fakeConfig = { fake: 'config' } as any

          if (providerInfrastructure.deploy) await providerInfrastructure.deploy(fakeConfig)

          expect(infra.deploy).to.have.been.calledWith(fakeConfig)
        })

        it('throws an error if the deploy process failed', async () => {
          const errorMessage = new Error('Ooops')
          replace(infra, 'deploy', fake.throws(errorMessage))

          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const fakeConfig = { fake: 'config' } as any

          expect(providerInfrastructure.deploy).to.be.a('function')
          if (providerInfrastructure.deploy)
            await expect(providerInfrastructure.deploy(fakeConfig)).to.be.rejectedWith(errorMessage)
        })
      })

      describe('nuke', () => {
        xit('initializes nuke with no rockets')

        it('throws error if the nuke process fails', async () => {
          const errorMessage = new Error('Ooops')
          replace(infra, 'nuke', fake.throws(errorMessage))

          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const fakeConfig = { fake: 'config' } as any

          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const providerInfrastructureAlias = providerInfrastructure as any
          await expect(providerInfrastructureAlias.nuke(fakeConfig)).to.be.rejectedWith(errorMessage)
        })
      })
    })

    context('with a list of rockets', () => {
      const fakePackageList = [
        {
          packageName: 'some-package-name',
          parameters: {
            some: 'parameters',
          },
        },
      ]

      it('returns a `ProviderInfrastructure` object', () => {
        replace(RocketLoader, 'loadRocket', fake())
        const providerInfrastructure: ProviderInfrastructure = Infrastructure(fakePackageList)

        expect(providerInfrastructure).to.be.an('object')
        expect(providerInfrastructure.deploy).to.be.a('function')
        expect(providerInfrastructure.nuke).to.be.a('function')
      })

      describe('deploy', () => {
        it('is called with rockets', async () => {
          const fakeLoadedRocket = { thisIs: 'aRocket' }
          const fakeLoadRocket = fake.returns(fakeLoadedRocket)
          replace(RocketLoader, 'loadRocket', fakeLoadRocket)

          const providerInfrastructure: ProviderInfrastructure = Infrastructure(fakePackageList)
          replace(infra, 'deploy', fake())

          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const fakeConfig = { fake: 'config' } as any

          if (providerInfrastructure.deploy) {
            await providerInfrastructure.deploy(fakeConfig)
          }

          expect(fakeLoadRocket).to.have.been.calledOnceWith(fakePackageList[0])
          expect(infra.deploy).to.have.been.calledWith(fakeConfig, [fakeLoadedRocket])
        })
      })

      describe('nuke', () => {
        it('is called with rockets', async () => {
          const fakeLoadedRocket = { thisIs: 'aRocket' }
          const fakeLoadRocket = fake.returns(fakeLoadedRocket)
          replace(RocketLoader, 'loadRocket', fakeLoadRocket)

          const providerInfrastructure: ProviderInfrastructure = Infrastructure(fakePackageList)
          replace(infra, 'nuke', fake())

          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const fakeConfig = { fake: 'config' } as any

          if (providerInfrastructure.nuke) {
            await providerInfrastructure.nuke(fakeConfig)
          }

          expect(fakeLoadRocket).to.have.been.calledOnceWith(fakePackageList[0])
          expect(infra.nuke).to.have.been.calledWith(fakeConfig, [fakeLoadedRocket])
        })
      })
    })
  })
})
