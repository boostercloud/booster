/* eslint-disable @typescript-eslint/no-unused-vars */
import { Observable, EMPTY } from 'rxjs'
import { BoosterConfig } from '@boostercloud/framework-types'
import { K8sManagement } from './k8s-sdk/K8sManagement'

export function deploy(configuration: BoosterConfig): Observable<string> {
  console.log('These pods are here bro!! ')
  return new Observable((observer): void => {
    // eslint-disable-next-line @typescript-eslint/no-floating-promises
    deployBoosterApp()
      .catch((error): void => observer.error(error))
      .then((): void => observer.complete())
  })
}

async function deployBoosterApp(): Promise<void> {
  const test = new K8sManagement()
  const nodes = await test.getallNodesWithOpenWhiskRole('invoker')
  console.log('*****************************')
  console.log(nodes)
}

export function nuke(configuration: BoosterConfig): Observable<string> {
  return EMPTY
}
