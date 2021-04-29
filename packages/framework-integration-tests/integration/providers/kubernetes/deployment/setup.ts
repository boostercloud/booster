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

  // So the docker image can find the kubernetes runtime (inside node_modules). Only needed for integration tests.
  await exec(`cp -r node_modules ${sandboxPath}`)

  console.log(`starting kubernetes server in ${sandboxPath}...`)
  // start kubernetes
  await deploy(sandboxPath)
  await sleep(10000)
})
