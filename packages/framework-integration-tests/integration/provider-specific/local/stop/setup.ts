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
  process.kill(pid)
  console.log('removing sandbox project...')
  removeFolders([sandboxPath])
})