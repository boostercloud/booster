import { createSandboxProject } from '../../../../../cli/src/common/sandbox'
import { sandboxPathFor } from '../../../helper/file-helper'
import { sandboxProjectName } from '../constants'
import { overrideWithBoosterLocalDependencies } from '../../../helper/deps-helper'
import { runCommand } from '../../../helper/run-command'
import { sleep } from '../../../helper/sleep'
import { deploy } from '../deploy'

// PENDING:
// 1. Config the minikube github action: https://github.com/marketplace/actions/minikube-single-node-kubernetes-action
// 2. minikube start && kubectl config set-context minikube --namespace booster-my-store-kubernetes

// dapr dashboard -k -n booster-my-store-kubernetes

before(async () => {
  console.log('preparing sandboxed project...')
  const configuredAssets = ['assets', 'assetFile.txt']
  const sandboxPath = await createSandboxProject(sandboxPathFor(sandboxProjectName), configuredAssets)

  console.log('overriding Booster dependencies...')
  await overrideWithBoosterLocalDependencies(sandboxPath)

  console.log('installing dependencies...')
  await runCommand(sandboxPath, 'npm install')

  console.log(`starting kubernetes server in ${sandboxPath}...`)
  // start kubernetes
  await deploy(sandboxPath)
  await sleep(10000)
})
