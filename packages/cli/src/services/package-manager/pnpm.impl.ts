import { FileSystem } from '../file-system'
import { Process } from '../process'
import { SimplePackageManager } from './simple.impl'
import { Logger } from '@boostercloud/framework-types'

/**
 * A simple implementation of the PackageManager interface that uses the
 * PNPM CLI to manage packages.
 */
export class PnpmPackageManager extends SimplePackageManager {
  constructor(readonly logger: Logger, readonly process: Process, readonly fileSystem: FileSystem) {
    super('pnpm', logger, process, fileSystem)
  }

  getLockfileName(): string {
    return 'pnpm-lock.yaml'
  }
}
