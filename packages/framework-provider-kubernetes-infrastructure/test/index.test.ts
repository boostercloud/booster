import { deploy, nuke } from '../src/infrastructure/index'
import { BoosterConfig } from '@boostercloud/framework-types'
import { K8sManagement } from '../src/infrastructure/k8s-sdk/K8sManagement'
import { KubeConfig, CoreV1Api } from '@kubernetes/client-node'

import { replace, fake, restore } from 'sinon'
import { expect } from './expect'
import { HelmManager } from '../src/infrastructure/helm-manager'

describe('The user wants to deploy a booster project into the cluster', async () => {
  const ENV_NAME = 'production'
  const APP_NAME = 'test_app'
  const NODE_NAME = 'node'
  const NODE = { name: NODE_NAME, ip: '192.168.1.1', mainNode: true }
  const INCOMPLETE_NODE = { name: NODE_NAME, mainNode: true }
  const NAMESPACE_NAME = 'namespace'
  const NAMESPACE = { name: NAMESPACE_NAME }
  const EXEC_OK = { stdout: 'ok' }
  const ERROR = 'ERROR'
  const EXEC_ERROR = { stderr: ERROR }
  const CONFIGURATION = new BoosterConfig(ENV_NAME)
  CONFIGURATION.appName = APP_NAME

  beforeEach(() => {
    replace(KubeConfig.prototype, 'makeApiClient', fake.returns(new CoreV1Api()))
  })
  afterEach(() => {
    restore()
  })

  const deployAndAssertError = async (errorString: string): Promise<void> => {
    const deployObservable = deploy(CONFIGURATION)
    await deployObservable.subscribe(
      () => {},
      (err) => {
        expect(err.toString()).to.be.equal(errorString)
      }
    )
  }

  it('but there is no invoker node for openwhisk', async () => {
    replace(K8sManagement.prototype, 'getAllNodesWithOpenWhiskRole', fake.resolves([]))
    await deployAndAssertError(
      'Error: Unable to find a openwhisk invoker node inside the cluster. Please remember to set at least one invoker node'
    )
  })

  it('but the system is no able to create the namespace', async () => {
    replace(K8sManagement.prototype, 'getAllNodesWithOpenWhiskRole', fake.resolves([NODE]))
    replace(K8sManagement.prototype, 'getNamespace', fake.resolves(null))
    replace(K8sManagement.prototype, 'createNamespace', fake.resolves(false))
    await deployAndAssertError('Error: Unable to create a namespace for your project')
  })

  it('but helm is not ready to install the resources inside the cluster', async () => {
    replace(K8sManagement.prototype, 'getAllNodesWithOpenWhiskRole', fake.resolves([NODE]))
    replace(K8sManagement.prototype, 'getNamespace', fake.resolves([NAMESPACE]))
    replace(HelmManager.prototype, 'init', fake.resolves(null))
    replace(HelmManager.prototype, 'isHelmReady', fake.resolves(false))
    replace(HelmManager.prototype, 'getHelmError', fake.returns('error with helm'))
    await deployAndAssertError('Error: error with helm')
  })

  it('but helm fails to install the app into the cluster', async () => {
    replace(K8sManagement.prototype, 'getAllNodesWithOpenWhiskRole', fake.resolves([NODE]))
    replace(K8sManagement.prototype, 'getNamespace', fake.resolves(null))
    replace(K8sManagement.prototype, 'createNamespace', fake.resolves(true))
    replace(HelmManager.prototype, 'init', fake.resolves(null))
    replace(HelmManager.prototype, 'isHelmReady', fake.resolves(true))
    replace(K8sManagement.prototype, 'getMainNode', fake.resolves(NODE))
    replace(HelmManager.prototype, 'exec', fake.resolves(EXEC_ERROR))
    await deployAndAssertError(`Error: ${ERROR}`)
  })

  it('but there is no main node inside the cluster', async () => {
    replace(K8sManagement.prototype, 'getAllNodesWithOpenWhiskRole', fake.resolves([NODE]))
    replace(K8sManagement.prototype, 'getNamespace', fake.resolves(null))
    replace(K8sManagement.prototype, 'createNamespace', fake.resolves(true))
    replace(HelmManager.prototype, 'init', fake.resolves(null))
    replace(HelmManager.prototype, 'isHelmReady', fake.resolves(true))
    replace(K8sManagement.prototype, 'getMainNode', fake.resolves(null))
    await deployAndAssertError('Error: Cluster main node not found')
  })

  it('but there is no IP in the main node', async () => {
    replace(K8sManagement.prototype, 'getAllNodesWithOpenWhiskRole', fake.resolves([NODE]))
    replace(K8sManagement.prototype, 'getNamespace', fake.resolves(null))
    replace(K8sManagement.prototype, 'createNamespace', fake.resolves(true))
    replace(HelmManager.prototype, 'init', fake.resolves(null))
    replace(HelmManager.prototype, 'isHelmReady', fake.resolves(true))
    replace(K8sManagement.prototype, 'getMainNode', fake.resolves(INCOMPLETE_NODE))
    await deployAndAssertError('Error: Unable to find the main node IP')
  })

  it('and the infrastructure deployed', async () => {
    replace(K8sManagement.prototype, 'getAllNodesWithOpenWhiskRole', fake.resolves([NODE]))
    replace(K8sManagement.prototype, 'getNamespace', fake.resolves([NAMESPACE]))
    replace(HelmManager.prototype, 'init', fake.resolves(null))
    replace(HelmManager.prototype, 'isHelmReady', fake.resolves(true))
    replace(K8sManagement.prototype, 'getMainNode', fake.resolves(NODE))
    replace(HelmManager.prototype, 'exec', fake.resolves(EXEC_OK))
    const deployObservable = deploy(CONFIGURATION)
    let progressMessage = ''
    deployObservable.subscribe(
      (message) => {
        progressMessage = message
      },
      () => {},
      () => {
        expect(progressMessage).to.be.equal('Deploying your Booster app into the cluster')
      }
    )
    await deployObservable.toPromise()
  })
})

describe('the user wants to nuke a booster app', async () => {
  const ENV_NAME = 'production'
  const APP_NAME = 'test_app'
  const ERROR = 'ERROR'
  const EXEC_ERROR = { stderr: ERROR }
  const EXEC_OK = { stdout: 'ok' }
  const configuration = new BoosterConfig(ENV_NAME)
  configuration.appName = APP_NAME

  beforeEach(() => {
    replace(KubeConfig.prototype, 'makeApiClient', fake.returns(new CoreV1Api()))
  })
  afterEach(() => {
    restore()
  })

  it('but helm fails deleting the booster infrastructure', async () => {
    replace(HelmManager.prototype, 'init', fake.resolves(null))
    replace(HelmManager.prototype, 'isHelmReady', fake.resolves(true))
    replace(HelmManager.prototype, 'exec', fake.resolves(EXEC_ERROR))
    replace(K8sManagement.prototype, 'deleteNamespace', fake.resolves(false))

    const nukeObservable = nuke(configuration)
    await nukeObservable.subscribe(
      () => {},
      (err) => {
        expect(err.toString()).to.be.equal(`Error: ${ERROR}`)
      }
    )
  })

  it('but the namespace is not deleted', async () => {
    replace(HelmManager.prototype, 'init', fake.resolves(null))
    replace(HelmManager.prototype, 'isHelmReady', fake.resolves(true))
    replace(HelmManager.prototype, 'exec', fake.resolves(EXEC_OK))
    replace(K8sManagement.prototype, 'deleteNamespace', fake.resolves(false))

    const nukeObservable = nuke(configuration)
    await nukeObservable.subscribe(
      () => {},
      (err) => {
        expect(err.toString()).to.be.equal('Error: Unable to delete the app namespace')
      }
    )
  })

  it('and the command is executed sucessfully', async () => {
    replace(HelmManager.prototype, 'init', fake.resolves(null))
    replace(HelmManager.prototype, 'isHelmReady', fake.resolves(true))
    replace(HelmManager.prototype, 'exec', fake.resolves(EXEC_OK))
    replace(K8sManagement.prototype, 'deleteNamespace', fake.resolves(true))

    let progressMessage = ''
    const nukeObservable = nuke(configuration)
    nukeObservable.subscribe(
      (message) => {
        progressMessage = message
      },
      () => {},
      () => {
        expect(progressMessage).to.be.equal('Your app is terminated and destroyed 💥')
      }
    )
    await nukeObservable.toPromise()
  })
})
