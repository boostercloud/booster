/* eslint-disable @typescript-eslint/no-unused-vars */
import { Observable, Subscriber } from 'rxjs'
import { BoosterConfig } from '@boostercloud/framework-types'
import { K8sManagement } from './k8s-sdk/K8sManagement'
import { getProjectNamespaceName } from './utils'
import { exec } from 'child-process-promise'
import { HelmManagement } from './helm'

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
  const nodes = await clusterManager.getAllNodesWithOpenWhiskRole('invoker')
  if (nodes.length == 0) {
    throw new Error(
      'Unable to find a openwhisk invoker node inside the cluster. Please remember to set at least one invoker node'
    )
  }
  const namespace = await clusterManager.getNamespace(projectNamespace)
  if (!namespace) {
    observer.next(`Creating the namespace: ${projectNamespace} for your project`)
    const clusterResponse = await clusterManager.createNamespace(projectNamespace)
    if (!clusterResponse) {
      throw new Error('Unable to create a namespace for your project')
    }
  }

  //TODO: we should check here the current cluster health instead of suppose that all is properly working.
  // This check will be implemented when the architecture will be fully defined

  observer.next('Provisioning all cluster resources 👷')
  //const command = `helm install boost-test boosterchart/openwhisk -n ${projectNamespace} --set whisk.ingress.apiHostName=192.168.64.11`
  //await exec(command)
  const helm = new HelmManagement()
  await helm.init()
  const deploy = await helm.exec(
    `install boost-test boosterchart/openwhisk -n ${projectNamespace} --set whisk.ingress.apiHostName=192.168.64.11`
  )
  observer.next(deploy.stdout)
  observer.next('Deploying your Booster app into the cluster')
  //TODO: upload the user code to the cluster
}

async function nukeBoosterApp(observer: Subscriber<string>, configuration: BoosterConfig): Promise<void> {
  const projectNamespace = getProjectNamespaceName(configuration)
  const clusterManager = new K8sManagement()
  observer.next('Nuking your Booster project 🧨')
  const command = `helm uninstall boost-test -n ${projectNamespace}`
  await exec(command)
  observer.next('Nuking your project namespace 🧨')
  await clusterManager.deleteNamespace(projectNamespace)
}

export function nuke(configuration: BoosterConfig): Observable<string> {
  return new Observable((observer): void => {
    // eslint-disable-next-line @typescript-eslint/no-floating-promises
    nukeBoosterApp(observer, configuration)
      .catch((error): void => observer.error(error))
      .then((): void => observer.complete())
  })
}
