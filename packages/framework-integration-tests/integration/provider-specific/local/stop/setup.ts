import { removeFolders, sandboxPathFor, readPIDFor } from '../../../helper/file-helper'
import { sandboxName } from '../constants'

before(async () => {
  const sandboxPath = sandboxPathFor(sandboxName)
  const pid: number = readPIDFor(sandboxPath)
  console.log(`stopping local server with pid ${pid}...`)
  try {
    process.kill(pid)
  } catch (e) {
    console.log('No server to stop. Skipping...')
  }
  console.log('removing sandbox project...')
  removeFolders([sandboxPath])
})
