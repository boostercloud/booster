import { Process } from '../process'
import { FileSystem } from '../file-system'
import { SimplePackageManager } from './simple.impl'
import { Logger } from '@boostercloud/framework-types'

/**
 * A simple implementation of the PackageManager interface that uses the
 * Yarn CLI to manage packages.
 */
export class YarnPackageManager extends SimplePackageManager {
  constructor(readonly logger: Logger, readonly process: Process, readonly fileSystem: FileSystem) {
    super('yarn', logger, process, fileSystem)
  }

  getLockfileName(): string {
    return 'yarn.lock'
  }
}
