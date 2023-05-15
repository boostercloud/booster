import { AnyClass } from '../typelevel'
import { Register } from './register'

export enum EntityTouchStatus {
  'RUNNING',
  'FINISHED',
}

export interface TouchEntityInterface extends AnyClass {
  start(register: Register): Promise<void>
}

export interface TouchEntityMetadata {
  readonly class: TouchEntityInterface
  touchOptions: TouchEntityParameters
}

export interface TouchEntityParameters {
  readonly order: number
}
