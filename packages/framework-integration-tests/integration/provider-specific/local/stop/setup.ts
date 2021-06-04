//import { start } from '../../helper/cli-helper'
//import { sleep } from '../../helper/sleep'
//import { ChildProcess } from 'child_process'
import { removeFolders } from '../../../helper/file-helper'
//import { overrideWithBoosterLocalDependencies } from '../../helper/deps-helper'
//import { sandboxName } from './constants'
//import { runCommand } from '../../helper/run-command'
// Imported from another package to avoid duplication
// It is OK-ish, since integration tests are always run in the context of the whole monorepo
//import { createSandboxProject } from '../../../../cli/src/common/sandbox'

//let serverProcess: ChildProcess
let sandboxPath: string

after(async () => {
  console.log('stopping local server...')
  //serverProcess.kill('SIGINT')
  console.log('removing sandbox project')
  removeFolders([sandboxPath])
})