/* eslint-disable @typescript-eslint/no-unused-vars */
import { Observable, EMPTY } from 'rxjs'
import { BoosterConfig } from '@boostercloud/framework-types'

export function deploy(configuration: BoosterConfig): Observable<string> {
  return EMPTY
}

export function nuke(configuration: BoosterConfig): Observable<string> {
  return EMPTY
}
