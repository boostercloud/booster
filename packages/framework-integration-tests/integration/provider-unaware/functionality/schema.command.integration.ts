import { expect } from 'chai'
import { ApolloClient, NormalizedCacheObject, gql } from '@apollo/client'
import { applicationUnderTest } from '../end-to-end/setup'

describe('schemas', async () => {
  let client: ApolloClient<NormalizedCacheObject>

  before(async () => {
    client = await applicationUnderTest.graphql.client()
  })

  describe('should return the expected schema for commands', async () => {
    it('When there is an ignored parameter', async () => {
      const queryResult = await client.query({
        query: gql`
          query UniversalQuery {
            __type(name: "ChangeCartItemInput") {
              __typename
              name
              kind
              inputFields {
                __typename
                name
                type {
                  __typename
                  name
                  kind
                }
              }
            }
          }
        `,
      })
      const fieldsNames = queryResult.data.__type.inputFields.map((field: any) => field.name)
      expect(fieldsNames).to.have.members(['cartId', 'productId', 'quantity'])
    })
  })
})
