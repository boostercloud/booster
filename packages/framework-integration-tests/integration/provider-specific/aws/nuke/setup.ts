// Imported from another package to avoid duplication
// It is OK-ish, since integration tests are always run in the context of the whole monorepo
import { createSandboxProject, removeSandboxProject } from '../../../../../cli/src/common/sandbox'
import { sandboxPathFor } from '../../../helper/file-helper'
import { setEnv } from '../../../helper/app-helper'
import { AWSTestHelper } from '@boostercloud/framework-provider-aws-infrastructure'
import { nuke } from '../deploy'
import { overrideWithBoosterLocalDependencies } from '../../../helper/deps-helper'

before(async () => {
  await setEnv()
  const sandboxPath = sandboxPathFor('nuke')
  const configuredAssets = ['assets', 'assetFile.txt']
  const sandboxedProject = createSandboxProject(sandboxPath, configuredAssets)

  await overrideWithBoosterLocalDependencies(sandboxedProject)

  AWSTestHelper.ensureAWSConfiguration()

  await nuke(sandboxedProject)
  removeSandboxProject(sandboxPath)
})
