import { fake } from 'sinon'
import { Effect, gen, Layer, mapError, pipe, unsafeRunEffect } from '@boostercloud/framework-types/dist/effect'
import { expect } from '../../expect'
import { makeTestFileSystem } from '../file-system/test.impl'
import { makeTestProcess } from '../process/test.impl'
import { YarnPackageManager } from '../../../src/services/package-manager/yarn.impl'
import { guardError } from '../../../src/common/errors'
import { PackageManagerService } from '../../../src/services/package-manager'

const TestFileSystem = makeTestFileSystem()
const TestProcess = makeTestProcess()

const mapEffError = <R, A>(effect: Effect<R, { error: Error }, A>) =>
  pipe(
    effect,
    mapError((e) => e.error)
  )

describe('PackageManager - Yarn Implementation', () => {
  beforeEach(() => {})

  it('run arbitrary scripts from package.json', async () => {
    const script = 'script'
    const args = ['arg1', 'arg2']
    const testLayer = Layer.all(TestFileSystem.layer, TestProcess.layer)

    const effect = gen(function* ($) {
      const { runScript } = yield* $(PackageManagerService)
      return yield* $(runScript(script, args))
    })

    await unsafeRunEffect(mapEffError(effect), {
      layer: Layer.using(testLayer)(YarnPackageManager),
      onError: guardError('An error ocurred'),
    })
    expect(TestProcess.fakes.exec).to.have.been.calledWith(`yarn run ${script} ${args.join(' ')}`)
  })

  describe('when the `compile` script exists', () => {
    const TestFileSystemWithCompileScript = makeTestFileSystem({
      readFileContents: fake.returns('{"scripts": {"compile": "tsc"}}'),
    })

    it('run the `compile` script', async () => {
      const testLayer = Layer.all(TestFileSystemWithCompileScript.layer, TestProcess.layer)

      const effect = gen(function* ($) {
        const { build } = yield* $(PackageManagerService)
        return yield* $(build([]))
      })

      await unsafeRunEffect(mapEffError(effect), {
        layer: Layer.using(testLayer)(YarnPackageManager),
        onError: guardError('An error ocurred'),
      })
      expect(TestProcess.fakes.exec).to.have.been.calledWith('yarn run compile')
    })
  })

  describe('when the `compile` script does not exist', () => {
    it('run the `build` script', async () => {
      const testLayer = Layer.all(TestFileSystem.layer, TestProcess.layer)

      const effect = gen(function* ($) {
        const { build } = yield* $(PackageManagerService)
        return yield* $(build([]))
      })

      await unsafeRunEffect(mapEffError(effect), {
        layer: Layer.using(testLayer)(YarnPackageManager),
        onError: guardError('An error ocurred'),
      })
      expect(TestProcess.fakes.exec).to.have.been.calledWith('yarn run build')
    })
  })

  it('can set the project root properly', async () => {
    const projectRoot = 'projectRoot'

    const CwdTestProcess = makeTestProcess({ cwd: fake.returns(projectRoot) })
    const testLayer = Layer.all(TestFileSystem.layer, CwdTestProcess.layer)

    const effect = gen(function* ($) {
      const { setProjectRoot, runScript } = yield* $(PackageManagerService)
      yield* $(setProjectRoot(projectRoot))
      yield* $(runScript('script', []))
    })

    await unsafeRunEffect(mapEffError(effect), {
      layer: Layer.using(testLayer)(YarnPackageManager),
      onError: guardError('An error ocurred'),
    })
    expect(CwdTestProcess.fakes.exec).to.have.been.calledWith('yarn run script', projectRoot)
  })

  it('can install production dependencies', async () => {
    const testLayer = Layer.all(TestFileSystem.layer, TestProcess.layer)

    const effect = gen(function* ($) {
      const { installProductionDependencies } = yield* $(PackageManagerService)
      return yield* $(installProductionDependencies())
    })

    await unsafeRunEffect(mapEffError(effect), {
      layer: Layer.using(testLayer)(YarnPackageManager),
      onError: guardError('An error ocurred'),
    })

    expect(TestProcess.fakes.exec).to.have.been.calledWith('yarn install --omit=dev --omit=optional --no-bin-links')
  })

  it('can install all dependencies', async () => {
    const testLayer = Layer.all(TestFileSystem.layer, TestProcess.layer)

    const effect = gen(function* ($) {
      const { installAllDependencies } = yield* $(PackageManagerService)
      return yield* $(installAllDependencies())
    })

    await unsafeRunEffect(mapEffError(effect), {
      layer: Layer.using(testLayer)(YarnPackageManager),
      onError: guardError('An error ocurred'),
    })

    expect(TestProcess.fakes.exec).to.have.been.calledWith('yarn install')
  })
})
