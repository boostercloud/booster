// Imported from another package to avoid duplication
// It is OK-ish, since integration tests are always run in the context of the whole monorepo
import { createSandboxProject, removeSandboxProject } from '../../../../../cli/src/common/sandbox'
import { sandboxPathFor } from '../../../helper/file-helper'
import { setEnvironment } from '../../../helper/app-helper'
import { nuke } from '../../../helper/cli-helper'
import { overrideWithBoosterLocalDependencies } from '../../../helper/deps-helper'
import { AzureTestHelper } from '@boostercloud/framework-provider-azure-infrastructure'

before(async () => {
  await setEnvironment()
  const sandboxPath = sandboxPathFor('nuke')
  const configuredAssets = ['assets', 'assetFile.txt']
  const sandboxedProject = createSandboxProject(sandboxPath, configuredAssets)

  await overrideWithBoosterLocalDependencies(sandboxedProject)

  AzureTestHelper.ensureAzureConfiguration()
  await nuke(sandboxedProject, 'azure')
  removeSandboxProject(sandboxPath)
})
