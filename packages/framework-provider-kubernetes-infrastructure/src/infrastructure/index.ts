/* eslint-disable @typescript-eslint/no-unused-vars */
import { Observable, Subscriber } from 'rxjs'
import { BoosterConfig } from '@boostercloud/framework-types'
import { K8sManagement } from './k8s-sdk/K8sManagement'
import { HelmManager } from './helm-manager'
import { DeployManager } from './deploy-manager'
import { getProjectNamespaceName } from './utils'
import { DaprManager } from './dapr-manager'

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
  const helmManager = new HelmManager()
  const daprManager = new DaprManager(configuration, clusterManager, helmManager)
  const deployManager = new DeployManager(configuration, clusterManager, daprManager, helmManager)

  observer.next('Checking your cluster and installed tools')
  await deployManager.verifyNamespace()
  await deployManager.verifyHelm()
  observer.next('Checking your volume claim')
  await deployManager.verifyVolumeClaim()
  observer.next('Deploying all neccesary services')
  await deployManager.verifyUploadService()
  await deployManager.verifyBoosterService()
  observer.next('Checking your Dapr services')
  await deployManager.verifyDapr()
  observer.next('Waiting for your event store to be ready')
  await deployManager.verifyEventStore()
  observer.next('Waiting for cluster to be ready to receive your code')
  await deployManager.verifyUploadPod()
  observer.next('Packing and uploading your code into the cluster')
  await deployManager.uploadUserCode()
  observer.next('Deploying your booster app 🚀')
  const serviceURL = await deployManager.deployBoosterApp()
  observer.next(`Your app is ready in this url: http://${serviceURL}`)
  observer.complete()
}

async function nukeBoosterApp(observer: Subscriber<string>, configuration: BoosterConfig): Promise<void> {
  const projectNamespace = getProjectNamespaceName(configuration)
  const clusterManager = new K8sManagement()
  const helm = new HelmManager()
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
