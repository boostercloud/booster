import { Logger, ProviderInfrastructure } from '@boostercloud/framework-types'
import { fake, replace, restore } from 'sinon'
import { Infrastructure } from '../src/index'
import * as pluginLoader from '../src/infrastructure-plugin'
import { expect } from './expect'
import * as infra from '../src/infrastructure'

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
    context('with no plugins', () => {
      const providerInfrastructure: ProviderInfrastructure = Infrastructure()

      it('returns a `ProviderInfrastructure` object', () => {
        expect(providerInfrastructure).to.be.an('object')
        expect(providerInfrastructure.deploy).to.be.a('function')
        expect(providerInfrastructure.nuke).to.be.a('function')
      })

      describe('deploy', () => {
        it('is called with no plugins', () => {
          replace(infra, 'deploy', fake())

          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const fakeConfig = { fake: 'config' } as any

          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const providerInfrastructureAlias = providerInfrastructure as any
          providerInfrastructureAlias.deploy(fakeConfig, logger)

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

          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const providerInfrastructureAlias = providerInfrastructure as any
          await expect(providerInfrastructureAlias.deploy(fakeConfig, logger)).not.to.eventually.be.rejected

          // It receives the thrown Error object, not just the message
          expect(logger.error).to.have.been.calledWithMatch(errorMessage)
        })
      })

      describe('nuke', () => {
        xit('initializes nuke with no plugins')

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
        it('is called with plugins', () => {
          replace(infra, 'deploy', fake())
          const fakeLoadedPlugin = { thisIs: 'aPlugin' }

          replace(pluginLoader, 'loadPlugin', fake.returns(fakeLoadedPlugin))
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const fakeConfig = { fake: 'config' } as any

          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const providerInfrastructureAlias = providerInfrastructure as any
          providerInfrastructureAlias.deploy(fakeConfig, logger)

          expect(pluginLoader.loadPlugin).to.have.been.calledOnceWith(fakePackageList[0])
          expect(infra.deploy).to.have.been.calledWith(fakeConfig, logger, [fakeLoadedPlugin])
        })
      })
    })
  })
})
