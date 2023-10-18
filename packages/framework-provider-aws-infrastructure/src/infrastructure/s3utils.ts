import { ObjectIdentifier, ObjectIdentifierList } from 'aws-sdk/clients/s3'
import * as AWS from 'aws-sdk'
import { BoosterConfig } from '@boostercloud/framework-types'
import { getLogger } from '@boostercloud/framework-common-helpers'

export async function emptyS3Bucket(config: BoosterConfig, bucketName: string): Promise<void> {
  const logger = getLogger(config, 's3utils#emptyS3Bucket')
  logger.info(bucketName + ': DELETE_IN_PROGRESS')
  const s3 = new AWS.S3()

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
      if (listedObjects.IsTruncated) await emptyS3Bucket(config, bucketName)
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
