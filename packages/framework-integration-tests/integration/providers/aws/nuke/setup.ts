import { overrideWithBoosterLocalDependencies } from '../../../helper/depsHelper'
import { createSandboxProject, removeSandboxProject } from '../../../helper/fileHelper'
import { nuke } from '../deploy'
import { setEnv, checkConfigAnd } from '../utils'

before(async () => {
  await setEnv()
  const sandboxedProject = createSandboxProject('deploy')

  await overrideWithBoosterLocalDependencies(sandboxedProject)

  await checkConfigAnd(nuke.bind(null, sandboxedProject))
  removeSandboxProject('deploy')
})
