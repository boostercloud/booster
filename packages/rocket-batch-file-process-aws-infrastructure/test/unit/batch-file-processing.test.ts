import { BoosterConfig } from '@boostercloud/framework-types'
import { App, IConstruct, Stack, StackProps } from '@aws-cdk/core'
import { Function, Runtime, Code } from '@aws-cdk/aws-lambda'

import { BatchFileProcessingStack, AWSBatchProcessingFilesParams } from '../../src/batch-file-processing'
import { Bucket, EventType } from '@aws-cdk/aws-s3'
import { RocketUtils } from '@boostercloud/framework-provider-aws-infrastructure'

import * as chai from 'chai'
import * as chaiSubset from 'chai-subset'

import { AttributeType, Table } from '@aws-cdk/aws-dynamodb'
import { fake } from 'sinon'

chai.use(require('sinon-chai'))
chai.use(require('chai-as-promised'))
chai.use(chaiSubset)

const expect = chai.expect

describe('Batch file processing stack', () => {
  let appStack: Stack
  let bucketName: string
  let config: BoosterConfig
  let chunkSize: string
  let environmentToCheck: any
  let params: AWSBatchProcessingFilesParams
  let eventsTable: Table
  let eventHandlerFunction: Function

  before(() => {
    bucketName = 'sample-bucket'
    config = new BoosterConfig('test')
    chunkSize = '10'
    config.appName = 'testing-app'

    appStack = new Stack(new App(), config.resourceNames.applicationStack, {} as StackProps)

    // Added dummy infra
    const eventStoreName = 'events-store'
    const eventsMain = 'events-main'
    eventsTable = new Table(appStack, eventStoreName, {
      tableName: eventStoreName,
      partitionKey: { name: 'id', type: AttributeType.STRING },
    })

    eventHandlerFunction = new Function(appStack, eventsMain, {
      memorySize: 1024,
      handler: 'index.handler',
      runtime: Runtime.NODEJS_12_X,
      code: Code.fromInline('{}'),
    })

    environmentToCheck = {
      EVENT_STORE_NAME: { value: eventsTable.tableName },
      CHUNK_SIZE: { value: chunkSize },
      STAGING_BUCKET_NAME: { value: `${bucketName}-staging` },
      ENTITY_TYPE_NAME: { value: 'File' },
      TYPE_NAME: { value: 'FileAdded' },
    }

    params = {
      bucketName,
      chunkSize,
    }
  })

  context('on mount', () => {
    const verifyFunctionPolicies = (currentPolicies: any[], expectedPolicies: any[]) => {
      expectedPolicies.forEach((item) => {
        const plcy = currentPolicies.find((policy) => {
          return policy.resource.includes(item.resource)
        })
        expect(plcy).not.to.be.null
        expect(plcy.action).containSubset(item.action)
      })
    }

    it('creates source bucket, staging bucket and lambda trigger', () => {
      BatchFileProcessingStack.mountStack(params, appStack, config)

      // Source bucket
      const sourceBucket = appStack.node.tryFindChild('sourceUploadBucket') as Bucket
      expect(sourceBucket).not.to.be.null

      const sourceBucketObj = constructToObj(sourceBucket)
      expect(sourceBucketObj).containSubset([{ key: 'physicalName', value: bucketName }])

      const notifications = getConstructObjValueForKey(sourceBucketObj, 'notifications')['lambdaNotifications']
      expect(notifications).containSubset([{ Events: [EventType.OBJECT_CREATED] }])

      // Stagin Bucket
      const staginBucket = appStack.node.tryFindChild('stagingUploadBucket') as Bucket
      expect(staginBucket).not.to.be.null

      const staginBucketObj = constructToObj(staginBucket)
      expect(staginBucketObj).containSubset([{ key: 'physicalName', value: `${bucketName}-staging` }])

      // Trigger
      const fileTriggerFunction = appStack.node.tryFindChild('rocketS3Trigger') as Function
      const fileTriggerFunctionObj = constructToObj(fileTriggerFunction)
      expect(getConstructObjValueForKey(fileTriggerFunctionObj, 'environment')).to.deep.equal(environmentToCheck)

      // Policies
      let grantPrincipal = getConstructObjValueForKey(fileTriggerFunctionObj, 'grantPrincipal')
      let currentPolicies: any[] = grantPrincipal['defaultPolicy']['document']['statements']

      let expectedPolicies = [
        {
          resource: eventsTable.tableArn,
          action: ['dynamodb:Put*'],
        },
        {
          resource: staginBucket.bucketArn,
          action: ['s3:ListObject', 's3:PutObject', 's3:GetObject'],
        },
      ]

      verifyFunctionPolicies(currentPolicies, expectedPolicies)

      // Event Handler Policies
      const eventHandlerFunctionObj = constructToObj(eventHandlerFunction)
      grantPrincipal = getConstructObjValueForKey(eventHandlerFunctionObj, 'grantPrincipal')
      currentPolicies = grantPrincipal['defaultPolicy']['document']['statements']

      expectedPolicies = [
        {
          resource: staginBucket.bucketArn,
          action: ['s3:*'],
        },
      ]

      verifyFunctionPolicies(currentPolicies, expectedPolicies)
    })
  })
  context('on unmount', () => {
    it('empties source and staging bucket', async () => {
      // Dummy rocket util
      const utils: RocketUtils = {
        s3: {
          emptyBucket: fake(),
        },
      }
      await BatchFileProcessingStack.unmountStack(params, utils)
      expect(utils.s3.emptyBucket).to.be.calledWith(bucketName)
      expect(utils.s3.emptyBucket).to.be.calledWith(`${bucketName}-staging`)
    })
  })
})

// Helpers
const constructToObj = (construct: IConstruct): { key: string; value: any }[] => {
  return Object.entries(construct).map((item) => {
    return {
      key: item[0],
      value: item[1],
    }
  })
}

const getConstructObjValueForKey = (obj: { key: string; value: any }[], key: string): any | undefined => {
  return obj.find((item) => item.key === key)?.value
}
