/* eslint-disable @typescript-eslint/explicit-function-return-type */
import * as Arr from 'fp-ts/ReadonlyArray'
import * as Str from 'fp-ts/string'
import * as Either from 'fp-ts/Either'
import { constVoid, pipe } from 'fp-ts/function'
import * as RTE from 'fp-ts/ReaderTaskEither'
import { CloudError, CloudModule as Cloud } from './cloud'
import { AwsImplementation } from './aws'
import { CloudFormationClient } from '@aws-sdk/client-cloudformation'
import { S3Client } from '@aws-sdk/client-s3'
import { trace } from './common'
import { Predicate } from 'fp-ts/lib/Predicate'

const partitionByType = Arr.partition(Str.includes('toolkit'))

const listObjectsAlongStorage = (storageName: string) =>
  pipe(
    Cloud.listObjectsInStorage(storageName),
    RTE.chainW(trace('Got objects ' + storageName)),
    RTE.map(Arr.map((objectName: string) => ({ storageName, objectName })))
  )

const inParallel = Arr.traverse(RTE.ApplicativeSeq)

const listAllObjects = (buckets: ReadonlyArray<string>) =>
  pipe(buckets, RTE.traverseArray(listObjectsAlongStorage), RTE.map(Arr.flatten))

const cleanBuckets = (region: string, bucketFilter: Predicate<string>) =>
  pipe(
    RTE.Do,
    RTE.bindW('buckets', () => pipe(Cloud.listStorages(region), RTE.map(Arr.filter(bucketFilter)))),
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

const cleanStacks = (stackFilter: Predicate<string>) =>
  pipe(
    RTE.Do,
    RTE.bindW('stacks', Cloud.getDeployedStacks),
    RTE.bindW('partitionedStacks', ({ stacks }) => RTE.right(partitionByType(Arr.filter(stackFilter)(stacks)))),
    RTE.chainFirstW(({ partitionedStacks }) => parallelDeleteStacks(partitionedStacks.left)),
    RTE.chainW(({ partitionedStacks }) => parallelDeleteStacks(partitionedStacks.right))
  )

type CleanAllInput = { region: string; bucketFilter: Predicate<string>; stackFilter: Predicate<string> }

const cleanAll = ({ region, bucketFilter, stackFilter }: CleanAllInput) =>
  pipe(
    cleanBuckets(region, bucketFilter),
    RTE.chain(() => cleanStacks(stackFilter))
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
  const [regionArgument, ...arguments_] = process.argv.slice(2)
  const region = regionArgument ?? 'us-east-1'
  const sdk = AwsImplementation(new CloudFormationClient({ region }), new S3Client({ region }))
  const stackFilter = (s: string) =>
    pipe(
      arguments_,
      Arr.some((prefix) => Str.startsWith(prefix)(s))
    )
  // Could be more specific, but this is good enough for now
  const bucketFilter = stackFilter
  const result = await cleanAll({ region, bucketFilter, stackFilter })(sdk)()
  return pipe(result, Either.fold(handleError, handleSuccess))
}
