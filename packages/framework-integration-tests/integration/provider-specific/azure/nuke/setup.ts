// Imported from another package to avoid duplication
// It is OK-ish, since integration tests are always run in the context of the whole monorepo
import { createSandboxProject, removeSandboxProject } from '../../../../../cli/src/common/sandbox'
import { sandboxPathFor } from '../../../helper/file-helper'
import { applicationName, setEnv } from '../../../helper/app-helper'
import { AzureTestHelper } from '@boostercloud/framework-provider-azure-infrastructure'
import { nuke } from '../../../helper/cli-helper'
import { overrideWithBoosterLocalDependencies } from '../../../helper/deps-helper'

before(async () => {
  await setEnv()
  const sandboxPath = sandboxPathFor('nuke')
  const configuredAssets = ['assets', 'assetFile.txt']
  const sandboxedProject = createSandboxProject(sandboxPath, configuredAssets)
  const servicePrincipalName = applicationName() + '-sp'

  await overrideWithBoosterLocalDependencies(sandboxedProject)

  await AzureTestHelper.preNuke(servicePrincipalName)
  await nuke(sandboxedProject, 'azure')
  removeSandboxProject(sandboxPath)
})
