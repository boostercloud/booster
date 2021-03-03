import { copyFileSync, mkdirSync, readdirSync, rmdirSync, statSync } from 'fs'
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

export const createSandboxProject = (sandboxPath: string, assets?: Array<string>): string => {
  rmdirSync(sandboxPath, { recursive: true })
  mkdirSync(sandboxPath, { recursive: true })
  copyFolder('src', path.join(sandboxPath, 'src'))

  const projectFiles = ['package.json', 'tsconfig.json']
  projectFiles.forEach((file: string) => copyFileSync(file, path.join(sandboxPath, file)))

  if (assets) {
    assets.forEach((asset) => {
      if (statSync(asset).isDirectory()) {
        copyFolder(asset, path.join(sandboxPath, asset))
      } else {
        copyFileSync(asset, path.join(sandboxPath, asset))
      }
    })
  }

  return sandboxPath
}

export const removeSandboxProject = (sandboxPath: string): void => {
  rmdirSync(sandboxPath, { recursive: true })
}
