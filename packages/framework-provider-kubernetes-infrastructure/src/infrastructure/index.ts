/* eslint-disable @typescript-eslint/no-unused-vars */
import { Observable, EMPTY, Subscriber } from 'rxjs'
import { BoosterConfig } from '@boostercloud/framework-types'
import { K8sManagement } from './k8s-sdk/K8sManagement'
import { getProjectNamespaceName } from './k8s-sdk/utils'

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
    const clusterResult = await clusterManager.createNamespace(projectNamespace)
    if (clusterResult) {
      observer.next('Provisioning the cluster resources 👷')
      //TODO: deploy with helm here
    } else {
      throw new Error('Unable to create a namespace for your project')
    }
  }
  observer.next('Deploying your Booster app into the cluster')
  //TODO: update the user code here

  //TODO: we should check here the current stack health instead of suppose that all is properly working.
  // This check should be implemented when the architecture will be fully defined
}

export function nuke(configuration: BoosterConfig): Observable<string> {
  return EMPTY
}
