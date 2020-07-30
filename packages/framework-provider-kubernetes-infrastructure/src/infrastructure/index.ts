import { Observable, Subscriber } from 'rxjs'
import { BoosterConfig } from '@boostercloud/framework-types'
import { K8sManagement } from './k8s-sdk/K8sManagement'
import { HelmManager } from './helm-manager'
import { DeployManager } from './deploy-manager'
import { DaprManager } from './dapr-manager'

export function deploy(configuration: BoosterConfig): Observable<string> {
  return new Observable((observer): void => {
    // eslint-disable-next-line @typescript-eslint/no-floating-promises
    deployBoosterApp(observer, configuration)
      .catch((error): void => observer.error(error))
      .then((): void => observer.complete())
  })
}

export function nuke(configuration: BoosterConfig): Observable<string> {
  return new Observable((observer): void => {
    // eslint-disable-next-line @typescript-eslint/no-floating-promises
    nukeBoosterApp(observer, configuration)
      .catch((error): void => observer.error(error))
      .then((): void => observer.complete())
  })
}

async function deployBoosterApp(observer: Subscriber<string>, configuration: BoosterConfig): Promise<void> {
  const clusterManager = new K8sManagement()
  const helmManager = new HelmManager()
  const daprManager = new DaprManager(configuration, clusterManager, helmManager)
  const deployManager = new DeployManager(configuration, clusterManager, daprManager, helmManager)
  try {
    observer.next('Checking your cluster and installed tools')
    await Promise.all([deployManager.verifyNamespace(), deployManager.verifyHelm()])
    observer.next('Checking your volume claim')
    await deployManager.verifyVolumeClaim()
    observer.next('Deploying all neccesary services')
    await Promise.all([deployManager.verifyUploadService(), deployManager.verifyBoosterService()])
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
  } catch (err) {
    throw new Error(err)
  }
}

async function nukeBoosterApp(observer: Subscriber<string>, configuration: BoosterConfig): Promise<void> {
  const clusterManager = new K8sManagement()
  const helmManager = new HelmManager()
  const daprManager = new DaprManager(configuration, clusterManager, helmManager)
  const deployManager = new DeployManager(configuration, clusterManager, daprManager, helmManager)
  try {
    observer.next('Nuking your Booster project 🧨')
    await deployManager.deleteDapr()
    observer.next('Nuking your event store if it was generated by Booster')
    await deployManager.deleteRedis()
    observer.next('Finishing to delete all resources')
    await deployManager.deleteAllResources()
    observer.next('Your app is terminated and destroyed 💥')
    observer.complete
  } catch (err) {
    throw new Error(err)
  }
}
