import { FileSystem } from '../file-system'
import { Process } from '../process'
import { PackageManager } from '.'
import { RushPackageManager } from './rush.impl'
import { PnpmPackageManager } from './pnpm.impl'
import { YarnPackageManager } from './yarn.impl'
import { NpmPackageManager } from './npm.impl'
import { ComponentContainer } from '../../common/component'
import { Class, Logger } from '@boostercloud/framework-types'

export async function inferPackageManager(
  container: ComponentContainer<Logger | FileSystem | Process>
): Promise<Class<PackageManager>> {
  const process = container.get(Process)
  const fileSystem = container.get(FileSystem)
  const workingDir = await process.cwd()
  const entries = await fileSystem.readDirectoryContents(workingDir)
  const contents = entries.map((entry) => entry.name)
  if (contents.includes('.rush')) {
    return RushPackageManager
  } else if (contents.includes('pnpm-lock.yaml')) {
    return PnpmPackageManager
  } else if (contents.includes('yarn.lock')) {
    return YarnPackageManager
  } else if (contents.includes('package-lock.json')) {
    return NpmPackageManager
  } else {
    // Infer npm by default
    return NpmPackageManager
  }
}
