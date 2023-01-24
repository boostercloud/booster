import { tryCatchPromise, Layer } from '@boostercloud/framework-types/dist/effect'
import { FileSystemService, FileSystemError } from '.'
import * as fs from 'fs'

const readDirectoryContents = (directoryPath: string) =>
  tryCatchPromise(
    () => fs.promises.readdir(directoryPath),
    (reason) =>
      new FileSystemError(new Error(`There were some issues reading the directory ${directoryPath}: ${reason}`))
  )

const readFileContents = (filePath: string) =>
  tryCatchPromise(
    () => fs.promises.readFile(filePath, 'utf8'),
    (reason) => new FileSystemError(new Error(`There were some issues reading the file ${filePath}: ${reason}`))
  )

export const LiveFileSystem = Layer.fromValue(FileSystemService)({ readDirectoryContents, readFileContents })
