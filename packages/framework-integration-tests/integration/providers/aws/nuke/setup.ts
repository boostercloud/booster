import { createSandboxProject, removeSandboxProject } from '../../../helper/fileHelper'
import { nuke } from '../deploy'
import { setEnv, checkConfigAnd } from '../utils'

before(async () => {
  await setEnv()
  const sandboxedProject = await createSandboxProject('deploy')
  await checkConfigAnd(nuke.bind(null, sandboxedProject))
  removeSandboxProject('deploy')
})
