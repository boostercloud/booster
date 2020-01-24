/* eslint-disable @typescript-eslint/explicit-function-return-type */
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/no-magic-numbers */
import { expect } from 'chai'
import * as chai from 'chai'
import * as Library from '../../src/library/commands-adapter'
import { restore, fake, match } from 'sinon'
import { Kinesis, CognitoIdentityServiceProvider } from 'aws-sdk'
import { BoosterConfig, Logger, EventEnvelope } from '@boostercloud/framework-types'
import { UUID } from '@boostercloud/framework-types'
import { APIGatewayProxyEvent } from 'aws-lambda'

chai.use(require('sinon-chai'))
chai.use(require('chai-as-promised'))

const fakeLogger: Logger = {
  info: fake(),
  error: fake(),
  debug: fake(),
}

describe('the commands-adapter', () => {
  afterEach(() => {
    restore()
  })

  describe('the `rawCommandToEnvelope` method', () => {
    it('generates an envelope correctly from an AWS event', async () => {
      const requestID = '123'
      const commandEnvelope = {
        requestID,
        typeName: 'TestCommand',
        value: {
          field1: 'test field',
          field2: 42,
        },
      }
      const AWSEvent = {
        headers: {},
        requestContext: {
          requestId: requestID,
        },
        body: JSON.stringify(commandEnvelope),
      }
      const userPool = new CognitoIdentityServiceProvider()

      const envelope = await Library.rawCommandToEnvelope(userPool, AWSEvent as APIGatewayProxyEvent)

      expect(envelope).to.be.deep.equal(commandEnvelope)
    })

    it('rejects an AWS event that does not contain a body', async () => {
      const AWSEvent = {}
      const userPool = new CognitoIdentityServiceProvider()

      // eslint-disable-next-line @typescript-eslint/no-floating-promises
      expect(Library.rawCommandToEnvelope(userPool, AWSEvent as APIGatewayProxyEvent)).to.be.eventually.rejectedWith(
        /The field "body" from the API Gateway Event arrived empty/
      )
    })
  })

  describe('the `handleCommandResult` method', () => {
    it('publishes the emitted events', async () => {
      const config = new BoosterConfig()
      config.appName = 'test-app'
      const requestID = 'request-id'
      const streamName = config.resourceNames.eventsStream
      const events = [
        {
          entityID(): UUID {
            return '123'
          },
        },
        {
          entityID(): UUID {
            return '456'
          },
        },
      ]

      const fakePutRecords = fake.returns({
        promise: fake.resolves(''),
      })
      const fakeKinesis: Kinesis = { putRecords: fakePutRecords } as any

      const eventEnvelopes = events.map(
        (e): EventEnvelope => {
          return {
            version: 1,
            kind: 'event',
            requestID,
            entityID: e.entityID(),
            entityTypeName: 'fake-entity-name',
            typeName: 'fake-type-name',
            value: {
              entityID: e.entityID,
            },
            createdAt: new Date().toISOString(),
          }
        }
      )

      await Library.handleCommandResult(fakeKinesis, config, eventEnvelopes, fakeLogger)
      expect(fakePutRecords).to.be.calledWith(
        match({
          StreamName: streamName,
          Records: match.has('length', 2),
        })
      )
    })
  })
})
