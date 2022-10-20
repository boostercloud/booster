import { fake } from 'sinon'
import { Layer, unsafeRunEffect } from '@boostercloud/framework-types/src/effect'
import { expect } from '../../expect'
import { TestFileSystem } from '../file-system/test.impl'
import { TestProcess } from '../process/test.impl'
import { packageManagerInternals } from '../../../src/services/package-manager'
import { InferredPackageManager } from '../../../src/services/package-manager/live.impl'

describe('PackageManager - Live Implementation (with inference)', () => {
  beforeEach(() => {})

  it('infers Rush when a `.rush` folder is present', () => {
    const fakeFileSystem = TestFileSystem({ readDirectoryContents: fake.resolves(['.rush']) })
    const fakeProcess = TestProcess()
    const testLayer = Layer.all(fakeFileSystem.layer, fakeProcess.layer)
    const { runScript } = packageManagerInternals
    unsafeRunEffect(runScript('script', []), {
      layer: Layer.using(testLayer)(InferredPackageManager),
      onError: (error) => {
        throw error
      },
    })
    expect(fakeProcess.fakes.exec).to.have.been.calledWith('rushx script', { cwd: '' })
  })

  it('infers pnpm when a `pnpm-lock.yaml` file is present', () => {
    const fakeFileSystem = TestFileSystem({ readDirectoryContents: fake.resolves(['pnpm-lock.yaml']) })
    const fakeProcess = TestProcess()
    const testLayer = Layer.all(fakeFileSystem.layer, fakeProcess.layer)
    const { runScript } = packageManagerInternals
    unsafeRunEffect(runScript('script', []), {
      layer: Layer.using(testLayer)(InferredPackageManager),
      onError: (error) => {
        throw error
      },
    })
    expect(fakeProcess.fakes.exec).to.have.been.calledWith('pnpm run', { cwd: '' })
  })

  it('infers npm when a `package-lock.json` file is present', () => {
    const fakeFileSystem = TestFileSystem({ readDirectoryContents: fake.resolves(['package-lock.json']) })
    const fakeProcess = TestProcess()
    const testLayer = Layer.all(fakeFileSystem.layer, fakeProcess.layer)
    const { runScript } = packageManagerInternals
    unsafeRunEffect(runScript('script', []), {
      layer: Layer.using(testLayer)(InferredPackageManager),
      onError: (error) => {
        throw error
      },
    })
    expect(fakeProcess.fakes.exec).to.have.been.calledWith('npm run', { cwd: '' })
  })

  it('infers yarn when a `yarn.lock` file is present', () => {
    const fakeFileSystem = TestFileSystem({ readDirectoryContents: fake.resolves(['yarn.lock']) })
    const fakeProcess = TestProcess()
    const testLayer = Layer.all(fakeFileSystem.layer, fakeProcess.layer)
    const { runScript } = packageManagerInternals
    unsafeRunEffect(runScript('script', []), {
      layer: Layer.using(testLayer)(InferredPackageManager),
      onError: (error) => {
        throw error
      },
    })
    expect(fakeProcess.fakes.exec).to.have.been.calledWith('yarn run', { cwd: '' })
  })

  it('throws if there is no lock file present or a `.rush` folder', () => {
    const fakeFileSystem = TestFileSystem()
    const fakeProcess = TestProcess()
    const testLayer = Layer.all(fakeFileSystem.layer, fakeProcess.layer)
    const { runScript } = packageManagerInternals
    expect(() =>
      unsafeRunEffect(runScript('script', []), {
        layer: Layer.using(testLayer)(InferredPackageManager),
        onError: (error) => {
          throw error
        },
      })
    ).to.throw('No package manager found')
  })
})
