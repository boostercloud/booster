/* eslint-disable @typescript-eslint/no-unused-vars */
import { Observable, EMPTY, Subscriber } from 'rxjs'
import { BoosterConfig } from '@boostercloud/framework-types'
import { K8sManagement } from './k8s-sdk/K8sManagement'

export function deploy(observer: Subscriber<string>, configuration: BoosterConfig): Observable<string> {
  return new Observable((observer): void => {
    // eslint-disable-next-line @typescript-eslint/no-floating-promises
    deployBoosterApp(observer, configuration)
      .catch((error): void => observer.error(error))
      .then((): void => observer.complete())
  })
}

async function deployBoosterApp(observer: Subscriber<string>, configuration: BoosterConfig): Promise<void> {
  const clusterManager = new K8sManagement()
  observer.next('starting the deploy to K8s cluster')
  const nodes = await clusterManager.getAllNodesWithOpenWhiskRole('invoker')
  if (nodes.length == 0) {
    throw new Error(
      'Unable to find a openwhisk invoker node inside the cluster. Please remember to set at least one invoker node.'
    )
  }
}

export function nuke(configuration: BoosterConfig): Observable<string> {
  return EMPTY
}
