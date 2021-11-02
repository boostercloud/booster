import { Logger, ProviderInfrastructure } from '@boostercloud/framework-types'
import { fake, restore, replace } from 'sinon'
import { expect } from './expect'
import * as Deployer from '../src/infrastructure/deploy'
import * as Nuker from '../src/infrastructure/nuke'
import { AWSProviderContext } from '../src/infrastructure/provider-context/aws-provider-context'

// We need to import the exact file instead of the module for Sinon.replace to work
const rewire = require('rewire')
const infrastructure = rewire('../src/index')

const logger = {
  info: fake(),
  debug: fake(),
  error: fake(),
} as Logger

describe('the `framework-provider-aws-infrastructure` package', () => {
  afterEach(() => {
    restore()
  })

  describe('the `Infrastructure` function', () => {
    context('with no rockets', () => {
      const providerInfrastructure: ProviderInfrastructure = infrastructure.Infrastructure()

      it('returns a `ProviderInfrastructure` object', () => {
        const fakeLoadRockets = fake()
        const undoRewire = infrastructure.__set__('loadRockets', fakeLoadRockets)
        expect(providerInfrastructure).to.be.an('object')
        expect(providerInfrastructure.deploy).to.be.a('function')
        expect(providerInfrastructure.nuke).to.be.a('function')
        expect(fakeLoadRockets).not.to.have.been.called
        undoRewire()
      })

      describe('deploy', () => {
        it('is called with no rockets', async () => {
          const fakeLoadRockets = fake()
          const undoRewire = infrastructure.__set__('loadRockets', fakeLoadRockets)
          replace(Deployer, 'deploy', fake())

          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const fakeConfig = { fake: 'config' } as any

          if (providerInfrastructure.deploy) await providerInfrastructure.deploy(fakeConfig, logger)

          expect(fakeLoadRockets).not.to.have.been.called
          expect(Deployer.deploy).to.have.been.calledWith(fakeConfig, logger)
          undoRewire()
        })

        it('throws an error if the deploy process failed', async () => {
          const fakeLoadRockets = fake()
          const undoRewire = infrastructure.__set__('loadRockets', fakeLoadRockets)
          const errorMessage = new Error('Ooops')
          replace(Deployer, 'deploy', fake.throws(errorMessage))

          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const fakeConfig = { fake: 'config' } as any

          expect(providerInfrastructure.deploy).to.be.a('function')
          if (providerInfrastructure.deploy)
            await expect(providerInfrastructure.deploy(fakeConfig, logger)).to.be.rejectedWith(errorMessage)
          expect(fakeLoadRockets).not.to.have.been.called
          undoRewire()
        })
      })

      describe('nuke', () => {
        it('initializes nuke with no rockets', async () => {
          const fakeLoadRockets = fake()
          const undoRewire = infrastructure.__set__('loadRockets', fakeLoadRockets)
          replace(Nuker, 'nuke', fake())

          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const fakeConfig = { fake: 'config' } as any

          if (providerInfrastructure.nuke) await providerInfrastructure.nuke(fakeConfig, logger)

          expect(fakeLoadRockets).not.to.have.been.called
          expect(Nuker.nuke).to.have.been.calledWith(fakeConfig, logger)
          undoRewire()
        })

        it('throws error if the nuke process fails', async () => {
          const fakeLoadRockets = fake()
          const undoRewire = infrastructure.__set__('loadRockets', fakeLoadRockets)
          const errorMessage = new Error('Ooops')
          replace(Nuker, 'nuke', fake.throws(errorMessage))

          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const fakeConfig = { fake: 'config' } as any

          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const providerInfrastructureAlias = providerInfrastructure as any
          await expect(providerInfrastructureAlias.nuke(fakeConfig, logger)).to.be.rejectedWith(errorMessage)
          expect(fakeLoadRockets).not.to.have.been.called
          undoRewire()
        })
      })
    })

    context('with a list of rockets', () => {
      const fakeRocketDescriptor = {
        context: {} as AWSProviderContext,
        packageName: 'some-package-name',
        parameters: {
          some: 'parameters',
        },
      }

      it('returns a `ProviderInfrastructure` object', () => {
        const fakeLoadRockets = fake()
        const undoRewire = infrastructure.__set__('loadRockets', fakeLoadRockets)
        const providerInfrastructure: ProviderInfrastructure = infrastructure.Infrastructure([fakeRocketDescriptor])

        expect(providerInfrastructure).to.be.an('object')
        expect(providerInfrastructure.deploy).to.be.a('function')
        expect(providerInfrastructure.nuke).to.be.a('function')
        expect(fakeLoadRockets).to.have.been.calledOnceWith([fakeRocketDescriptor])
        undoRewire()
      })

      describe('deploy', () => {
        it('is called with rockets', async () => {
          const fakeLoadedRocket = { thisIs: 'aRocket' }
          const fakeLoadRockets = fake.returns([fakeLoadedRocket])
          const undoRewire = infrastructure.__set__('loadRockets', fakeLoadRockets)

          const providerInfrastructure: ProviderInfrastructure = infrastructure.Infrastructure([fakeRocketDescriptor])
          replace(Deployer, 'deploy', fake())

          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const fakeConfig = { fake: 'config' } as any

          if (providerInfrastructure.deploy) {
            await providerInfrastructure.deploy(fakeConfig, logger)
          }

          expect(fakeLoadRockets).to.have.been.calledOnceWith([fakeRocketDescriptor])
          expect(Deployer.deploy).to.have.been.calledWith(fakeConfig, logger, [fakeLoadedRocket])

          undoRewire()
        })
      })

      describe('nuke', () => {
        it('is called with rockets', async () => {
          const fakeLoadedRocket = { thisIs: 'aRocket' }
          const fakeLoadRockets = fake.returns([fakeLoadedRocket])
          const undoRewire = infrastructure.__set__('loadRockets', fakeLoadRockets)

          const providerInfrastructure: ProviderInfrastructure = infrastructure.Infrastructure([fakeRocketDescriptor])
          replace(Nuker, 'nuke', fake())

          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const fakeConfig = { fake: 'config' } as any

          if (providerInfrastructure.nuke) {
            await providerInfrastructure.nuke(fakeConfig, logger)
          }

          expect(fakeLoadRockets).to.have.been.calledOnceWith([fakeRocketDescriptor])
          expect(Nuker.nuke).to.have.been.calledWith(fakeConfig, logger, [fakeLoadedRocket])
          undoRewire()
        })
      })
    })
  })
})
