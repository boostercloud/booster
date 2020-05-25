import { Subscriber } from 'rxjs'
import { Mode, SDK } from 'aws-cdk'
import { ObjectIdentifier, ObjectIdentifierList } from 'aws-sdk/clients/s3'

export async function emptyS3Bucket(observer: Subscriber<string>, bucketName: string, aws: SDK): Promise<void> {
  observer.next(bucketName + ': DELETE_IN_PROGRESS')
  const s3 = await aws.s3(await aws.defaultAccount(), await aws.defaultRegion(), Mode.ForWriting)
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
    if (listedObjects.IsTruncated) await emptyS3Bucket(observer, bucketName, aws)
  }
  observer.next(bucketName + ': DELETE_COMPLETE')
}
