import {
  existsSync,
  mkdirSync,
  readdirSync,
  readFileSync,
  rmdirSync,
  unlinkSync,
  writeFileSync,
  copyFileSync,
} from 'fs'
import * as path from 'path'
import { symLinkBoosterDependencies } from './depsHelper'

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

const copyFolder = (origin: string, destiny: string): void => {
  readdirSync(origin, { withFileTypes: true }).forEach((dirEnt) => {
    if (dirEnt.isFile()) {
      copyFileSync(path.join(origin, dirEnt.name), path.join(destiny, dirEnt.name))
    }
    if (dirEnt.isDirectory()) {
      mkdirSync(path.join(destiny, dirEnt.name), { recursive: true })
      copyFolder(path.join(origin, dirEnt.name), path.join(destiny, dirEnt.name))
    }
  })
}

const sandboxPathFor = (sandboxName: string): string => sandboxName + '-integration-sandbox' // Add the suffix to make sure this folder is gitignored

export const createSandboxProject = async (sandboxName: string): Promise<string> => {
  const sandboxPath = sandboxPathFor(sandboxName)

  rmdirSync(sandboxPath, { recursive: true })
  mkdirSync(sandboxPath, { recursive: true })
  copyFolder('src', path.join(sandboxPath, 'src'))

  const projectFiles = ['.eslintignore', 'package.json', 'tsconfig.eslint.json', 'tsconfig.json']
  projectFiles.forEach((file: string) => copyFileSync(file, path.join(sandboxPath, file)))

  copyFileSync(path.join('..', '..', 'yarn.lock'), path.join(sandboxPath, 'yarn.lock'))

  // Make sure all booster dependencies come from the versions under development
  await symLinkBoosterDependencies(sandboxPath)

  return sandboxPath
}

export const removeSandboxProject = (sandboxName: string): void => {
  const sandboxPath = sandboxPathFor(sandboxName)
  rmdirSync(sandboxPath, { recursive: true })
}
