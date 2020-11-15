import { ISDK } from 'aws-cdk'
import { ObjectIdentifier, ObjectIdentifierList } from 'aws-sdk/clients/s3'
import * as AWS from 'aws-sdk'
import { Logger } from '@boostercloud/framework-types'

export async function emptyS3Bucket(sdk: ISDK, logger: Logger, bucketName: string): Promise<void> {
  logger.info(bucketName + ': DELETE_IN_PROGRESS')
  const s3: AWS.S3 = await sdk.s3()

  if (await s3BucketExists(bucketName, s3)) {
    const listedObjects = await s3.listObjectVersions({ Bucket: bucketName }).promise()
    const contents = (listedObjects.Versions || []).concat(listedObjects.DeleteMarkers || [])
    if (contents.length > 0) {
      const records: ObjectIdentifierList = contents.map(
        (record) =>
          ({
            Key: record.Key,
            VersionId: record.VersionId,
          } as ObjectIdentifier)
      )
      await s3.deleteObjects({ Bucket: bucketName, Delete: { Objects: records } }).promise()
      if (listedObjects.IsTruncated) await emptyS3Bucket(sdk, logger, bucketName)
    }
    logger.info(bucketName + ': DELETE_COMPLETE')
  } else {
    logger.info(bucketName + ': BUCKET_NOT_FOUND : SKIPPING_DELETION')
  }
}

export async function s3BucketExists(bucketName: string, s3: AWS.S3): Promise<boolean> {
  try {
    await s3.headBucket({ Bucket: bucketName }).promise()
    return true
  } catch (e) {
    return false
  }
}
