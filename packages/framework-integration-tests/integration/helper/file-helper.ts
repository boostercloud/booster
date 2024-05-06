import { existsSync, mkdirSync, readdirSync, readFileSync, rmSync, unlinkSync, writeFileSync } from 'fs'
import * as path from 'path'
import { sleep } from './sleep'

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

export const removeFolders = async (paths: Array<string>): Promise<void> => {
  for (const path of paths) {
    let retries = 5
    while (retries > 0) {
      try {
        rmSync(path, { recursive: true, force: true })
        break // Break out of the while loop if the deletion succeeds
      } catch (error) {
        // Although we're using parameters recursive and force, sometimes the deletion fails with ENOTEMPTY
        // because the OS haven't had the time to fully release the files. We retry a few times before giving up.
        if (error.code === 'ENOTEMPTY' && retries > 0) {
          retries--
          console.warn(`Retrying deletion of ${path}, ${retries} retries remaining...`)
          await sleep(1000) // Wait for 1 second before retrying
        } else {
          // After the retries are exhausted, we silently desist. It's not worth failing integration tests because of this
          console.warn(`Failed to delete ${path}, skipping...`)
          break
        }
      }
    }
  }
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
