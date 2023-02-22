import { FileSystem } from '.'
import * as fs from 'fs'
import * as path from 'path'
import { Component } from '../../common/component'
import { Logger } from '@boostercloud/framework-types'
import { CliError } from '../../common/errors'

/**
 * A simple implementation of the FileSystem interface that uses the Node.js
 * file system module.
 */
@Component
export class LocalFileSystem implements FileSystem {
  constructor(readonly logger: Logger) {}

  async exists(filePath: string): Promise<boolean> {
    const fpath = path.normalize(filePath)
    return fs.promises
      .access(fpath, fs.constants.F_OK)
      .then(() => true)
      .catch(() => false)
  }

  async remove(path: string, options: { recursive?: boolean | undefined; force?: boolean | undefined }): Promise<void> {
    const fpath = path.normalize(path)
    try {
      await fs.promises.rm(fpath, options)
    } catch (e) {
      throw new CliError('FileSystemError', `There were some issues removing the file ${path}: ${e}`, e)
    }
  }

  async makeDirectory(path: string, options: { recursive?: boolean | undefined }): Promise<void> {
    try {
      const fpath = path.normalize(path)
      await fs.promises.mkdir(fpath, options)
    } catch (e) {
      throw new CliError('FileSystemError', `There were some issues creating the directory ${path}: ${e}`, e)
    }
  }

  async copy(source: string, destination: string): Promise<void> {
    try {
      const sourcePath = path.normalize(source)
      const destinationPath = path.normalize(destination)
      // We check whether its a file or a directory and then use the appropriate method
      const statSource = await fs.promises.stat(sourcePath)
      if (statSource.isFile()) {
        // If its a file, we copy it directly
        await fs.promises.copyFile(sourcePath, destinationPath)
      } else {
        // If not, we create the directory and then copy all the files recursively
        await fs.promises.mkdir(path.join(destinationPath), { recursive: true })

        // For that, we read the directory and then copy each entry
        const entries = await fs.promises.readdir(sourcePath, { withFileTypes: true })
        for (const entry of entries) {
          // We must join the source and destination paths with the entry name
          // to get the full path of the entry
          const entrySrc = path.join(sourcePath, entry.name)
          const entryDest = path.join(destinationPath, entry.name)
          await this.copy(entrySrc, entryDest)
        }
      }
    } catch (e) {
      throw new CliError('FileSystemError', `There were some issues copying the file ${source}: ${e}`, e)
    }
  }

  async readDirectoryContents(directoryPath: string): Promise<ReadonlyArray<string>> {
    try {
      return await fs.promises.readdir(directoryPath)
    } catch (error) {
      throw new CliError(
        'FileSystemError',
        `There were some issues reading the directory ${directoryPath}: ${error}`,
        error
      )
    }
  }

  async readFileContents(filePath: string): Promise<string> {
    try {
      return await fs.promises.readFile(filePath, 'utf8')
    } catch (error) {
      throw new CliError('FileSystemError', `There were some issues reading the file ${filePath}: ${error}`, error)
    }
  }
}
