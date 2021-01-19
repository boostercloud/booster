import { BoosterConfig } from '@boostercloud/framework-types'
import { App, Stack, StackProps, CfnOutput } from '@aws-cdk/core'
import { stub, replace, restore, fake } from 'sinon'
import * as fs from 'fs'
import { StaticWebsiteStack } from '../src/static-website-stack'
import { expect } from './expect'
import { BucketDeployment, Source, SourceConfig } from '@aws-cdk/aws-s3-deployment'
import { Bucket, BucketProps } from '@aws-cdk/aws-s3'
import { CloudFrontWebDistribution } from '@aws-cdk/aws-cloudfront'

describe('Static website creation', () => {
  const staticWebsiteBucket = 'staticWebsiteBucket'
  const staticWebsiteDeployment = 'staticWebsiteDeployment'
  const staticWebsiteDistribution = 'staticWebsiteDistribution'
  const cfnOutputId = 'staticWebsiteURL'
  const config = new BoosterConfig('test')
  config.appName = 'testing-app'

  afterEach(() => {
    restore()
  })

  context('when no params are passed', () => {
    it('builds the website stack of a full-stack app correctly if public directory exists', () => {
      const fakeExistsAsync = fake.resolves(true)
      replace(fs, 'existsSync', fakeExistsAsync)

      const appStack = new Stack(new App(), config.resourceNames.applicationStack, {} as StackProps)

      stub(Source, 'asset').returns({
        bind: () =>
          ({
            bucket: new Bucket(appStack, 'fakeBucket', {} as BucketProps),
            zipObjectKey: 'staticSiteDeployment.zip',
          } as SourceConfig),
      })

      StaticWebsiteStack.mountStack(
        {
          bucketName: 'test-bucket-name',
        },
        appStack
      )

      expect(fakeExistsAsync).to.have.been.calledWith('./public')

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

  context('when params are passed', () => {
    it('builds the website stack of a full-stack app correctly if public directory exists', () => {
      const fakeExistsAsync = fake.resolves(true)
      replace(fs, 'existsSync', fakeExistsAsync)

      const appStack = new Stack(new App(), config.resourceNames.applicationStack, {} as StackProps)

      stub(Source, 'asset').returns({
        bind: () =>
          ({
            bucket: new Bucket(appStack, 'fakeBucket', {} as BucketProps),
            zipObjectKey: 'staticSiteDeployment.zip',
          } as SourceConfig),
      })

      StaticWebsiteStack.mountStack(
        {
          bucketName: 'test-bucket-name',
          rootPath: './frontend/dist',
          indexFile: 'main.html',
          errorFile: 'error.html',
        },
        appStack
      )

      expect(fakeExistsAsync).to.have.been.calledWith('./frontend/dist')

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
})
