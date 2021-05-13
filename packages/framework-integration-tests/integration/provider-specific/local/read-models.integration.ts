import { ApolloClient } from 'apollo-client'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'
import { changeCartItem, graphQLClient } from './utils'
import { random } from 'faker'
import { expect } from 'chai'
import * as DataStore from 'nedb'
import { sandboxPath } from './constants'
import * as path from 'path'
import { waitForIt } from '../../helper/sleep'

describe('read-models', () => {
  let readModels: DataStore<unknown>

  let client: ApolloClient<NormalizedCacheObject>

  before(async () => {
    readModels = new DataStore(path.join(sandboxPath, '.booster', 'read_models.json'))
    client = await graphQLClient()
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

    xit('should store read-models in the database', async () => {
      await changeCartItem(client, mockCartId, mockProductId, mockQuantity)

      // Wait until event is stored in database
      await waitForIt(
        async () => readModels.loadDatabase(),
        () => readModels.getAllData().some((readModel) => readModel.value.id === mockCartId)
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
          checks: 0,
        },
      }

      expect(result).to.deep.include(expectedResult)
    })
  })
})
