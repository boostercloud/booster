/* eslint-disable @typescript-eslint/explicit-function-return-type */
import * as Arr from 'fp-ts/ReadonlyArray'
import * as Str from 'fp-ts/string'
import * as Either from 'fp-ts/Either'
import { constVoid, flow, pipe } from 'fp-ts/function'
import * as RTE from 'fp-ts/ReaderTaskEither'
import { CloudError, CloudModule as Cloud } from './cloud'
import { AwsImplementation } from './aws'
import { CloudFormationClient } from '@aws-sdk/client-cloudformation'
import { S3Client } from '@aws-sdk/client-s3'

const filterStoreNames = Arr.filter(Str.startsWith('my-store-'))
const filterToolkitNames = Arr.filter(Str.includes('toolkit'))
const filterNames = flow(filterStoreNames, filterToolkitNames)
const partitionByType = Arr.partition(Str.includes('toolkit'))

const trace =
  <A>(message: string) =>
  (a: A): RTE.ReaderTaskEither<unknown, never, A> =>
    RTE.fromIO(() => {
      console.log(message, a)
      return a
    })

const listObjectsAlongStorage = (storageName: string) =>
  pipe(
    Cloud.listObjectsInStorage(storageName),
    RTE.chainW(trace('Got objects ' + storageName)),
    RTE.map(Arr.map((objectName: string) => ({ storageName, objectName })))
  )

const inParallel = Arr.traverse(RTE.ApplicativeSeq)

const listAllObjects = (buckets: ReadonlyArray<string>) =>
  pipe(buckets, RTE.traverseArray(listObjectsAlongStorage), RTE.map(Arr.flatten))

const cleanBuckets = pipe(
  RTE.Do,
  RTE.bindW('buckets', () => pipe(Cloud.listStorages(), RTE.map(filterNames))),
  RTE.chainFirstW(({ buckets }) => trace('Found buckets')(buckets)),
  RTE.bindW('objects', ({ buckets }) => listAllObjects(buckets)),
  RTE.chainFirstW(({ objects }) => trace('Found objects')(objects)),
  RTE.bindW('deletedObjects', ({ objects }) =>
    pipe(
      objects,
      RTE.traverseSeqArray(({ storageName, objectName }) => Cloud.deleteObject(storageName, objectName))
    )
  ),
  RTE.bindW('deletedBuckets', ({ buckets }) => pipe(buckets, RTE.traverseSeqArray(Cloud.deleteStorage))),
  RTE.chain(() => RTE.of(constVoid()))
)

const parallelDeleteStacks = inParallel(Cloud.deleteStack)

const cleanStacks = pipe(
  RTE.Do,
  RTE.bindW('stacks', Cloud.getDeployedStacks),
  RTE.bindW('partitionedStacks', ({ stacks }) => RTE.right(partitionByType(filterStoreNames(stacks)))),
  RTE.chainFirstW(({ partitionedStacks }) => parallelDeleteStacks(partitionedStacks.left)),
  RTE.chainW(({ partitionedStacks }) => parallelDeleteStacks(partitionedStacks.right))
)

const cleanAll = pipe(
  cleanBuckets,
  RTE.chain(() => cleanStacks)
)

const handleError = (cloudError: CloudError): void => {
  switch (cloudError._tag) {
    case 'StackOperationError':
      console.error('Stack operation error', cloudError.error)
      break
    case 'StorageError':
      console.error('Storage operation error', cloudError.error)
      break
  }
}

const handleSuccess = (): void => {
  console.log('Cleaned up successfully')
}

export const main = async (): Promise<void> => {
  const region = 'us-east-1'
  const sdk = AwsImplementation(new CloudFormationClient({ region }), new S3Client({ region }))
  pipe(cleanAll)
  const result = await cleanAll(sdk)()
  return pipe(result, Either.fold(handleError, handleSuccess))
}
