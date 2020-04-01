/* eslint-disable @typescript-eslint/no-explicit-any */
import * as chai from 'chai'
import { expect } from 'chai'
import { restore, replace, fake } from 'sinon'
import {
  rawReadModelRequestToEnvelope,
  fetchReadModel,
  storeReadModel,
  fetchAllReadModels,
} from '../../src/library/read-model-adapter'
import * as UserEnvelopes from '../../src/library/user-envelopes'
import { CognitoIdentityServiceProvider, DynamoDB } from 'aws-sdk'
import {
  BoosterConfig,
  Logger,
  InvalidParameterError,
  ReadModelRequestEnvelope,
  UserEnvelope,
} from '@boostercloud/framework-types'
import { APIGatewayProxyEvent } from 'aws-lambda'

chai.use(require('sinon-chai'))
chai.use(require('chai-as-promised'))

const logger: Logger = {
  info: fake(),
  error: fake(),
  debug: fake(),
}

describe('the "rawReadModelRequestToEnvelope" method', () => {
  afterEach(() => {
    restore()
  })

  it('fails with no path parameters', async () => {
    const userPool: CognitoIdentityServiceProvider = new CognitoIdentityServiceProvider()
    const apiEvent: APIGatewayProxyEvent = {} as any

    await expect(rawReadModelRequestToEnvelope(userPool, apiEvent)).to.be.eventually.rejectedWith(
      InvalidParameterError,
      'Could not find path parameters in URL'
    )
  })

  it('fails with no read model name', async () => {
    const userPool: CognitoIdentityServiceProvider = new CognitoIdentityServiceProvider()
    const apiEvent: APIGatewayProxyEvent = {
      pathParameters: {},
    } as any

    await expect(rawReadModelRequestToEnvelope(userPool, apiEvent)).to.be.eventually.rejectedWith(
      InvalidParameterError,
      'No read model name provided'
    )
  })

  it('returns the right envelope when requesting all read models', async () => {
    const userPool: CognitoIdentityServiceProvider = new CognitoIdentityServiceProvider()
    const readModelName = 'testReadModel'
    const apiEvent: APIGatewayProxyEvent = {
      headers: {},
      requestContext: { requestId: '123' },
      pathParameters: {
        readModelName,
      },
    } as any

    const expectedRequestEnvelope: ReadModelRequestEnvelope = {
      requestID: '123',
      typeName: readModelName,
      version: 1,
      readModelID: undefined,
      currentUser: undefined,
    }
    await expect(rawReadModelRequestToEnvelope(userPool, apiEvent)).to.be.become(expectedRequestEnvelope)
  })

  it('returns the right envelope when requesting all read models with a logged-in user', async () => {
    const userPool: CognitoIdentityServiceProvider = new CognitoIdentityServiceProvider()
    const readModelName = 'testReadModel'
    const readModelID = '123'
    const expectedUser: UserEnvelope = {
      email: 'test@user.com',
      roles: [],
    }
    const expectedRequestEnvelope: ReadModelRequestEnvelope = {
      requestID: '123',
      typeName: readModelName,
      version: 1,
      readModelID,
      currentUser: expectedUser,
    }

    const apiEvent: APIGatewayProxyEvent = {
      headers: {},
      requestContext: { requestId: '123' },
      pathParameters: {
        readModelName,
        id: readModelID,
      },
    } as any

    replace(UserEnvelopes, 'fetchUserFromRequest', () => {
      return Promise.resolve(expectedUser)
    })
    await expect(rawReadModelRequestToEnvelope(userPool, apiEvent)).to.become(expectedRequestEnvelope)
  })
})

describe('the "fetchReadModel" method', () => {
  it("responds with an error when the read model doesn't exist", async () => {
    const db: DynamoDB.DocumentClient = new DynamoDB.DocumentClient()
    const config = new BoosterConfig('test')
    replace(
      db,
      'get',
      fake.returns({
        promise: fake.rejects('not found'),
      })
    )

    // eslint-disable-next-line @typescript-eslint/no-floating-promises
    expect(fetchReadModel(db, config, logger, 'SomeReadModel', 'someReadModelID')).to.be.eventually.rejectedWith(
      'not found'
    )

    expect(db.get).to.have.been.calledOnceWith({
      TableName: 'new-booster-app-application-stack-SomeReadModel',
      Key: { id: 'someReadModelID' },
    })
  })

  it('responds with a read model when it exist', async () => {
    const db: DynamoDB.DocumentClient = new DynamoDB.DocumentClient()
    const config = new BoosterConfig('test')
    replace(
      db,
      'get',
      fake.returns({
        promise: fake.resolves({ Item: { some: 'object' } }),
      })
    )

    const result = await fetchReadModel(db, config, logger, 'SomeReadModel', 'someReadModelID')

    expect(db.get).to.have.been.calledOnceWithExactly({
      TableName: 'new-booster-app-application-stack-SomeReadModel',
      Key: { id: 'someReadModelID' },
    })
    expect(result).to.deep.equal({ some: 'object' })
  })
})

describe('the "fetchAllReadModels" method', () => {
  it("responds with an error when the read model name doesn't exist", async () => {
    const db: DynamoDB.DocumentClient = new DynamoDB.DocumentClient()
    const config = new BoosterConfig('test')
    replace(
      db,
      'scan',
      fake.returns({
        promise: fake.rejects('not found'),
      })
    )

    // eslint-disable-next-line @typescript-eslint/no-floating-promises
    expect(fetchAllReadModels(db, config, logger, 'SomeReadModel')).to.be.eventually.rejectedWith('not found')

    expect(db.scan).to.have.been.calledOnceWith({
      TableName: 'new-booster-app-application-stack-SomeReadModel',
    })
  })

  it('responds with all read models when the read model name exists', async () => {
    const db: DynamoDB.DocumentClient = new DynamoDB.DocumentClient()
    const config = new BoosterConfig('test')
    const expectedModels = [{ prop: 'first model' }, { prop: 'second model' }]
    replace(
      db,
      'scan',
      fake.returns({
        promise: fake.resolves({ Items: expectedModels }),
      })
    )

    const result = await fetchAllReadModels(db, config, logger, 'SomeReadModel')

    expect(db.scan).to.have.been.calledOnceWithExactly({
      TableName: 'new-booster-app-application-stack-SomeReadModel',
    })
    expect(result).to.deep.equal(expectedModels)
  })
})

describe('the "storeReadModel" method', () => {
  it('saves a read model', async () => {
    const db: DynamoDB.DocumentClient = new DynamoDB.DocumentClient()
    const config = new BoosterConfig('test')
    replace(
      db,
      'put',
      fake.returns({
        promise: fake.resolves({
          $response: {},
        }),
      })
    )

    const something = await storeReadModel(db, config, logger, 'SomeReadModel', { id: 777, some: 'object' } as any)

    expect(db.put).to.have.been.calledOnceWithExactly({
      TableName: 'new-booster-app-application-stack-SomeReadModel',
      Item: { id: 777, some: 'object' },
    })
    expect(something).not.to.be.null
  })
})
