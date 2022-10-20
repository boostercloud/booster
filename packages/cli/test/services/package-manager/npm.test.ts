import { fake } from 'sinon'
import { gen, Layer, unsafeRunEffect } from '@boostercloud/framework-types/src/effect'
import { expect } from '../../expect'
import { TestFileSystem } from '../file-system/test.impl'
import { TestProcess } from '../process/test.impl'
import { packageManagerInternals } from '../../../src/services/package-manager'
import { NpmPackageManager } from '../../../src/services/package-manager/npm.impl'

describe('PackageManager - npm Implementation', () => {
  beforeEach(() => {})

  it('run arbitrary scripts from package.json', () => {
    const script = 'script'
    const args = ['arg1', 'arg2']
    const fakeFileSystem = TestFileSystem()
    const fakeProcess = TestProcess()
    const testLayer = Layer.all(fakeFileSystem.layer, fakeProcess.layer)
    const { runScript } = packageManagerInternals

    unsafeRunEffect(runScript(script, args), {
      layer: Layer.using(testLayer)(NpmPackageManager),
      onError: (error) => {
        throw error
      },
    })
    expect(fakeProcess.fakes.exec).to.have.been.calledWith(`npm run ${script} ${args.join(' ')}`, { cwd: '' })
  })

  it('can set the project root properly', () => {
    const projectRoot = 'projectRoot'

    const fakeFileSystem = TestFileSystem()
    const fakeProcess = TestProcess({ cwd: fake.resolves(projectRoot) })
    const testLayer = Layer.all(fakeFileSystem.layer, fakeProcess.layer)
    const { setProjectRoot, runScript } = packageManagerInternals

    const effect = gen(function* ($) {
      yield* $(setProjectRoot(projectRoot))
      yield* $(runScript('script', []))
    })

    unsafeRunEffect(effect, {
      layer: Layer.using(testLayer)(NpmPackageManager),
      onError: (error) => {
        throw error
      },
    })
    expect(fakeProcess.fakes.exec).to.have.been.calledWith('npm run script', { cwd: projectRoot })
  })

  it('can install production dependencies', () => {
    const fakeFileSystem = TestFileSystem()
    const fakeProcess = TestProcess()
    const testLayer = Layer.all(fakeFileSystem.layer, fakeProcess.layer)
    const { installProductionDependencies } = packageManagerInternals

    unsafeRunEffect(installProductionDependencies(), {
      layer: Layer.using(testLayer)(NpmPackageManager),
      onError: (error) => {
        throw error
      },
    })

    expect(fakeProcess.fakes.exec).to.have.been.calledWith('npm install --production --no-bin-links --no-optional', {
      cwd: '',
    })
  })

  it('can install all dependencies', () => {
    const fakeFileSystem = TestFileSystem()
    const fakeProcess = TestProcess()
    const testLayer = Layer.all(fakeFileSystem.layer, fakeProcess.layer)
    const { installAllDependencies } = packageManagerInternals

    unsafeRunEffect(installAllDependencies(), {
      layer: Layer.using(testLayer)(NpmPackageManager),
      onError: (error) => {
        throw error
      },
    })

    expect(fakeProcess.fakes.exec).to.have.been.calledWith('npm install', {
      cwd: '',
    })
  })
})
