import { start } from './utils'
import { sleep } from '../../helper/sleep'
import { ChildProcess } from 'child_process'
import { createSandboxProject, removeFolders } from '../../helper/fileHelper'
import { installBoosterPackage } from '../../helper/depsHelper'
import { sandboxName } from './constants'
import { runCommand } from '../../helper/runCommand'

let serverProcess: ChildProcess
let sandboxPath: string

before(async () => {
  console.log('Installing the infrastructure package in the global scope')
  await installBoosterPackage('framework-provider-local-infrastructure')

  console.log('preparing sandboxed project...')
  sandboxPath = await createSandboxProject(sandboxName)

  console.log('installing dependencies...')
  await runCommand(sandboxPath, 'npx yarn install')

  console.log(`starting local server in ${sandboxPath}...`)
  serverProcess = start('local', sandboxPath)
  await sleep(10000) // TODO: We need some time for the server to start, but maybe we could do this faster using the `waitForIt` method
})

after(async () => {
  console.log('stopping local server...')
  serverProcess.kill('SIGINT')
  console.log('removing sandbox project')
  removeFolders([sandboxPath])
})
