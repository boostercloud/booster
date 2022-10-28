import { fake } from 'sinon'
import { gen, Layer, unsafeRunEffect } from '@boostercloud/framework-types/dist/effect'
import { expect } from '../../expect'
import { makeTestFileSystem } from '../file-system/test.impl'
import { makeTestProcess } from '../process/test.impl'
import { PackageManagerService } from '../../../src/services/package-manager'
import { InferredPackageManager } from '../../../src/services/package-manager/live.impl'
import { guardError } from '../../../src/common/errors'

describe('PackageManager - Live Implementation (with inference)', () => {
  const TestProcess = makeTestProcess()

  afterEach(() => {
    TestProcess.reset()
  })

  const runScript = gen(function* ($) {
    const { runScript } = yield* $(PackageManagerService)
    return yield* $(runScript('script', []))
  })

  it('infers Rush when a `.rush` folder is present', async () => {
    const TestFileSystem = makeTestFileSystem({ readDirectoryContents: fake.returns(['.rush']) })
    const testLayer = Layer.all(TestFileSystem.layer, TestProcess.layer)
    await unsafeRunEffect(runScript, {
      layer: Layer.using(testLayer)(InferredPackageManager),
      onError: guardError('An error ocurred'),
    })
    expect(TestProcess.fakes.exec).to.have.been.calledWith('rushx script')
  })

  it('infers pnpm when a `pnpm-lock.yaml` file is present', async () => {
    const TestFileSystem = makeTestFileSystem({ readDirectoryContents: fake.returns(['pnpm-lock.yaml']) })
    const testLayer = Layer.all(TestFileSystem.layer, TestProcess.layer)
    await unsafeRunEffect(runScript, {
      layer: Layer.using(testLayer)(InferredPackageManager),
      onError: guardError('An error ocurred'),
    })
    expect(TestProcess.fakes.exec).to.have.been.calledWith('pnpm run script')
  })

  it('infers npm when a `package-lock.json` file is present', async () => {
    const TestFileSystem = makeTestFileSystem({ readDirectoryContents: fake.returns(['package-lock.json']) })
    const testLayer = Layer.all(TestFileSystem.layer, TestProcess.layer)
    await unsafeRunEffect(runScript, {
      layer: Layer.using(testLayer)(InferredPackageManager),
      onError: guardError('An error ocurred'),
    })
    expect(TestProcess.fakes.exec).to.have.been.calledWith('npm run script')
  })

  it('infers yarn when a `yarn.lock` file is present', async () => {
    const TestFileSystem = makeTestFileSystem({ readDirectoryContents: fake.returns(['yarn.lock']) })
    const testLayer = Layer.all(TestFileSystem.layer, TestProcess.layer)
    await unsafeRunEffect(runScript, {
      layer: Layer.using(testLayer)(InferredPackageManager),
      onError: guardError('An error ocurred'),
    })
    expect(TestProcess.fakes.exec).to.have.been.calledWith('yarn run script')
  })

  it('infers npm when no lock file is present', async () => {
    const TestFileSystem = makeTestFileSystem({ readDirectoryContents: fake.returns([]) })
    const testLayer = Layer.all(TestFileSystem.layer, TestProcess.layer)
    await unsafeRunEffect(runScript, {
      layer: Layer.using(testLayer)(InferredPackageManager),
      onError: guardError('An error ocurred'),
    })
    expect(TestProcess.fakes.exec).to.have.been.calledWith('npm run script')
  })
})
