import { Logger, ProviderInfrastructure } from '@boostercloud/framework-types'
import { fake, replace, restore } from 'sinon'
import { expect } from './expect'
import * as infra from '../src/infrastructure'
import * as infrastructureRocket from '../src/rockets/infrastructure-rocket'

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
        expect(providerInfrastructure).to.be.an('object')
        expect(providerInfrastructure.deploy).to.be.a('function')
        expect(providerInfrastructure.nuke).to.be.a('function')
      })

      describe('deploy', () => {
        it('is called with no rockets', async () => {
          replace(infra, 'deploy', fake())

          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const fakeConfig = { fake: 'config' } as any

          if (providerInfrastructure.deploy) await providerInfrastructure.deploy(fakeConfig, logger)

          expect(infra.deploy).to.have.been.calledWith(fakeConfig, logger)
        })

        it('logs an error through the passed logger when an error is thrown', async () => {
          const errorMessage = new Error('Ooops')
          replace(infra, 'deploy', fake.throws(errorMessage))

          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const fakeConfig = { fake: 'config' } as any

          const logger: Logger = {
            info: fake(),
            error: fake(),
            debug: fake(),
          }

          expect(providerInfrastructure.deploy).to.be.a('function')
          if (providerInfrastructure.deploy)
            await expect(providerInfrastructure.deploy(fakeConfig, logger)).not.to.eventually.be.rejected

          // It receives the thrown Error object, not just the message
          expect(logger.error).to.have.been.calledWithMatch(errorMessage)
        })
      })

      describe('nuke', () => {
        xit('initializes nuke with no rockets')

        it('logs an error through the passed logger when an error is thrown', async () => {
          const errorMessage = new Error('Ooops')
          replace(infra, 'nuke', fake.throws(errorMessage))

          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const fakeConfig = { fake: 'config' } as any

          const logger: Logger = {
            info: fake(),
            error: fake(),
            debug: fake(),
          }

          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const providerInfrastructureAlias = providerInfrastructure as any
          await expect(providerInfrastructureAlias.nuke(fakeConfig, logger)).not.to.eventually.be.rejected

          // It receives the thrown Error object, not just the message
          expect(logger.error).to.have.been.calledWithMatch(errorMessage)
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
        replace(infrastructureRocket, 'loadRocket', fake())
        const providerInfrastructure: ProviderInfrastructure = infrastructure.Infrastructure(fakePackageList)

        expect(providerInfrastructure).to.be.an('object')
        expect(providerInfrastructure.deploy).to.be.a('function')
        expect(providerInfrastructure.nuke).to.be.a('function')
      })

      describe('deploy', () => {
        it('is called with rockets', async () => {
          const fakeLoadedRocket = { thisIs: 'aRocket' }
          const fakeLoadRocket = fake.returns(fakeLoadedRocket)
          replace(infrastructureRocket, 'loadRocket', fakeLoadRocket)

          const providerInfrastructure: ProviderInfrastructure = infrastructure.Infrastructure(fakePackageList)
          replace(infra, 'deploy', fake())

          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const fakeConfig = { fake: 'config' } as any

          if (providerInfrastructure.deploy) {
            await providerInfrastructure.deploy(fakeConfig, logger)
          }

          expect(fakeLoadRocket).to.have.been.calledOnceWith(fakePackageList[0])
          expect(infra.deploy).to.have.been.calledWith(fakeConfig, logger, [fakeLoadedRocket])
        })
      })

      describe('nuke', () => {
        it('is called with rockets', async () => {
          const fakeLoadedRocket = { thisIs: 'aRocket' }
          const fakeLoadRocket = fake.returns(fakeLoadedRocket)
          replace(infrastructureRocket, 'loadRocket', fakeLoadRocket)

          const providerInfrastructure: ProviderInfrastructure = infrastructure.Infrastructure(fakePackageList)
          replace(infra, 'nuke', fake())

          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const fakeConfig = { fake: 'config' } as any

          if (providerInfrastructure.nuke) {
            await providerInfrastructure.nuke(fakeConfig, logger)
          }

          expect(fakeLoadRocket).to.have.been.calledOnceWith(fakePackageList[0])
          expect(infra.nuke).to.have.been.calledWith(fakeConfig, logger, [fakeLoadedRocket])
        })
      })
    })
  })
})
