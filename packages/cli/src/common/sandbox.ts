import { mkdirSync, readdirSync, rmSync, statSync } from 'fs'
import { copySync, existsSync } from 'fs-extra'
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
