import { Stack } from '@aws-cdk/core'
import { Bucket } from '@aws-cdk/aws-s3'
import { BucketDeployment, Source } from '@aws-cdk/aws-s3-deployment'
import { CloudFrontWebDistribution } from '@aws-cdk/aws-cloudfront'
import { BoosterConfig } from '@boostercloud/framework-types'
import { Level } from '@boostercloud/framework-types'
import {buildLogger} from "../../../../framework-core/dist/booster-logger";

const publicDistPath = './public/dist'
const indexHTML = 'index.html'

export default class StaticWebsiteStack {
  public static build(config: BoosterConfig, stack: Stack): void {
    const logger = buildLogger(Level.info)
    const staticSiteBucket = new Bucket(stack, 'staticWebsiteBucket', {
      websiteIndexDocument: indexHTML,
      bucketName: config.resourceNames.staticWebsite,
    })

    logger.debug("////////////////BUCKET/////////////////")
    logger.debug(staticSiteBucket)
    logger.debug("/////////////////////////////////")

    const cloudFrontDistribution = new CloudFrontWebDistribution(stack, 'staticWebsiteDistribution', {
      originConfigs: [
        {
          s3OriginSource: {
            s3BucketSource: staticSiteBucket,
          },
          behaviors: [{ isDefaultBehavior: true }],
        },
      ],
    })

    logger.debug("///////////////CLOUDFRONT DISTRIBUTION//////////////////")
    logger.debug(cloudFrontDistribution)
    logger.debug("/////////////////////////////////")

    logger.debug("////////////////ASSET S3/////////////////")
    logger.debug(Source.asset(publicDistPath))
    logger.debug("/////////////////////////////////")

    new BucketDeployment(stack, 'staticWebsiteDeployment', {
      sources: [Source.asset(publicDistPath)],
      destinationBucket: staticSiteBucket,
      distribution: cloudFrontDistribution,
    })
  }
}
