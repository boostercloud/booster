import { fake, replace, restore, SinonStub, stub } from 'sinon'
import * as projectChecker from '../../src/services/project-checker'
import { BoosterConfig } from '@boostercloud/framework-types'
import { expect } from '../expect'
import * as environment from '../../src/services/environment'
import * as dynamicLoader from '../../src/services/dynamic-loader'

const rewire = require('rewire')
const configService = rewire('../../src/services/config-service')

describe('configService', () => {
  afterEach(() => {
    restore()
  })

  describe('compileProjectAndLoadConfig', () => {
    let checkItIsABoosterProject: SinonStub

    beforeEach(() => {
      checkItIsABoosterProject = stub(projectChecker, 'checkItIsABoosterProject').resolves()
    })

    it('loads the config when the selected environment exists', async () => {
      const config = new BoosterConfig('test')

      const unRewireCompileProject = configService.__set__('compileProject', fake())

      replace(
        dynamicLoader,
        'dynamicLoad',
        fake.returns({
          Booster: {
            config: config,
            configuredEnvironments: new Set(['test']),
            configureCurrentEnv: fake.yields(config),
          },
        })
      )
      replace(environment, 'currentEnvironment', fake.returns('test'))

      await expect(configService.compileProjectAndLoadConfig()).to.eventually.become(config)
      expect(checkItIsABoosterProject).to.have.been.calledOnce

      unRewireCompileProject()
    })

    it('throws the right error when there are not configured environments', async () => {
      const config = new BoosterConfig('test')

      const unRewireCompileProject = configService.__set__('compileProject', fake())

      replace(
        dynamicLoader,
        'dynamicLoad',
        fake.returns({
          Booster: {
            config: config,
            configuredEnvironments: new Set([]),
            configureCurrentEnv: fake.yields(config),
          },
        })
      )
      await expect(configService.compileProjectAndLoadConfig()).to.eventually.be.rejectedWith(
        /You haven't configured any environment/
      )
      expect(checkItIsABoosterProject).to.have.been.calledOnceWithExactly()

      unRewireCompileProject()
    })

    it('throws the right error when the environment does not exist', async () => {
      const config = new BoosterConfig('test')

      const unRewireCompileProject = configService.__set__('compileProject', fake())

      replace(
        dynamicLoader,
        'dynamicLoad',
        fake.returns({
          Booster: {
            config: config,
            configuredEnvironments: new Set(['another']),
            configureCurrentEnv: fake.yields(config),
          },
        })
      )
      replace(environment, 'currentEnvironment', fake.returns('test'))

      await expect(configService.compileProjectAndLoadConfig()).to.eventually.be.rejectedWith(
        /The environment 'test' does not match any of the environments/
      )
      expect(checkItIsABoosterProject).to.have.been.calledOnceWithExactly()

      unRewireCompileProject()
    })
  })
})
