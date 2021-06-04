<<<<<<< HEAD
import { removeFolders, sandboxPathFor, readPIDFor } from '../../../helper/file-helper'
import { sandboxName } from '../constants'

before(async () => {
  const sandboxPath = sandboxPathFor(sandboxName)
  const pid: number = readPIDFor(sandboxPath)
  console.log(`stopping local server with pid ${pid}...`)
=======
import { removeFolders } from '../../../helper/file-helper'
import { sandboxName } from '../constants'
import { sandboxPathFor } from '../../../helper/file-helper'
import { readFileSync } from 'fs'
import * as path from 'path'

before(async () => {
  const sandboxPath = sandboxPathFor(sandboxName)
  const pidFile: string = path.join(sandboxPathFor(sandboxName), 'local_provider.pid')
  const pid = parseInt(readFileSync(pidFile).toString(), 10)
  console.log(`stopping local server with pid ${pid}...`)  
>>>>>>> 0f61eaab (local provider start and stop methods for integration tests)
  process.kill(pid)
  console.log('removing sandbox project...')
  removeFolders([sandboxPath])
})
