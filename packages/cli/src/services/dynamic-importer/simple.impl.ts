import { FileSystem } from '../file-system'
import { Component } from '../../common/component'
import { DynamicImporter } from '.'
import { Logger } from '@boostercloud/framework-types'
import { CliError, cliErrorCatch } from '../../common/errors'

@Component({ throws: CliError })
export class SimpleDynamicImporter implements DynamicImporter {
  constructor(readonly logger: Logger, readonly fileSystem: FileSystem) {}

  async catch(e: unknown): Promise<CliError> {
    return cliErrorCatch('DynamicImportError', e)
  }

  // TODO: Perform checks to ensure constraints by base class
  async import<T>(modulePath: string): Promise<T> {
    const module = require(modulePath)
    return module as T
  }
}
