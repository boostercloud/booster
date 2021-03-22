import { deploy } from '../deploy'
import { sleep } from '../../../helper/sleep'
import { setEnv, checkConfigAnd } from '../utils'
import { sandboxPathFor } from '../../../helper/file-helper'
import { overrideWithBoosterLocalDependencies } from '../../../helper/deps-helper'
// Imported from another package to avoid duplication
// It is OK-ish, since integration tests are always run in the context of the whole monorepo
import { createSandboxProject } from '../../../../../cli/src/common/sandbox'

before(async () => {
  await setEnv()
  const configuredAssets = ['assets', 'assetFile.txt']
  const sandboxedProject = createSandboxProject(sandboxPathFor('deploy'), configuredAssets)

  await overrideWithBoosterLocalDependencies(sandboxedProject)

  await checkConfigAnd(deploy.bind(null, sandboxedProject))
  console.log('Waiting 30 seconds after deployment to let the stack finish its initialization...')
  await sleep(30000)
  console.log('...sleep finished. Let the tests begin 🔥!')
})
