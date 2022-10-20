import { fake, replace, restore, SinonStub, stub } from 'sinon'
import * as projectChecker from '../../src/services/project-checker'
import { BoosterConfig } from '@boostercloud/framework-types'
import { expect } from '../expect'
import * as environment from '../../src/services/environment'
import * as childProcessPromise from 'child-process-promise'
// import { NpmPackageManager } from '../../src/services/package-manager/npm.impl'
// import { LiveProcess } from '../../src/services/process/live.impl'
// import { LiveFileSystem } from '../../src/services/file-system/live.impl'
// import { Layer } from '@boostercloud/framework-types/src/effect'

const rewire = require('rewire')
const configService = rewire('../../src/services/config-service')

describe.only('configService', () => {
  const userProjectPath = 'path/to/project'
  // let fakeInstallProductionDependencies: SinonSpy
  beforeEach(() => {
    // fakeInstallProductionDependencies = fake()
    // replace(dependencies, 'installProductionDependencies', fakeInstallProductionDependencies)
    // TODO: Don't use live services when testing. This should be fixed when we finish migrating all the services to the new effect system
    // const testLayer = Layer.all(LiveProcess, LiveFileSystem)
    // replace(configService, 'UsedLayer', Layer.using(testLayer)(NpmPackageManager))
  })
  afterEach(() => {
    restore()
  })

  describe('compileProject', () => {
    beforeEach(() => {
      replace(childProcessPromise, 'exec', fake.resolves({}))
    })

    it('runs the npm command', async () => {
      const result = await configService.compileProject(userProjectPath)
      // expect(childProcessPromise.exec).to.have.callCount('npm run clean && npm run build')
      expect(childProcessPromise.exec).to.have.calledWith('npm run clean')
      expect(childProcessPromise.exec).to.have.calledWith('npm run build')
      expect(result).to.not.be.undefined
    })
  })

  describe('cleanProject', () => {
    beforeEach(() => {
      replace(childProcessPromise, 'exec', fake.resolves({}))
    })

    it('runs the npm command', async () => {
      await configService.cleanProject(userProjectPath)
      expect(childProcessPromise.exec).to.have.been.calledWith('npm run clean')
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
