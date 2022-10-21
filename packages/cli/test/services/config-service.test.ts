import { fake, replace, restore, SinonStub, stub } from 'sinon'
import * as projectChecker from '../../src/services/project-checker'
import { BoosterConfig } from '@boostercloud/framework-types'
import { expect } from '../expect'
import * as environment from '../../src/services/environment'
import { TestPackageManager } from './package-manager/test.impl'
import * as PackageManager from '../../src/services/package-manager/live.impl'
// import { LiveProcess } from '../../src/services/process/live.impl'
// import { LiveFileSystem } from '../../src/services/file-system/live.impl'
// import { Layer } from '@boostercloud/framework-types/src/effect'

const rewire = require('rewire')
const configService = rewire('../../src/services/config-service')

describe.only('configService', () => {
  const userProjectPath = 'path/to/project'
  const packageManager = TestPackageManager()
  // let fakeInstallProductionDependencies: SinonSpy
  before(() => {
    // fakeInstallProductionDependencies = fake()
    // replace(dependencies, 'installProductionDependencies', fakeInstallProductionDependencies)
    // TODO: Don't use live services when testing. This should be fixed when we finish migrating all the services to the new effect system
    // const testLayer = Layer.all(LiveProcess, LiveFileSystem)
    replace(PackageManager, 'LivePackageManager', packageManager.layer)
  })
  afterEach(() => {
    restore()
    for (const fake of Object.values(packageManager.fakes)) {
      fake.resetHistory()
    }
  })

  describe('compileProject', () => {
    it('runs the npm command', async () => {
      await configService.compileProject(userProjectPath)
      expect(packageManager.fakes.runScript).to.have.calledWith('clean')
      expect(packageManager.fakes.runScript).to.have.calledWith('build')
    })
  })

  describe('cleanProject', () => {
    it('runs the npm command', async () => {
      await configService.cleanProject(userProjectPath)
      expect(packageManager.fakes.runScript).to.have.been.calledWith('clean')
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
