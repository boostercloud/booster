import { CfnOutput, RemovalPolicy, Stack } from '@aws-cdk/core'
import { Bucket } from '@aws-cdk/aws-s3'
import { BucketDeployment, Source } from '@aws-cdk/aws-s3-deployment'
import {
  CloudFrontWebDistribution,
  OriginAccessIdentity,
  ViewerCertificate,
  ViewerProtocolPolicy,
} from '@aws-cdk/aws-cloudfront'
import { existsSync } from 'fs'
import { RocketUtils } from '@boostercloud/framework-provider-aws-infrastructure'

export type AWSStaticSiteParams = {
  localDistPath?: string
  indexFile?: string
  errorFile?: string
  bucketName: string
}

export class StaticWebsiteStack {
  public static mountStack(params: AWSStaticSiteParams, stack: Stack): void {
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
        bucketName: params.bucketName,
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
        `The rocket '${
          require('package.json').name
        }' tried to deploy a static site from local folder ${localDistPath}, but couldn't find it. Please review the configuration and parameters for this rocket.`
      )
    }
  }

  public static async unmountStack(params: AWSStaticSiteParams, utils: RocketUtils): Promise<void> {
    // The bucket must be empty for the stack deletion to succeed
    await utils.s3.emptyBucket(params.bucketName)
  }
}
