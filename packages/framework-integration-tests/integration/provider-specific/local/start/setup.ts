import { start } from '../../../helper/cli-helper'
import { sleep } from '../../../helper/sleep'
import { ChildProcess } from 'child_process'
import { sandboxPathFor, storePIDFor } from '../../../helper/file-helper'
import { overrideWithBoosterLocalDependencies } from '../../../helper/deps-helper'
import { sandboxName } from '../constants'
import { createSandboxProject } from '../../../../../cli/src/common/sandbox'
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
  await runCommand(sandboxPath, 'npm install --production --no-bin-links --no-optional')

  console.log(`starting local server in ${sandboxPath}...`)
  serverProcess = start(sandboxPath, 'local')
  if (!serverProcess.pid) {
    throw new Error('Pid not found')
  }
  storePIDFor(sandboxPath, serverProcess.pid) //store pid to kill process on stop
  await sleep(2000)
  console.log('local server ready')
})
