/* eslint-disable @typescript-eslint/explicit-function-return-type */
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/no-magic-numbers */
import { expect } from 'chai'
import * as chai from 'chai'
import * as Library from '../../src/library/commands-adapter'
import { replace, restore } from 'sinon'
import { CognitoIdentityServiceProvider } from 'aws-sdk'
import { UserEnvelope } from '@boostercloud/framework-types'
import { APIGatewayProxyEvent } from 'aws-lambda'
import * as UserEnvelopes from '../../src/library/user-envelopes'

chai.use(require('sinon-chai'))
chai.use(require('chai-as-promised'))

describe('the commands-adapter', () => {
  afterEach(() => {
    restore()
  })

  describe('the `rawCommandToEnvelope` method', () => {
    it('generates an envelope correctly from an AWS event', async () => {
      const userPool = new CognitoIdentityServiceProvider()
      const requestID = '123'
      const expectedUser: UserEnvelope = {
        email: 'test@user.com',
        roles: [],
      }
      const commandEnvelope = {
        requestID,
        typeName: 'TestCommand',
        value: {
          field1: 'test field',
          field2: 42,
        },
        currentUser: expectedUser,
      }
      const AWSEvent = {
        headers: {},
        requestContext: {
          requestId: requestID,
        },
        body: JSON.stringify(commandEnvelope),
      }

      replace(UserEnvelopes, 'fetchUserFromRequest', () => {
        return Promise.resolve(expectedUser)
      })
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

  describe('the `submitCommands` method', () => {
    it('adds the passed command envelopes to the command queue') // TODO: First we have to do a refactor to make Command handling asynchronous
  })
})
