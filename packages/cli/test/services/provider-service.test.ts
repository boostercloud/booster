/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { afterEach, describe } from 'mocha'
import * as providerService from '../../src/services/provider-service'
import { restore, fake } from 'sinon'
import { expect } from '../expect'
import { lorem, random } from 'faker'
import * as dynamicLoader from '../../src/services/dynamic-loader'
import { replace } from 'sinon'
import { Logger } from '@boostercloud/framework-types'

const logger: Logger = {
  debug: fake(),
  info: fake(),
  error: fake(),
}

describe('providerService', () => {
  afterEach(() => {
    restore()
  })

  describe('assertNameIsCorrect', () => {
    it('should throw an error on surpassing project name max length', () => {
      const inputString = random.alphaNumeric(random.number({ min: 38 }))
      const errorString = `Project name cannot be longer than 37 chars long:\n\n    Found: '${inputString}'`

      expect(() => providerService.assertNameIsCorrect(inputString)).to.throw(errorString)
    })

    it('should throw an error if project name includes a space', () => {
      const inputString = lorem.words(2)
      const errorString = `Project name cannot contain spaces:\n\n    Found: '${inputString}'`

      expect(() => providerService.assertNameIsCorrect(inputString)).to.throw(errorString)
    })

    it('should throw an error if project name includes an uppercase letter', () => {
      const inputString = random.alphaNumeric(37).toUpperCase()
      const errorString = `Project name cannot contain uppercase letters:\n\n    Found: '${inputString}'`

      expect(() => providerService.assertNameIsCorrect(inputString)).to.throw(errorString)
    })

    it('should not throw an error if project name is correct', () => {
      const inputString = random.alphaNumeric(37)

      expect(() => providerService.assertNameIsCorrect(inputString)).to.not.throw()
    })
  })

  describe('deployToCloudProvider', () => {
    context('when the configured provider implements the `deploy` function', () => {
      context('with no rockets', () => {
        it('loads and initializes the infrastructure module and calls the `deploy` method', async () => {
          const fakeProviderDescription = {
            name: 'some-provider-package',
            version: '3.14.16',
          }
          const fakeDeploy = fake()

          const fakeInfrastructure = {
            Infrastructure: fake.returns({ deploy: fakeDeploy }),
          }

          replace(dynamicLoader, 'dynamicLoad', fake.returns(fakeInfrastructure))

          const fakeProvider = {
            packageDescription: fake.returns(fakeProviderDescription),
          }

          const fakeConfig = {
            appName: 'lolapp',
            provider: fakeProvider,
            rockets: [],
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
          } as any

          await providerService.deployToCloudProvider(fakeConfig, logger)

          expect(fakeProvider.packageDescription).to.have.been.calledOnce
          expect(dynamicLoader.dynamicLoad).to.have.been.calledWith('some-provider-package-infrastructure')
          expect(fakeInfrastructure.Infrastructure).to.have.been.calledWith([])
          expect(fakeDeploy).to.have.been.calledOnceWith(fakeConfig, logger)
        })
      })

      context('with rockets', () => {
        it('loads and initializes the infrastructure module with the rockets and calls the `deploy` method', async () => {
          const fakeProviderDescription = {
            name: 'some-provider-package',
            version: '3.14.16',
          }
          const fakeDeploy = fake()

          const fakeInfrastructure = {
            Infrastructure: fake.returns({ deploy: fakeDeploy }),
          }

          replace(dynamicLoader, 'dynamicLoad', fake.returns(fakeInfrastructure))

          const fakeProvider = {
            packageDescription: fake.returns(fakeProviderDescription),
          }

          const fakeRockets = [
            {
              packageName: 'rocket-package',
              parameters: { some: 'parameter' },
            },
          ]

          const fakeConfig = {
            appName: 'lolapp',
            provider: fakeProvider,
            rockets: fakeRockets,
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
          } as any

          await providerService.deployToCloudProvider(fakeConfig, logger)

          expect(fakeProvider.packageDescription).to.have.been.calledOnce
          expect(dynamicLoader.dynamicLoad).to.have.been.calledWith('some-provider-package-infrastructure')
          expect(fakeInfrastructure.Infrastructure).to.have.been.calledWith(fakeRockets)
          expect(fakeDeploy).to.have.been.calledOnceWith(fakeConfig, logger)
        })
      })
    })

    context('when the configured provider does not implement the `deploy` function', () => {
      it('throws an error', async () => {
        const fakeProviderDescription = {
          name: 'some-provider-package',
          version: '3.14.16',
        }

        const fakeInfrastructure = {
          Infrastructure: fake.returns({}),
        }

        replace(dynamicLoader, 'dynamicLoad', fake.returns(fakeInfrastructure))

        const fakeProvider = {
          packageDescription: fake.returns(fakeProviderDescription),
        }

        const fakeConfig = {
          appName: 'lolapp',
          provider: fakeProvider,
          rockets: [],
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
        } as any

        await expect(providerService.deployToCloudProvider(fakeConfig, logger)).to.eventually.be.rejectedWith(
          "Attempted to perform the 'deploy' operation with a provider that does not support this feature, please check your environment configuration."
        )

        expect(fakeProvider.packageDescription).to.have.been.calledOnce
        expect(dynamicLoader.dynamicLoad).to.have.been.calledWith('some-provider-package-infrastructure')
        expect(fakeInfrastructure.Infrastructure).to.have.been.calledWith([])
      })
    })
  })

  describe('nukeCloudProviderResources', () => {
    context('when the configured provider implements the `nuke` function', () => {
      context('with no rockets', () => {
        it('loads and initializes the infrastructure module and calls the `nuke` method', async () => {
          const fakeProviderDescription = {
            name: 'some-provider-package',
            version: '3.14.16',
          }
          const fakeNuke = fake()

          const fakeInfrastructure = {
            Infrastructure: fake.returns({ nuke: fakeNuke }),
          }

          replace(dynamicLoader, 'dynamicLoad', fake.returns(fakeInfrastructure))

          const fakeProvider = {
            packageDescription: fake.returns(fakeProviderDescription),
          }

          const fakeConfig = {
            appName: 'lolapp',
            provider: fakeProvider,
            rockets: [],
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
          } as any

          await providerService.nukeCloudProviderResources(fakeConfig, logger)

          expect(fakeProvider.packageDescription).to.have.been.calledOnce
          expect(dynamicLoader.dynamicLoad).to.have.been.calledWith('some-provider-package-infrastructure')
          expect(fakeInfrastructure.Infrastructure).to.have.been.calledWith([])
          expect(fakeNuke).to.have.been.calledOnceWith(fakeConfig, logger)
        })
      })

      context('with rockets', () => {
        it('loads and initializes the infrastructure module with the rockets and calls the `nuke` method', async () => {
          const fakeProviderDescription = {
            name: 'some-provider-package',
            version: '3.14.16',
          }
          const fakeNuke = fake()

          const fakeInfrastructure = {
            Infrastructure: fake.returns({ nuke: fakeNuke }),
          }

          replace(dynamicLoader, 'dynamicLoad', fake.returns(fakeInfrastructure))

          const fakeProvider = {
            packageDescription: fake.returns(fakeProviderDescription),
          }

          const fakeRockets = [
            {
              packageName: 'rocket-package',
              parameters: { some: 'parameter' },
            },
          ]

          const fakeConfig = {
            appName: 'lolapp',
            provider: fakeProvider,
            rockets: fakeRockets,
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
          } as any

          await providerService.nukeCloudProviderResources(fakeConfig, logger)

          expect(fakeProvider.packageDescription).to.have.been.calledOnce
          expect(dynamicLoader.dynamicLoad).to.have.been.calledWith('some-provider-package-infrastructure')
          expect(fakeInfrastructure.Infrastructure).to.have.been.calledWith(fakeRockets)
          expect(fakeNuke).to.have.been.calledOnceWith(fakeConfig, logger)
        })
      })
    })

    context('when the configured provider does not implement the `nuke` function', () => {
      it('throws an error', async () => {
        const fakeProviderDescription = {
          name: 'some-provider-package',
          version: '3.14.16',
        }

        const fakeInfrastructure = {
          Infrastructure: fake.returns({}),
        }

        replace(dynamicLoader, 'dynamicLoad', fake.returns(fakeInfrastructure))

        const fakeProvider = {
          packageDescription: fake.returns(fakeProviderDescription),
        }

        const fakeConfig = {
          appName: 'lolapp',
          provider: fakeProvider,
          rockets: [],
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
        } as any

        await expect(providerService.nukeCloudProviderResources(fakeConfig, logger)).to.be.eventually.rejectedWith(
          "Attempted to perform the 'nuke' operation with a provider that does not support this feature, please check your environment configuration."
        )

        expect(fakeProvider.packageDescription).to.have.been.calledOnce
        expect(dynamicLoader.dynamicLoad).to.have.been.calledWith('some-provider-package-infrastructure')
        expect(fakeInfrastructure.Infrastructure).to.have.been.calledWith([])
      })
    })
  })

  describe('startProvider', () => {
    context('when the configured provider implements the `start` function', () => {
      it('calls the provider start method', async () => {
        const fakeProviderDescription = {
          name: 'some-provider-package',
          version: '3.14.16',
        }
        const fakeStart = fake()

        const fakeInfrastructure = {
          Infrastructure: fake.returns({ start: fakeStart }),
        }

        replace(dynamicLoader, 'dynamicLoad', fake.returns(fakeInfrastructure))

        const fakeProvider = {
          packageDescription: fake.returns(fakeProviderDescription),
        }

        const fakeConfig = {
          appName: 'lolapp',
          provider: fakeProvider,
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
        } as any

        await providerService.startProvider(3000, fakeConfig)

        expect(fakeProvider.packageDescription).to.have.been.calledOnce
        expect(dynamicLoader.dynamicLoad).to.have.been.calledWith('some-provider-package-infrastructure')
        expect(fakeStart).to.have.been.calledOnceWith(fakeConfig, 3000)
      })
    })

    context('when the configured provider does not implement the `start` function', () => {
      it('throws an error', async () => {
        const fakeProviderDescription = {
          name: 'some-provider-package',
          version: '3.14.16',
        }

        const fakeInfrastructure = {
          Infrastructure: fake.returns({}),
        }

        replace(dynamicLoader, 'dynamicLoad', fake.returns(fakeInfrastructure))

        const fakeProvider = {
          packageDescription: fake.returns(fakeProviderDescription),
        }

        const fakeConfig = {
          appName: 'lolapp',
          provider: fakeProvider,
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
        } as any

        await expect(providerService.startProvider(3000, fakeConfig)).to.be.eventually.rejectedWith(
          "Attempted to perform the 'start' operation with a provider that does not support this feature, please check your environment configuration."
        )

        expect(fakeProvider.packageDescription).to.have.been.calledOnce
        expect(dynamicLoader.dynamicLoad).to.have.been.calledWith('some-provider-package-infrastructure')
      })
    })
  })
})
