/* eslint-disable @typescript-eslint/no-unused-vars */
import { Observable, EMPTY } from 'rxjs'
import { BoosterConfig } from '@boostercloud/framework-types'
import { K8sManagement } from './k8s-sdk/K8sManagement'

export function deploy(configuration: BoosterConfig): Observable<string> {
  console.log('These pods are here bro!! ')
  return new Observable((observer): void => {
    // eslint-disable-next-line @typescript-eslint/no-floating-promises
    myFunc()
      .catch((error): void => observer.error(error))
      .then((): void => observer.complete())
  })
}

async function myFunc(): Promise<void> {
  const test = new K8sManagement()
  console.log('*****************************')
  const namespace = await test.getAllNamespace()
  console.log(namespace)
  console.log('*****************************')
  await test.deleteNamespace('prueba')
  const namespace2 = await test.getAllNamespace()
  console.log(namespace2)
}

export function nuke(configuration: BoosterConfig): Observable<string> {
  return EMPTY
}
