/* eslint-disable @typescript-eslint/no-explicit-any */
import { expect } from '../expect'
import { random, lorem, internet } from 'faker'
import { stub, replace } from 'sinon'
import { deleteConnectionData, fetchConnectionData, storeConnectionData } from '../../src/library/connections-adapter'
import { DocumentClient } from 'aws-sdk/clients/dynamodb'
import { BoosterConfig, ConnectionDataEnvelope } from '@boostercloud/framework-types'

const config = new BoosterConfig('test')

describe('The "storeConnectionData" method', () => {
  it('stores connection data correctly', async () => {
    const fakeDB: DocumentClient = new DocumentClient()
    const fakePut = stub().returns({
      promise: stub(),
    })
    replace(fakeDB, 'put', fakePut)

    const connectionID = random.uuid()
    const expectedData: ConnectionDataEnvelope = {
      expirationTime: random.number(),
      user: {
        role: lorem.word(),
        username: internet.email(),
      },
    }
    await storeConnectionData(fakeDB, config, connectionID, expectedData)
    expect(fakePut).to.be.calledWith({
      TableName: config.resourceNames.connectionsStore,
      Item: {
        ...expectedData,
        connectionID,
      },
    })
  })
})

describe('The "fetchConnectionData" method', () => {
  it('returns the expected data', async () => {
    const fakeDB: DocumentClient = new DocumentClient()
    const expectedData: ConnectionDataEnvelope = {
      expirationTime: random.number(),
      user: {
        role: lorem.word(),
        username: internet.email(),
      },
    }
    const fakeGet = stub().returns({
      promise: stub().returns({ Item: expectedData }),
    })
    replace(fakeDB, 'get', fakeGet)

    const connectionID = random.uuid()
    const gotData = await fetchConnectionData(fakeDB, config, connectionID)
    expect(fakeGet).to.be.calledWith({
      TableName: config.resourceNames.connectionsStore,
      Key: { connectionID },
      ConsistentRead: true,
    })
    expect(gotData).to.be.deep.equal(expectedData)
  })
})

describe('The "deleteConnectionData" method', () => {
  it('deletes connection data correctly', async () => {
    const fakeDB: DocumentClient = new DocumentClient()
    const fakeDelete = stub().returns({
      promise: stub(),
    })
    replace(fakeDB, 'delete', fakeDelete)

    const connectionID = random.uuid()
    await deleteConnectionData(fakeDB, config, connectionID)
    expect(fakeDelete).to.be.calledWith({
      TableName: config.resourceNames.connectionsStore,
      Key: { connectionID },
    })
  })
})

describe('The "sendMessageToConnection" method', () => {
  it('sends the proper data to the connectionID', () => {
    // TODO: I didn't find a way to properly mock ApiGatewayManagementApi and check it is being called correctly
  })
})
