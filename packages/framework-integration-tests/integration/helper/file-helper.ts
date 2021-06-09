import { existsSync, mkdirSync, readdirSync, readFileSync, rmSync, unlinkSync, writeFileSync } from 'fs'
import * as path from 'path'

export const loadFixture = (fixturePath: string, replacements?: Array<Array<string>>): string => {
  const template = readFileContent(`integration/fixtures/${fixturePath}`)
  return (
    replacements?.reduce(
      (prevContents: string, replacement: string[]): string => prevContents.split(replacement[0]).join(replacement[1]),
      template
    ) ?? template
  )
}

export const readFileContent = (filePath: string): string => readFileSync(filePath, 'utf-8')

export const writeFileContent = (filePath: string, data: string): void => writeFileSync(filePath, data)

export const removeFiles = (filePaths: Array<string>, ignoreErrors = false): void => {
  filePaths.map((file: string) => {
    try {
      unlinkSync(file)
    } catch (e) {
      if (!ignoreErrors) throw e
    }
  })
}

export const createFolder = (folder: string): void => {
  if (!existsSync(folder)) {
    mkdirSync(folder)
  }
}

export const removeFolders = (paths: Array<string>): void => {
  paths.map((path: string) => rmSync(path, { recursive: true, force: true }))
}

export const fileExists = existsSync
export const dirContents = readdirSync

export const sandboxPathFor = (sandboxName: string): string => sandboxName + '-integration-sandbox' // Add the suffix to make sure this folder is gitignored

export const pidForSandboxPath = (sandboxPath: string): string => path.join(sandboxPath, 'local_provider.pid')

export const storePIDFor = (sandboxPath: string, pid: number): void => {
  writeFileSync(pidForSandboxPath(sandboxPath), pid.toString())
}

export const readPIDFor = (sandboxPath: string): number => {
  const pidFile: string = pidForSandboxPath(sandboxPath)
  return parseInt(readFileSync(pidFile).toString(), 10)
}
