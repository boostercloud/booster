import { createSandboxProject, removeSandboxProject, sandboxPathFor } from '../../../helper/fileHelper'
import { nuke } from '../deploy'
import { setEnv, checkConfigAnd } from '../utils'
import { existsSync } from 'fs'

before(async () => {
  await setEnv()

  // If the sandbox project is not present we create it to allow the nuke command run
  const sandboxPath = sandboxPathFor('deploy')
  if (!existsSync(sandboxPath)) createSandboxProject('deploy')

  await checkConfigAnd(nuke.bind(null, sandboxPath))

  removeSandboxProject('deploy')
})
