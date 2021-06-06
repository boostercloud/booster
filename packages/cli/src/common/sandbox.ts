import { mkdirSync, readdirSync, rmSync, statSync } from 'fs'
import { copySync } from 'fs-extra'
import * as path from 'path'

const copyFolder = (origin: string, destiny: string): void => {
  readdirSync(origin, { withFileTypes: true }).forEach((dirEnt) => {
    if (dirEnt.isFile()) {
      copySync(path.join(origin, dirEnt.name), path.join(destiny, dirEnt.name))
    }
    if (dirEnt.isDirectory()) {
      mkdirSync(path.join(destiny, dirEnt.name), { recursive: true })
      copyFolder(path.join(origin, dirEnt.name), path.join(destiny, dirEnt.name))
    }
  })
}

export const createSandboxProject = (sandboxPath: string, assets?: Array<string>): string => {
  rmSync(sandboxPath, { recursive: true, force: true })
  mkdirSync(sandboxPath, { recursive: true })
  copyFolder('src', path.join(sandboxPath, 'src'))

  const projectFiles = ['package.json', 'tsconfig.json']
  projectFiles.forEach((file: string) => copySync(file, path.join(sandboxPath, file)))

  if (assets) {
    assets.forEach((asset) => {
      if (statSync(asset).isDirectory()) {
        copyFolder(asset, path.join(sandboxPath, asset))
      } else {
        copySync(asset, path.join(sandboxPath, asset))
      }
    })
  }

  return sandboxPath
}

export const removeSandboxProject = (sandboxPath: string): void => {
  rmSync(sandboxPath, { recursive: true, force: true })
}
