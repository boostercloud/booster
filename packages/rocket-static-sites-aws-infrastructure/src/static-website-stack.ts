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

export type AWSStaticSiteParams = {
  localDistPath?: string
  indexFile?: string
  errorFile?: string
}

export class StaticWebsiteStack {
  public static mountStack(params: AWSStaticSiteParams, config: BoosterConfig, stack: Stack): void {
    const localDistPath = params.localDistPath ?? './public'
    const indexFile = params.indexFile ?? 'index.html'
    const errorFile = params.errorFile ?? '404.html'
    if (existsSync(localDistPath)) {
      const staticWebsiteOIA = new OriginAccessIdentity(stack, 'staticWebsiteOIA', {
        comment: 'Allows static site to be reached only via CloudFront',
      })
      const staticSiteBucket = new Bucket(stack, 'staticWebsiteBucket', {
        websiteIndexDocument: indexFile,
        websiteErrorDocument: errorFile,
        bucketName: config.resourceNames.staticWebsite,
        removalPolicy: RemovalPolicy.DESTROY,
      })

      staticSiteBucket.grantRead(staticWebsiteOIA)

      const cloudFrontDistribution = new CloudFrontWebDistribution(stack, 'staticWebsiteDistribution', {
        defaultRootObject: indexFile,
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

      new BucketDeployment(stack, 'staticWebsiteDeployment', {
        sources: [Source.asset(localDistPath)],
        destinationBucket: staticSiteBucket,
        distribution: cloudFrontDistribution,
      })

      new CfnOutput(stack, 'staticWebsiteURL', {
        value: `https://${cloudFrontDistribution.domainName}`,
        description: `The URL for the static website generated from ${localDistPath} directory`,
      })
    } else {
      throw new Error(
        `The plugin '${
          require('package.json').name
        }' tried to deploy a static site from local folder ${localDistPath}, but couldn't find it. Please review the configuration and parameters for this plugin.`
      )
    }
  }
}
