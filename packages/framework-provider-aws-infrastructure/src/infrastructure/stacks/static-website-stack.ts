import { CfnOutput, RemovalPolicy, Stack } from '@aws-cdk/core'
import { Bucket } from '@aws-cdk/aws-s3'
import { BucketDeployment, Source } from '@aws-cdk/aws-s3-deployment'
import {
  CloudFrontWebDistribution,
  OriginAccessIdentity,
  ViewerCertificate,
  ViewerProtocolPolicy,
} from '@aws-cdk/aws-cloudfront'
import { BoosterConfig } from '@boostercloud/framework-types'
import { existsSync } from 'fs'

const publicPath = './public'
const indexHTML = 'index.html'

export default class StaticWebsiteStack {
  public constructor(private readonly config: BoosterConfig, private readonly stack: Stack) {}

  public build(): void {
    if (existsSync(publicPath)) {
      const staticWebsiteOIA = new OriginAccessIdentity(this.stack, 'staticWebsiteOIA', {
        comment: 'Allows static site to be reached only via CloudFront',
      })
      const staticSiteBucket = new Bucket(this.stack, 'staticWebsiteBucket', {
        websiteIndexDocument: indexHTML,
        websiteErrorDocument: indexHTML,
        bucketName: this.config.resourceNames.staticWebsite,
        removalPolicy: RemovalPolicy.DESTROY,
      })

      staticSiteBucket.grantRead(staticWebsiteOIA)

      const cloudFrontDistribution = new CloudFrontWebDistribution(this.stack, 'staticWebsiteDistribution', {
        defaultRootObject: indexHTML,
        viewerProtocolPolicy: ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
        viewerCertificate: ViewerCertificate.fromCloudFrontDefaultCertificate(),
        originConfigs: [
          {
            s3OriginSource: {
              s3BucketSource: staticSiteBucket,
              originAccessIdentity: staticWebsiteOIA,
            },
            behaviors: [{ isDefaultBehavior: true }],
          },
        ],
      })

      new BucketDeployment(this.stack, 'staticWebsiteDeployment', {
        sources: [Source.asset(publicPath)],
        destinationBucket: staticSiteBucket,
        distribution: cloudFrontDistribution,
      })

      new CfnOutput(this.stack, 'staticWebsiteURL', {
        value: `https://${cloudFrontDistribution.domainName}`,
        description: `The URL for the static website generated from ${publicPath} directory`,
      })
    }
  }
}
