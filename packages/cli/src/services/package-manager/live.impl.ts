/* eslint-disable @typescript-eslint/explicit-function-return-type */
import { FileSystemService } from '../file-system'
import { ProcessService } from '../process'
import { PackageManagerService } from '.'
import { dieMessage, gen, Layer, orDie } from '@boostercloud/framework-types/src/effect'
import { RushPackageManager } from './rush.impl'
import { PnpmPackageManager } from './pnpm.impl'
import { YarnPackageManager } from './yarn.impl'
import { NpmPackageManager } from './npm.impl'
import { LiveFileSystem } from '../file-system/live.impl'
import { LiveProcess } from '../process/live.impl'

const inferPackageManagerNameFromDirectoryContents = gen(function* ($) {
  const { cwd } = yield* $(ProcessService)
  const { readDirectoryContents } = yield* $(FileSystemService)
  const workingDir = yield* $(cwd())
  const contents = yield* $(readDirectoryContents(workingDir))
  if (contents.includes('.rush')) {
    return yield* $(RushPackageManager)
  } else if (contents.includes('pnpm-lock.yaml')) {
    return yield* $(PnpmPackageManager)
  } else if (contents.includes('yarn.lock')) {
    return yield* $(YarnPackageManager)
  } else if (contents.includes('package-lock.json')) {
    return yield* $(NpmPackageManager)
  } else {
    return yield* $(dieMessage('No package manager found'))
  }
})

export const InferredPackageManager = Layer.fromEffect(PackageManagerService)(
  orDie(inferPackageManagerNameFromDirectoryContents)
)

export const LivePackageManager = Layer.using(Layer.all(LiveFileSystem, LiveProcess))(InferredPackageManager)
