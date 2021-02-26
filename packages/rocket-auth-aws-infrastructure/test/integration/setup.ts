import { deploy } from './helpers/deploy'
import { nuke } from './helpers/nuke'
import { sleep } from './helpers/sleep'
import { setEnv, checkConfigAnd } from './helpers/utils'
import { sandboxPathFor } from './helpers/fileHelper'
import { overrideWithBoosterLocalDependencies } from './helpers/depsHelper'

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

export const createSandboxProject = (sandboxPath: string): string => {
  rmdirSync(sandboxPath, { recursive: true })
  mkdirSync(sandboxPath, { recursive: true })
  copyFolder(path.join('test', 'integration', 'app'), sandboxPath)
  return sandboxPath
}

export const removeSandboxProject = (sandboxPath: string): void => {
  rmdirSync(sandboxPath, { recursive: true })
}

before(async () => {
  await setEnv()
  const sandboxedProject = createSandboxProject(sandboxPathFor('deploy'))

  await overrideWithBoosterLocalDependencies(sandboxedProject)

  await checkConfigAnd(deploy.bind(null, sandboxedProject))
  console.log('Waiting 30 seconds after deployment to let the stack finish its initialization...')
  await sleep(30000)
  console.log('...sleep finished. Let the tests begin 🔥!')
})

after(async () => {
  await setEnv()
  const sandboxPath = sandboxPathFor('deploy')
  const sandboxedProject = createSandboxProject(sandboxPath)

  await overrideWithBoosterLocalDependencies(sandboxedProject)

  await checkConfigAnd(nuke.bind(null, sandboxedProject))
  removeSandboxProject(sandboxPath)
})
