import { start } from './utils'
import { sleep } from '../../helper/sleep'
import { ChildProcess } from 'child_process'
import { createSandboxProject, removeFolders } from '../../helper/fileHelper'
import { symLinkBoosterDependencies } from '../../helper/depsHelper'
import { sandboxName } from './constants'
import { runCommand } from '../../helper/runCommand'

let serverProcess: ChildProcess
let sandboxPath: string

before(async () => {
  console.log('preparing sandboxed project...')
  sandboxPath = createSandboxProject(sandboxName)

  console.log('installing dependencies...')
  await runCommand(sandboxPath, 'npm install')

  console.log('symlinking booster dependencies...')
  await symLinkBoosterDependencies(sandboxPath)

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
