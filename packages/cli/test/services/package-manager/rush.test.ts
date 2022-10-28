import { fake } from 'sinon'
import { gen, Layer, unsafeRunEffect } from '@boostercloud/framework-types/dist/effect'
import { expect } from '../../expect'
import { makeTestFileSystem } from '../file-system/test.impl'
import { makeTestProcess } from '../process/test.impl'
import { RushPackageManager } from '../../../src/services/package-manager/rush.impl'
import { guardError } from '../../../src/common/errors'
import { PackageManagerService } from '../../../src/services/package-manager'

const TestFileSystem = makeTestFileSystem()
const TestProcess = makeTestProcess()

describe('PackageManager - Rush Implementation', () => {
  beforeEach(() => {
    TestFileSystem.reset()
    TestProcess.reset()
  })

  it('run arbitrary scripts from package.json', async () => {
    const script = 'script'
    const args = ['arg1', 'arg2']
    const testLayer = Layer.all(TestFileSystem.layer, TestProcess.layer)

    const effect = gen(function* ($) {
      const { runScript } = yield* $(PackageManagerService)
      return yield* $(runScript(script, args))
    })

    await unsafeRunEffect(effect, {
      layer: Layer.using(testLayer)(RushPackageManager),
      onError: guardError('An error ocurred'),
    })
    expect(TestProcess.fakes.exec).to.have.been.calledWith(`rushx ${script} ${args.join(' ')}`)
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

    await unsafeRunEffect(effect, {
      layer: Layer.using(testLayer)(RushPackageManager),
      onError: guardError('An error ocurred'),
    })
    expect(CwdTestProcess.fakes.exec).to.have.been.calledWith('rushx script', projectRoot)
  })

  it('cannot install production dependencies', async () => {
    const testLayer = Layer.all(TestFileSystem.layer, TestProcess.layer)

    const effect = gen(function* ($) {
      const { installProductionDependencies } = yield* $(PackageManagerService)
      return yield* $(installProductionDependencies())
    })

    return expect(
      unsafeRunEffect(effect, {
        layer: Layer.using(testLayer)(RushPackageManager),
        onError: guardError('An error ocurred'),
      })
    ).to.be.eventually.rejected
  })

  it('can install all dependencies', async () => {
    const testLayer = Layer.all(TestFileSystem.layer, TestProcess.layer)

    const effect = gen(function* ($) {
      const { installAllDependencies } = yield* $(PackageManagerService)
      return yield* $(installAllDependencies())
    })

    await unsafeRunEffect(effect, {
      layer: Layer.using(testLayer)(RushPackageManager),
      onError: guardError('An error ocurred'),
    })

    expect(TestProcess.fakes.exec).to.have.been.calledWith('rush update')
  })
})
