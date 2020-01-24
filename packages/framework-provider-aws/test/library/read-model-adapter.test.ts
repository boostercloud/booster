/* eslint-disable @typescript-eslint/no-explicit-any */
import * as chai from 'chai'
import { expect } from 'chai'
import { replace, stub, fake } from 'sinon'
import { processReadModelAPICall, fetchReadModel, storeReadModel } from '../../src/library/read-model-adapter'
import { DynamoDB } from 'aws-sdk'
import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import { APIGatewayProxyEvent } from 'aws-lambda'

chai.use(require('sinon-chai'))
chai.use(require('chai-as-promised'))

const logger: Logger = {
  info: fake(),
  error: fake(),
  debug: fake(),
}

describe('the "processReadModelAPICall" method', () => {
  it('fails with no path parameters', async () => {
    const db: DynamoDB.DocumentClient = new DynamoDB.DocumentClient()
    const config = new BoosterConfig()
    const apiEvent: APIGatewayProxyEvent = {} as any

    const expectedStatusCode = 400
    const expectedReason = 'Could not find path parameters in URL'
    const got = await processReadModelAPICall(db, config, apiEvent)
    expect(got.statusCode).to.equal(expectedStatusCode)

    const gotBody = JSON.parse(got.body)
    expect(gotBody.statusCode).to.equal(expectedStatusCode)
    expect(gotBody.reason).to.equal(expectedReason)
  })

  it('fails with no read model name', async () => {
    const db: DynamoDB.DocumentClient = new DynamoDB.DocumentClient()
    const config = new BoosterConfig()
    const apiEvent: APIGatewayProxyEvent = {
      pathParameters: {},
    } as any

    const expectedStatusCode = 400
    const expectedReason = 'No read model name provided'
    const got = await processReadModelAPICall(db, config, apiEvent)
    expect(got.statusCode).to.equal(expectedStatusCode)

    const gotBody = JSON.parse(got.body)
    expect(gotBody.statusCode).to.equal(expectedStatusCode)
    expect(gotBody.reason).to.equal(expectedReason)
  })

  it('fails when requesting all read models and there is a DB error', async () => {
    const db: DynamoDB.DocumentClient = new DynamoDB.DocumentClient()
    const config = new BoosterConfig()
    const apiEvent: APIGatewayProxyEvent = {
      pathParameters: {
        readModelName: 'TestReadModel',
      },
    } as any

    const expectedStatusCode = 503
    const expectedReason = 'An error'

    const fakeScan = stub().throws(expectedReason)
    replace(db, 'scan', fakeScan)

    const got = await processReadModelAPICall(db, config, apiEvent)

    expect(fakeScan).to.have.been.called
    expect(got.statusCode).to.equal(expectedStatusCode)

    const gotBody = JSON.parse(got.body)
    expect(gotBody.statusCode).to.equal(expectedStatusCode)
    expect(gotBody.reason).to.equal(expectedReason)
  })

  it('fails when requesting one read model and there is a DB error', async () => {
    const db: DynamoDB.DocumentClient = new DynamoDB.DocumentClient()
    const config = new BoosterConfig()
    const apiEvent: APIGatewayProxyEvent = {
      pathParameters: {
        readModelName: 'TestReadModel',
        id: '1',
      },
    } as any

    const expectedStatusCode = 503
    const expectedReason = 'An error'

    const fakeGet = stub().throws(expectedReason)
    replace(db, 'get', fakeGet)

    const got = await processReadModelAPICall(db, config, apiEvent)

    expect(fakeGet).to.have.been.called
    expect(got.statusCode).to.equal(expectedStatusCode)

    const gotBody = JSON.parse(got.body)
    expect(gotBody.statusCode).to.equal(expectedStatusCode)
    expect(gotBody.reason).to.equal(expectedReason)
  })

  it('returns correctly an array of items when requested without ID', async () => {
    const db: DynamoDB.DocumentClient = new DynamoDB.DocumentClient()
    const config = new BoosterConfig()
    const apiEvent: APIGatewayProxyEvent = {
      pathParameters: {
        readModelName: 'TestReadModel',
      },
    } as any

    const expectedStatusCode = 200
    // The actual value of each item is irrelevant, as we are telling the DB to return this
    const expectedBody = ['Item1', 'Item2', 'Item3']

    const fakeScan = stub().returns({
      promise: () => Promise.resolve({ Items: expectedBody }),
    })
    replace(db, 'scan', fakeScan)

    const got = await processReadModelAPICall(db, config, apiEvent)

    expect(fakeScan).to.have.been.called
    expect(got.statusCode).to.equal(expectedStatusCode)

    const gotBody = JSON.parse(got.body)
    expect(gotBody).to.be.deep.equal(expectedBody)
  })

  it('returns a specific item when requested with an ID', async () => {
    const db: DynamoDB.DocumentClient = new DynamoDB.DocumentClient()
    const config = new BoosterConfig()
    const apiEvent: APIGatewayProxyEvent = {
      pathParameters: {
        readModelName: 'TestReadModel',
        id: 1,
      },
    } as any

    const expectedStatusCode = 200
    // The actual value the item is irrelevant, as we are telling the DB to return this
    const expectedBody = 'Item1'

    const fakeGet = stub().returns({
      promise: () => Promise.resolve({ Item: expectedBody }),
    })
    replace(db, 'get', fakeGet)

    const got = await processReadModelAPICall(db, config, apiEvent)

    expect(fakeGet).to.have.been.called
    expect(got.statusCode).to.equal(expectedStatusCode)

    const gotBody = JSON.parse(got.body)
    expect(gotBody).to.be.deep.equal(expectedBody)
  })
})

describe('the "fetchReadModel" method', () => {
  it("responds with an error when the read model doesn't exist", async () => {
    const db: DynamoDB.DocumentClient = new DynamoDB.DocumentClient()
    const config = new BoosterConfig()
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
    const config = new BoosterConfig()
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

describe('the "storeReadModel" method', () => {
  it('saves a read model', async () => {
    const db: DynamoDB.DocumentClient = new DynamoDB.DocumentClient()
    const config = new BoosterConfig()
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
