import { ISDK } from 'aws-cdk'
import { BoosterConfig } from '@boostercloud/framework-types'
import { emptyS3Bucket } from '../infrastructure/s3utils'

interface S3RocketUtils {
  emptyBucket: (bucketName: string) => Promise<void>
}
export interface RocketUtils {
  s3: S3RocketUtils
}

export const buildRocketUtils = (config: BoosterConfig, sdk: ISDK): RocketUtils => ({
  s3: {
    emptyBucket: emptyS3Bucket.bind(undefined, config, sdk),
  },
})
