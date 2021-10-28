import { deploy } from '../../../helper/cli-helper'
import { sleep } from '../../../helper/sleep'
import { sandboxPathFor } from '../../../helper/file-helper'
import { overrideWithBoosterLocalDependencies } from '../../../helper/deps-helper'
// Imported from another package to avoid duplication
// It is OK-ish, since integration tests are always run in the context of the whole monorepo
import { createSandboxProject } from '../../../../../cli/src/common/sandbox'
import { applicationName, setEnv } from '../../../helper/app-helper'
import { AzureTestHelper } from '@boostercloud/framework-provider-azure-infrastructure'

before(async () => {
  await setEnv()
  const configuredAssets = ['assets', 'assetFile.txt']
  const sandboxedProject = createSandboxProject(sandboxPathFor('deploy'), configuredAssets)

  await overrideWithBoosterLocalDependencies(sandboxedProject)

  const servicePrincipalName = applicationName() + '-sp'
  await AzureTestHelper.preBuild(servicePrincipalName)

  console.log('Deploying sandbox project')
  await deploy(sandboxedProject, 'azure')
  console.log('Waiting 30 seconds after deployment to let the ARM finish its initialization...')
  await sleep(30000)
  console.log('...sleep finished. Let the tests begin 🔥!')
})
