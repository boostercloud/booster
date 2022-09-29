/* eslint-disable @typescript-eslint/explicit-function-return-type */
/* eslint-disable @typescript-eslint/explicit-module-boundary-types */
import { buildModule } from './common'

// Define operations for the module SDK
export interface CloudSDK {
  getDeployedStacks: () => Promise<Array<string>>
  deleteStack: (stackName: string) => Promise<void>
  listStorages: (region: string) => Promise<Array<string>>
  listObjectsInStorage: (storageName: string) => Promise<Array<string>>
  deleteObject: (storageName: string, objectName: string) => Promise<void>
  deleteStorage: (storageName: string) => Promise<void>
}

// Error definition and handling
export type CloudError = { _tag: 'StackOperationError'; error: Error } | { _tag: 'StorageError'; error: Error }

const toStackOperationError = (error: unknown): CloudError => ({
  _tag: 'StackOperationError',
  error: error instanceof Error ? error : new Error(String(error)),
})

const toStorageOperationError = (error: unknown): CloudError => ({
  _tag: 'StorageError',
  error: error instanceof Error ? error : new Error(String(error)),
})

// Define the module, here we combine them
const StackModule = buildModule<CloudSDK, CloudError>(['getDeployedStacks', 'deleteStack'], toStackOperationError)

const StorageModule = buildModule<CloudSDK, CloudError>(
  ['listStorages', 'listObjectsInStorage', 'deleteObject', 'deleteStorage'],
  toStorageOperationError
)

export const CloudModule = {
  ...StackModule,
  ...StorageModule,
}
