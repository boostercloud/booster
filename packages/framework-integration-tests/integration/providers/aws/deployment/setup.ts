import { deploy } from '../deploy'
import { sleep } from '../../../helper/sleep'
import { setEnv, checkConfigAnd } from '../utils'
import { createSandboxProject } from '../../../helper/fileHelper'
import { forceLernaRebuild, overrideWithBoosterLocalDependencies } from '../../../helper/depsHelper'

before(async () => {
  await setEnv()
  const sandboxedProject = createSandboxProject('deploy')

  await forceLernaRebuild()
  await overrideWithBoosterLocalDependencies(sandboxedProject)

  await checkConfigAnd(deploy.bind(null, sandboxedProject))
  console.log('Waiting 30 seconds after deployment to let the stack finish its initialization...')
  await sleep(30000)
  console.log('...sleep finished. Let the tests begin 🔥!')
})
