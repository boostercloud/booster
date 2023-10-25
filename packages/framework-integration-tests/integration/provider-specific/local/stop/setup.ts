import { removeFolders, sandboxPathFor, readPIDFor } from '../../../helper/file-helper'
import { sandboxName } from '../constants'

before(async () => {
  const sandboxPath = sandboxPathFor(sandboxName)
  const pid: number = readPIDFor(sandboxPath)
  console.log(`stopping local server with pid ${pid}...`)
  process.kill(pid)
  console.log('removing sandbox project...')
  await removeFolders([sandboxPath])
})
