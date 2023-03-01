import { Process } from '../process'
import { FileSystem } from '../file-system'
import { SimplePackageManager } from './simple.impl'
import { Logger } from '@boostercloud/framework-types'
import { Component } from '../../common/component'
import { CliError } from '../../common/errors'

/**
 * A simple implementation of the PackageManager interface that uses the
 * Yarn CLI to manage packages.
 */
@Component({ throws: CliError })
export class YarnPackageManager extends SimplePackageManager {
  constructor(readonly logger: Logger, readonly process: Process, readonly fileSystem: FileSystem) {
    super('yarn', logger, process, fileSystem)
  }

  getLockfileName(): string {
    return 'yarn.lock'
  }
}
