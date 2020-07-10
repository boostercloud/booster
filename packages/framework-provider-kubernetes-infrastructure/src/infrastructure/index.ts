/* eslint-disable @typescript-eslint/no-unused-vars */
import { Observable, Subscriber } from 'rxjs'
import { BoosterConfig } from '@boostercloud/framework-types'
import { K8sManagement } from './k8s-sdk/K8sManagement'
import { getProjectNamespaceName } from './utils'
import { HelmManager } from './helm-manager'

export function deploy(configuration: BoosterConfig): Observable<string> {
  return new Observable((observer): void => {
    // eslint-disable-next-line @typescript-eslint/no-floating-promises
    deployBoosterApp(observer, configuration)
      .catch((error): void => observer.error(error))
      .then((): void => observer.complete())
  })
}

async function deployBoosterApp(observer: Subscriber<string>, configuration: BoosterConfig): Promise<void> {
  const clusterManager = new K8sManagement()
  const projectNamespace = getProjectNamespaceName(configuration)
  const namespace = await clusterManager.getNamespace(projectNamespace)

  if (!namespace) {
    observer.next(`Creating the namespace: ${projectNamespace} for your project`)
    const clusterResponse = await clusterManager.createNamespace(projectNamespace)
    if (!clusterResponse) {
      throw new Error('Unable to create a namespace for your project, please check your Kubectl configuration')
    }
  }
  const templateValues = {
    environment: configuration.environmentName,
    namespace: projectNamespace,
    clusterVolume: 'booster-pvc',
  }
  observer.next('Preparing the environment to launch tyou code 🏗')
  await clusterManager.applyTemplate('fileUploader', templateValues)
  const fileUploaderReady = await clusterManager.waitForPodToBeReady(projectNamespace, 'fileuploader')
  if (!fileUploaderReady) {
    throw new Error('Unable to create a resource inside your cluster, please review your Kubectl configuration')
  }

  observer.next('Packing your to send it into the space  📦')
  /*
  const namespace = await clusterManager.getNamespace(projectNamespace)
  if (!namespace) {
    observer.next(`Creating the namespace: ${projectNamespace} for your project`)
    const clusterResponse = await clusterManager.createNamespace(projectNamespace)
    if (!clusterResponse) {
      throw new Error('Unable to create a namespace for your project')
    }
  }
  //TODO: we should check here the current cluster health instead of suppose that all is properly working

  observer.next('Provisioning all cluster resources 👷')
  const helm = new HelmManager()
  await helm.init()
  const isHelmReady = await helm.isHelmReady()
  if (!isHelmReady) {
    throw new Error(helm.getHelmError())
  }

  const mainNode = await clusterManager.getMainNode()
  if (!mainNode) {
    throw new Error('Cluster main node not found')
  }

  if (!mainNode.ip) {
    throw new Error('Unable to find the main node IP')
  }
  //TODO: Currently for the IP we are using the main Node IP but this needs to be properly defined when the architecture will be fully defined
  const deploy = await helm.exec(
    `install boost-test boosterchart/openwhisk -n ${projectNamespace} --set whisk.ingress.apiHostName=${mainNode.ip}`
  )
  if (deploy.stderr) {
    throw new Error(deploy.stderr)
  }
  observer.next(deploy.stdout)
  observer.next('Deploying your Booster app into the cluster')
  //TODO: upload the user code to the cluster this will be managed when the cluster architecture will be fully defined*/
  observer.complete()
}

async function nukeBoosterApp(observer: Subscriber<string>, configuration: BoosterConfig): Promise<void> {
  const projectNamespace = getProjectNamespaceName(configuration)
  const clusterManager = new K8sManagement()
  const helm = new HelmManager()
  await helm.init()
  observer.next('Nuking your Booster project 🧨')
  const command = `uninstall boost-test -n ${projectNamespace}`
  const deleteResult = await helm.exec(command)
  if (deleteResult.stderr) {
    throw new Error(deleteResult.stderr)
  }
  observer.next('Nuking your project namespace 🧨')
  const deleteNamespace = await clusterManager.deleteNamespace(projectNamespace)
  if (!deleteNamespace) {
    throw new Error('Unable to delete the app namespace')
  }
  observer.next('Your app is terminated and destroyed 💥')
  observer.complete
}

export function nuke(configuration: BoosterConfig): Observable<string> {
  return new Observable((observer): void => {
    // eslint-disable-next-line @typescript-eslint/no-floating-promises
    nukeBoosterApp(observer, configuration)
      .catch((error): void => observer.error(error))
      .then((): void => observer.complete())
  })
}
