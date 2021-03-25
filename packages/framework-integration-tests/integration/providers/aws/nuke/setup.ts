import { overrideWithBoosterLocalDependencies } from '../../../helper/deps-helper'
import { nuke } from '../deploy'
import { setEnv, checkConfigAnd } from '../utils'
// Imported from another package to avoid duplication
// It is OK-ish, since integration tests are always run in the context of the whole monorepo
import { createSandboxProject, removeSandboxProject } from '../../../../../cli/src/common/sandbox'
import { sandboxPathFor } from '../../../helper/file-helper'

before(async () => {
  await setEnv()
  const sandboxPath = sandboxPathFor('deploy')
  const configuredAssets = ['assets', 'assetFile.txt']
  const sandboxedProject = createSandboxProject(sandboxPath, configuredAssets)

  await overrideWithBoosterLocalDependencies(sandboxedProject)

  await checkConfigAnd(nuke.bind(null, sandboxedProject))
  removeSandboxProject(sandboxPath)
})
