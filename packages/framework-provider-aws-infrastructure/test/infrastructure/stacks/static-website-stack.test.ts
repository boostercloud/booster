import { BoosterConfig } from "@boostercloud/framework-types/dist";
import { App, Stack, StackProps, CfnOutput } from '@aws-cdk/core'
import { stub, replace } from "sinon";
import * as fs from "fs";
import StaticWebsiteStack from "../../../dist/infrastructure/stacks/static-website-stack";
import { expect } from "@boostercloud/cli/test/expect";
import { BucketDeployment, Source, SourceConfig } from "@aws-cdk/aws-s3-deployment";
import { Bucket, BucketProps } from "@aws-cdk/aws-s3";
import { CloudFrontWebDistribution } from "@aws-cdk/aws-cloudfront";

describe('Static website creation', () => {
  const staticWebsiteBucket = 'staticWebsiteBucket'
  const staticWebsiteDeployment = 'staticWebsiteDeployment'
  const staticWebsiteDistribution = 'staticWebsiteDistribution'
  const cfnOutputId = 'staticWebsiteURL'
  const config = new BoosterConfig('test')
  config.appName = 'testing-app'
  const appStack = new Stack(new App(), config.resourceNames.applicationStack, {} as StackProps)

  it('builds the website stack of a full-stack app correctly if public directory exists', () => {
    const fakeExistsAsync = stub().resolves(true)
    replace(fs, 'existsSync', fakeExistsAsync)

    stub(Source, 'asset').returns({
      bind: () => ({
        bucket: new Bucket(appStack, 'fakeBucket', {} as BucketProps),
        zipObjectKey: 'staticSiteDeployment.zip'
      }) as SourceConfig
    })

    StaticWebsiteStack.build(config, appStack)

    const staticSiteBucket = appStack.node.tryFindChild(staticWebsiteBucket) as Bucket
    const cloudFrontDistribution = appStack.node.tryFindChild(staticWebsiteDistribution) as CloudFrontWebDistribution
    const bucketDeployment = appStack.node.tryFindChild(staticWebsiteDeployment) as BucketDeployment
    const cfnOutput = appStack.node.tryFindChild(cfnOutputId) as CfnOutput

    expect(staticSiteBucket).not.to.be.undefined
    expect(bucketDeployment).not.to.be.undefined
    expect(cloudFrontDistribution).not.to.be.undefined
    expect(cfnOutput).not.to.be.undefined
  })
})