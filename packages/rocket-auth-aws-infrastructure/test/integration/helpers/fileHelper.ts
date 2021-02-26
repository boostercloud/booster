import { existsSync, mkdirSync, readdirSync, readFileSync, rmdirSync, unlinkSync, writeFileSync } from 'fs'

export const loadFixture = (fixturePath: string, replacements?: Array<Array<string>>): string => {
  const template = readFileContent(`integration/fixtures/${fixturePath}`)
  return (
    replacements?.reduce(
      (prevContents: string, replacement: string[]): string => prevContents.replace(replacement[0], replacement[1]),
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
  paths.map((path: string) => rmdirSync(path, { recursive: true }))
}

export const fileExists = existsSync
export const dirContents = readdirSync

export const sandboxPathFor = (sandboxName: string): string => sandboxName + '-integration-sandbox' // Add the suffix to make sure this folder is gitignored
