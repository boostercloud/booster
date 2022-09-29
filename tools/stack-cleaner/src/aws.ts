import { CloudFormationClient, DeleteStackCommand, ListStacksCommand } from '@aws-sdk/client-cloudformation'
import {
  DeleteBucketCommand,
  DeleteObjectCommand,
  GetBucketLocationCommand,
  ListBucketsCommand,
  ListObjectsCommand,
  S3Client,
} from '@aws-sdk/client-s3'
import { CloudSDK } from './cloud'

export const AwsImplementation = (cloudFormation: CloudFormationClient, s3: S3Client): CloudSDK => ({
  async getDeployedStacks(): Promise<string[]> {
    console.log('Getting stacks')
    let nextToken: string | undefined
    const stackNames = [] as string[]
    do {
      const command = new ListStacksCommand({ NextToken: nextToken })
      const stacks = await cloudFormation.send(command)
      const result = (stacks.StackSummaries?.map((summary) => summary.StackName).filter(Boolean) as Array<string>) ?? []
      nextToken = stacks.NextToken
      stackNames.push(...result)
    } while (nextToken)
    return stackNames
  },

  async deleteStack(stackName: string): Promise<void> {
    console.log('Deleting stack', stackName)
    let retryRate = 2000
    let retryAgain = true
    while (retryAgain) {
      if (retryRate > 32_000) {
        break
      }
      try {
        const command = new DeleteStackCommand({ StackName: stackName })
        await cloudFormation.send(command)
        retryAgain = false
      } catch {
        console.log('ThrottlingException, retrying in', retryRate, 'ms')
        await new Promise((resolve) => setTimeout(resolve, retryRate))
        retryRate *= 2
      }
    }
  },

  async listStorages(expectedRegion: string): Promise<string[]> {
    console.log('Getting storages')
    const command = new ListBucketsCommand({})
    const storages = await s3.send(command)
    const result = (storages.Buckets?.map((bucket) => bucket.Name).filter(Boolean) as Array<string>) ?? []
    for (const index in result) {
      console.log('Checking region')
      const isSameRegion = await checkRegion(result[index], s3, expectedRegion)
      if (!isSameRegion) {
        delete result[index]
      }
    }
    return result
  },

  async listObjectsInStorage(storageName: string): Promise<string[]> {
    console.log('Getting objects in storage', storageName)
    const command = new ListObjectsCommand({ Bucket: storageName })
    const objects = await s3.send(command)
    console.log('Got objects in storage', storageName, objects)
    return (objects.Contents?.map((content) => content.Key).filter(Boolean) as Array<string>) ?? []
  },

  async deleteObject(storageName: string, objectName: string): Promise<void> {
    console.log('Deleting object', objectName, 'in storage', storageName)
    const command = new DeleteObjectCommand({ Bucket: storageName, Key: objectName })
    await s3.send(command)
  },

  async deleteStorage(storageName: string): Promise<void> {
    console.log('Deleting storage', storageName)
    const command = new DeleteBucketCommand({ Bucket: storageName })
    await s3.send(command)
  },
})

async function checkRegion(bucketName: string, s3: S3Client, s3Region: string): Promise<boolean> {
  console.log('Getting storage location of', bucketName)
  const getBucketRegionCommand = new GetBucketLocationCommand({ Bucket: bucketName })
  const location = await s3.send(getBucketRegionCommand)
  const bucketRegion = location.LocationConstraint ?? 'us-east-1'
  return bucketRegion === s3Region
}
