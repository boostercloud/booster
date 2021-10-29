import { ObjectIdentifier, ObjectIdentifierList } from 'aws-sdk/clients/s3'
import * as AWS from 'aws-sdk'
import { Logger } from '@boostercloud/framework-types'
import { ISDK } from 'aws-cdk'

export async function emptyBucket(logger: Logger, sdk: ISDK, bucketName: string): Promise<void> {
  logger.info(bucketName + ': DELETE_IN_PROGRESS')
  const s3: AWS.S3 = sdk.s3()

  if (await bucketExists(s3, bucketName)) {
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
      if (listedObjects.IsTruncated) await emptyBucket(logger, sdk, bucketName)
    }
    logger.info(bucketName + ': DELETE_COMPLETE')
  } else {
    logger.info(bucketName + ': BUCKET_NOT_FOUND : SKIPPING_DELETION')
  }
}

export async function bucketExists(s3: AWS.S3, bucketName: string): Promise<boolean> {
  try {
    await s3.headBucket({ Bucket: bucketName }).promise()
    return true
  } catch (e) {
    return false
  }
}
