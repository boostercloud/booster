import { fake, replace, restore, SinonSpy, SinonStub, stub } from 'sinon'
import * as projectChecker from '../../src/services/project-checker'
import { BoosterConfig } from '@boostercloud/framework-types'
import { expect } from '../expect'
import * as environment from '../../src/services/environment'
import * as dependencies from '../../src/services/dependencies'

const rewire = require('rewire')
const configService = rewire('../../src/services/config-service')

describe('configService', () => {
  const userProjectPath = 'path/to/project'
  let fakeInstallProductionDependencies: SinonSpy
  beforeEach(() => {
    fakeInstallProductionDependencies = fake()
    replace(dependencies, 'installProductionDependencies', fakeInstallProductionDependencies)
  })
  afterEach(() => {
    restore()
  })

  describe('checkAndCompileProject', () => {
    let checkItIsABoosterProject: SinonStub

    beforeEach(() => {
      checkItIsABoosterProject = stub(projectChecker, 'checkItIsABoosterProject').resolves()
    })

    it('loads the config when the selected environment exists', async () => {
      const config = new BoosterConfig('build')

      const rewires = [
        configService.__set__('compileProject', fake())
      ]

      replace(environment, 'currentEnvironment', fake.returns('test'))

      await expect(configService.checkAndCompileProject(userProjectPath)).to.eventually.become(config)
      expect(checkItIsABoosterProject).to.have.been.calledOnceWithExactly(userProjectPath)

      rewires.forEach((fn) => fn())
    })
  })

  describe('compileProjectAndLoadConfig', () => {
    let checkItIsABoosterProject: SinonStub

    beforeEach(() => {
      checkItIsABoosterProject = stub(projectChecker, 'checkItIsABoosterProject').resolves()
    })

    it('loads the config when the selected environment exists', async () => {
      const config = new BoosterConfig('test')

      const rewires = [
        configService.__set__('compileProject', fake()),
        configService.__set__(
          'loadUserProject',
          fake.returns({
            Booster: {
              config: config,
              configuredEnvironments: new Set(['test']),
              configureCurrentEnv: fake.yields(config),
            },
          })
        ),
      ]

      replace(environment, 'currentEnvironment', fake.returns('test'))

      await expect(configService.compileProjectAndLoadConfig(userProjectPath)).to.eventually.become(config)
      expect(checkItIsABoosterProject).to.have.been.calledOnceWithExactly(userProjectPath)

      rewires.forEach((fn) => fn())
    })

    it('throws the right error when there are not configured environments', async () => {
      const config = new BoosterConfig('test')

      const rewires = [
        configService.__set__('compileProject', fake()),
        configService.__set__(
          'loadUserProject',
          fake.returns({
            Booster: {
              config: config,
              configuredEnvironments: new Set([]),
              configureCurrentEnv: fake.yields(config),
            },
          })
        ),
      ]

      await expect(configService.compileProjectAndLoadConfig(userProjectPath)).to.eventually.be.rejectedWith(
        /You haven't configured any environment/
      )
      expect(checkItIsABoosterProject).to.have.been.calledOnceWithExactly(userProjectPath)

      rewires.forEach((fn) => fn())
    })

    it('throws the right error when the environment does not exist', async () => {
      const config = new BoosterConfig('test')

      const rewires = [
        configService.__set__('compileProject', fake()),
        configService.__set__(
          'loadUserProject',
          fake.returns({
            Booster: {
              config: config,
              configuredEnvironments: new Set(['another']),
              configureCurrentEnv: fake.yields(config),
            },
          })
        ),
      ]

      replace(environment, 'currentEnvironment', fake.returns('test'))

      await expect(configService.compileProjectAndLoadConfig(userProjectPath)).to.eventually.be.rejectedWith(
        /The environment 'test' does not match any of the environments/
      )
      expect(checkItIsABoosterProject).to.have.been.calledOnceWithExactly(userProjectPath)

      rewires.forEach((fn) => fn())
    })
  })
})
