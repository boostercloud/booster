import { start } from '../../helper/cli-helper'
import { sleep } from '../../helper/sleep'
import { ChildProcess } from 'child_process'
import { removeFolders, sandboxPathFor } from '../../helper/file-helper'
import { overrideWithBoosterLocalDependencies } from '../../helper/deps-helper'
import { sandboxName } from './constants'
// Imported from another package to avoid duplication
// It is OK-ish, since integration tests are always run in the context of the whole monorepo
import { createSandboxProject } from '../../../../cli/src/common/sandbox'
import { runCommand } from '@boostercloud/framework-common-helpers'

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
  serverProcess = start(sandboxPath, 'local')
  await sleep(10000) // TODO: We need some time for the server to start, but maybe we could do this faster using the `waitForIt` method
})

after(async () => {
  console.log('stopping local server...')
  serverProcess.kill('SIGINT')
  console.log('removing sandbox project...')
  await removeFolders([sandboxPath])
})
