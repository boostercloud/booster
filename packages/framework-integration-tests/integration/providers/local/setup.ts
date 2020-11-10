import { start } from '../../../src/deploy'
import { sleep } from '../../helper/sleep'
import { exec } from 'child-process-promise'
import { ChildProcess } from 'child_process'
import { createSandboxProject, removeFolders } from '../../helper/fileHelper'
import { forceLernaRebuild, symLinkBoosterDependencies } from '../../helper/depsHelper'
import { sandboxPath } from './constants'

let serverProcess: ChildProcess

before(async () => {
  console.log('preparing sandboxed project...')
  createSandboxProject(sandboxPath)

  console.log('installing dependencies...')
  await exec('npm install', { cwd: sandboxPath })

  console.log('symlinking booster dependencies...')
  await symLinkBoosterDependencies(sandboxPath)

  console.log('rebuilding framework...')
  await forceLernaRebuild()

  console.log(`starting local server in ${sandboxPath}...`)
  serverProcess = start('local', sandboxPath)
  await sleep(10000) // TODO: We need some time for the server to start, but maybe we can do this faster using the `waitForIt` method developed for waiting for AWS resources.
})

after(async () => {
  console.log('stopping local server...')
  serverProcess.kill('SIGINT')
  console.log('removing sandbox project')
  removeFolders([sandboxPath])
})
