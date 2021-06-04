import { start } from '../../../helper/cli-helper'
import { sleep } from '../../../helper/sleep'
import { ChildProcess } from 'child_process'
import { sandboxPathFor } from '../../../helper/file-helper'
import { overrideWithBoosterLocalDependencies } from '../../../helper/deps-helper'
import { sandboxName } from '../constants'
import { runCommand } from '../../../helper/run-command'
import { createSandboxProject } from '../../../../../cli/src/common/sandbox'
import { writeFileSync } from 'fs'
import * as path from 'path'

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
  const pidFile: string = path.join(sandboxPath, 'local_provider.pid')
  writeFileSync(pidFile, serverProcess.pid.toString()) //store pid to kill process on stop 
  await sleep(2000)
  console.log(`local server ready`)
})
