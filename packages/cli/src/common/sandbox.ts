import { copyFileSync, mkdirSync, readdirSync, rmdirSync } from 'fs'
import * as path from 'path'

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

export const createSandboxProject = (sandboxPath: string, extraFolders?: Array<string>): string => {
  rmdirSync(sandboxPath, { recursive: true })
  mkdirSync(sandboxPath, { recursive: true })
  copyFolder('src', path.join(sandboxPath, 'src'))
  if (extraFolders) {
    extraFolders.forEach((folder) => copyFolder(folder, path.join(sandboxPath, folder)))
  }

  const projectFiles = ['.eslintignore', 'package.json', 'tsconfig.eslint.json', 'tsconfig.json']
  projectFiles.forEach((file: string) => copyFileSync(file, path.join(sandboxPath, file)))

  return sandboxPath
}

export const removeSandboxProject = (sandboxPath: string): void => {
  rmdirSync(sandboxPath, { recursive: true })
}
