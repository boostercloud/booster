import { ApolloClient } from 'apollo-client'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'
import { changeCartItem, graphQLClient } from './utils'
import { random } from 'faker'
import { expect } from 'chai'
import * as DataStore from 'nedb'
import { readModelsDatabase } from '@boostercloud/framework-provider-local'
import { ReadModelEnvelope } from '@boostercloud/framework-types'
import { waitForIt } from '../aws/utils'
import * as fs from 'fs'

describe('read-models', () => {
  const readModels: DataStore<ReadModelEnvelope> = new DataStore(readModelsDatabase)

  let client: ApolloClient<NormalizedCacheObject>

  before(async () => {
    client = await graphQLClient()
    fs.unlinkSync(readModelsDatabase)
  })

  context('valid command', () => {
    let mockCartId: string
    let mockProductId: string
    let mockQuantity: number

    beforeEach(() => {
      mockCartId = random.uuid()
      mockProductId = random.uuid()
      mockQuantity = random.number({ min: 1 })
    })

    it('should store read-models in the database', async () => {
      await changeCartItem(client, mockCartId, mockProductId, mockQuantity)

      // Wait until event is stored in database
      await waitForIt(
        async () => readModels.loadDatabase(),
        () => readModels.getAllData().some((readModel: ReadModelEnvelope) => readModel.value.id === mockCartId)
      )

      // Verify the event content
      const result = await new Promise((resolve, reject) =>
        readModels.findOne({ 'value.id': mockCartId }, (err, docs) => {
          err ? reject(err) : resolve(docs)
        })
      )

      const expectedResult = {
        typeName: 'CartReadModel',
        value: {
          id: mockCartId,
          cartItems: [{ productId: mockProductId, quantity: mockQuantity }],
        },
      }

      expect(result).to.deep.include(expectedResult)
    })
  })
})
