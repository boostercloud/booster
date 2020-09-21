import { ProviderInfrastructure } from '@boostercloud/framework-types'
import { fake, replace, restore } from 'sinon'
import { Infrastructure } from '../src/index'
import * as infrastructureFunctions from '../src/infrastructure/'
import * as pluginLoader from '../src/infrastructure-plugin'
import { expect } from './expect'

describe('the `framework-provider-aws-infrastructure` package', () => {
  afterEach(() => {
    restore()
  })

  describe('the `Infrastructure` function', () => {
    context('with no plugins', () => {
      const providerInfrastructure: ProviderInfrastructure = Infrastructure()

      it('returns a `ProviderInfrastructure` object', () => {
        expect(providerInfrastructure).to.be.an('object')
        expect(providerInfrastructure.deploy).to.be.a('function')
        expect(providerInfrastructure.nuke).to.be.a('function')
      })

      describe('deploy', () => {
        it('is called with no plugins', () => {
          replace(infrastructureFunctions, 'deploy', fake())
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const fakeConfig = { fake: 'config' } as any

          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const providerInfrastructureAlias = providerInfrastructure as any
          providerInfrastructureAlias.deploy(fakeConfig)

          expect(infrastructureFunctions.deploy).to.have.been.calledWith(fakeConfig)
        })
      })
    })

    context('with a list of plugins', () => {
      const fakePackageList = [
        {
          packageName: 'some-package-name',
          parameters: {
            some: 'parameters',
          },
        },
      ]
      const providerInfrastructure: ProviderInfrastructure = Infrastructure(fakePackageList)

      it('returns a `ProviderInfrastructure` object', () => {
        expect(providerInfrastructure).to.be.an('object')
        expect(providerInfrastructure.deploy).to.be.a('function')
        expect(providerInfrastructure.nuke).to.be.a('function')
      })

      describe('deploy', () => {
        it('is called with no plugins', () => {
          const fakeLoadedPlugin = { thisIs: 'aPlugin' }
          replace(infrastructureFunctions, 'deploy', fake())
          replace(pluginLoader, 'loadPlugin', fake.returns(fakeLoadedPlugin))
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const fakeConfig = { fake: 'config' } as any

          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const providerInfrastructureAlias = providerInfrastructure as any
          providerInfrastructureAlias.deploy(fakeConfig)

          expect(pluginLoader.loadPlugin).to.have.been.calledOnceWith(fakePackageList[0])
          expect(infrastructureFunctions.deploy).to.have.been.calledWith(fakeConfig, [fakeLoadedPlugin])
        })
      })
    })
  })
})
