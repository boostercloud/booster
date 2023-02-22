import { FileSystem } from '../file-system'
import { Component } from '../../common/component'
import { DynamicImporter } from '.'

@Component
export class SimpleDynamicImporter implements DynamicImporter {
  constructor(readonly fileSystem: FileSystem) {}

  // TODO: Perform checks to ensure constraints by base class
  async import<T>(modulePath: string): Promise<T> {
    const moduleExists = await this.fileSystem.exists(modulePath)
    if (!moduleExists) {
      throw new Error(`Module ${modulePath} does not exist`)
    }
    const module = require(modulePath)
    return module as T
  }
}
