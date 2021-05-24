import { overrideWithBoosterLocalDependencies } from '../../../helper/deps-helper'
import { nuke } from '../../../helper/cli-helper'
import { createSandboxProject, removeSandboxProject } from '../../../../../cli/src/common/sandbox'
import { sandboxPathFor } from '../../../helper/file-helper'
import { sandboxProjectName } from '../constants'

before(async () => {
  const sandboxPath = sandboxPathFor(sandboxProjectName)
  const configuredAssets = ['assets', 'components', 'assetFile.txt']
  createSandboxProject(sandboxPath, configuredAssets)

  await overrideWithBoosterLocalDependencies(sandboxPath)

  await nuke(sandboxPath, 'kubernetes')
  removeSandboxProject(sandboxPath)
})
