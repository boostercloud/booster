import { deploy } from '../deploy'
import { sleep } from '../../../helper/sleep'
import { setEnv, checkConfigAnd } from '../utils'
import { sandboxPathFor } from '../../../helper/fileHelper'
import { overrideWithBoosterLocalDependencies } from '../../../helper/depsHelper'
// Imported from another package to avoid duplication
// It is OK-ish, since integration tests are always run in the context of the whole monorepo
import { createSandboxProject } from '../../../cli/src/common/sandbox'

before(async () => {
  await setEnv()
  const sandboxedProject = createSandboxProject(sandboxPathFor('deploy'))

  await overrideWithBoosterLocalDependencies(sandboxedProject)

  await checkConfigAnd(deploy.bind(null, sandboxedProject))
  console.log('Waiting 30 seconds after deployment to let the stack finish its initialization...')
  await sleep(30000)
  console.log('...sleep finished. Let the tests begin 🔥!')
})
