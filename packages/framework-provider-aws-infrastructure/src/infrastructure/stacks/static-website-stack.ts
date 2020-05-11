import { Stack } from '@aws-cdk/core'
import { Bucket } from '@aws-cdk/aws-s3'
import { BucketDeployment, Source } from '@aws-cdk/aws-s3-deployment'
import { CloudFrontWebDistribution } from '@aws-cdk/aws-cloudfront'
import { BoosterConfig } from '@boostercloud/framework-types'

const publicDistPath = './public/dist'
const indexHTML = 'index.html'

export default class StaticWebsiteStack {
  public static build(config: BoosterConfig, stack: Stack): void {
    const staticSiteBucket = new Bucket(stack, 'staticWebsiteBucket', {
      websiteIndexDocument: indexHTML,
      bucketName: config.resourceNames.staticWebsite,
    })

    new BucketDeployment(stack, 'staticWebsiteDeployment', {
      sources: [Source.asset(publicDistPath)],
      destinationBucket: staticSiteBucket,
    })

    new CloudFrontWebDistribution(stack, 'staticWebsiteDistribution', {
      originConfigs: [
        {
          s3OriginSource: {
            s3BucketSource: staticSiteBucket,
          },
          behaviors: [{ isDefaultBehavior: true }],
        },
      ],
    })
  }
}
