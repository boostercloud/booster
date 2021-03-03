import { start } from './utils'
import { sleep } from '../../helper/sleep'
import { ChildProcess } from 'child_process'
import { removeFolders, sandboxPathFor } from '../../helper/fileHelper'
import { overrideWithBoosterLocalDependencies } from '../../helper/depsHelper'
import { sandboxName } from './constants'
import { runCommand } from '../../helper/runCommand'
// Imported from another package to avoid duplication
// It is OK-ish, since integration tests are always run in the context of the whole monorepo
import { createSandboxProject } from '../../../../cli/src/common/sandbox'

let serverProcess: ChildProcess
let sandboxPath: string

before(async () => {
  console.log('preparing sandboxed project...')
  const configuredAssets = ['assets', 'assetFile.txt']
  sandboxPath = createSandboxProject(sandboxPathFor(sandboxName), configuredAssets)

  console.log('overriding booster dependencies...')
  await overrideWithBoosterLocalDependencies(sandboxPath)

  console.log('installing dependencies...')
  await runCommand(sandboxPath, 'npm install')

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
