import { fake } from 'sinon'
import { gen, Layer, unsafeRunEffect } from '@boostercloud/framework-types/src/effect'
import { expect } from '../../expect'
import { makeTestFileSystem } from '../file-system/test.impl'
import { makeTestProcess } from '../process/test.impl'
import { packageManagerInternals } from '../../../src/services/package-manager'
import { RushPackageManager } from '../../../src/services/package-manager/rush.impl'
import { guardError } from '../../../src/common/errors'

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
    const { runScript } = packageManagerInternals

    await unsafeRunEffect(runScript(script, args), {
      layer: Layer.using(testLayer)(RushPackageManager),
      onError: guardError('An error ocurred'),
    })
    expect(TestProcess.fakes.exec).to.have.been.calledWith(`rushx ${script} ${args.join(' ')}`)
  })

  it('can set the project root properly', async () => {
    const projectRoot = 'projectRoot'

    const CwdTestProcess = makeTestProcess({ cwd: fake.returns(projectRoot) })
    const testLayer = Layer.all(TestFileSystem.layer, CwdTestProcess.layer)
    const { setProjectRoot, runScript } = packageManagerInternals

    const effect = gen(function* ($) {
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
    const { installProductionDependencies } = packageManagerInternals

    expect(
      unsafeRunEffect(installProductionDependencies(), {
        layer: Layer.using(testLayer)(RushPackageManager),
        onError: guardError('An error ocurred'),
      })
    ).to.eventually.throw()
  })

  it('can install all dependencies', async () => {
    const testLayer = Layer.all(TestFileSystem.layer, TestProcess.layer)
    const { installAllDependencies } = packageManagerInternals

    await unsafeRunEffect(installAllDependencies(), {
      layer: Layer.using(testLayer)(RushPackageManager),
      onError: guardError('An error ocurred'),
    })

    expect(TestProcess.fakes.exec).to.have.been.calledWith('rush update')
  })
})
