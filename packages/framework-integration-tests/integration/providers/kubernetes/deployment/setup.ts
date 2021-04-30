import { createSandboxProject } from '../../../../../cli/src/common/sandbox'
import { sandboxPathFor } from '../../../helper/file-helper'
import { sandboxProjectName } from '../constants'
import { overrideWithBoosterLocalDependencies } from '../../../helper/deps-helper'
import { sleep } from '../../../helper/sleep'
import { deploy } from '../deploy'
import { exec } from 'child-process-promise'

before(async () => {
  console.log('preparing sandboxed project...')
  const configuredAssets = ['assets', 'assetFile.txt']
  const sandboxPath = await createSandboxProject(sandboxPathFor(sandboxProjectName), configuredAssets)

  console.log('overriding Booster dependencies...')
  await overrideWithBoosterLocalDependencies(sandboxPath)

  // This command is also ran during the deployment, but this provider takes dependencies from the root sandboxed directory.
  // This is a bug that only happens on integration-tests, and this is a quick workaround to avoid editing the provider or the cli package.
  await exec('npm install --production --no-bin-links', { cwd: sandboxPath })
  console.log(`starting kubernetes server in ${sandboxPath}...`)
  // start kubernetes
  await deploy(sandboxPath)
  await sleep(10000)
})
