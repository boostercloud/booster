import { CfnOutput, Stack } from '@aws-cdk/core'
import { Bucket } from '@aws-cdk/aws-s3'
import { BucketDeployment, Source } from '@aws-cdk/aws-s3-deployment'
import { CloudFrontWebDistribution, ViewerCertificate, ViewerProtocolPolicy } from '@aws-cdk/aws-cloudfront'
import { BoosterConfig } from '@boostercloud/framework-types'
import { existsSync } from "fs";

const publicDistPath = './public/dist'
const indexHTML = 'index.html'

export default class StaticWebsiteStack {
  public static build(config: BoosterConfig, stack: Stack): void {
    if (existsSync(publicDistPath)) {
      const staticSiteBucket = new Bucket(stack, 'staticWebsiteBucket', {
        websiteIndexDocument: indexHTML,
        websiteErrorDocument: indexHTML,
        bucketName: config.resourceNames.staticWebsite
      })

      const cloudFrontDistribution = new CloudFrontWebDistribution(stack, 'staticWebsiteDistribution', {
        defaultRootObject: indexHTML,
        viewerProtocolPolicy: ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
        viewerCertificate: ViewerCertificate.fromCloudFrontDefaultCertificate(),
        originConfigs: [
          {
            s3OriginSource: {
              s3BucketSource: staticSiteBucket,
            },
            behaviors: [{ isDefaultBehavior: true }],
          },
        ],
      })

      new BucketDeployment(stack, 'staticWebsiteDeployment', {
        sources: [Source.asset(publicDistPath)],
        destinationBucket: staticSiteBucket,
        distribution: cloudFrontDistribution,
      })

      new CfnOutput(stack, 'staticWebsiteURL', {
        value: `https://${cloudFrontDistribution.domainName}`,
        description: `The URL for the static website generated from ${publicDistPath} directory`,
      })
    }
  }
}
