import { BoosterConfig } from '@boostercloud/framework-types'
import { emptyS3Bucket } from '../infrastructure/s3utils'

interface S3RocketUtils {
  emptyBucket: (bucketName: string) => Promise<void>
}

export interface RocketUtils {
  s3: S3RocketUtils
}

export const buildRocketUtils = (config: BoosterConfig): RocketUtils => ({
  s3: {
    emptyBucket: emptyS3Bucket.bind(null, config),
  },
})
