import util = require('util')
import * as fs from 'fs'

const unlink = util.promisify(require('fs').unlink)
const rmdir = util.promisify(require('fs').rmdir)

export const readFileContent = (filePath: string): string => fs.readFileSync(filePath, 'utf-8')

export const writeFileContent = (filePath: string, data: string): void => fs.writeFileSync(filePath, data)

export const removeFiles = (filePaths: Array<string>): Array<Promise<void>> =>
  filePaths.map((file: string) => unlink(file))

export const removeFolders = (paths: Array<string>): Array<Promise<void>> =>
  paths.map((path: string) => rmdir(path, { recursive: true }))
