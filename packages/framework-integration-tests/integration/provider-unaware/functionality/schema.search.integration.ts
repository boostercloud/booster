/* eslint-disable @typescript-eslint/ban-ts-comment */
import { expect } from 'chai'
import gql from 'graphql-tag'
import { ApolloClient } from 'apollo-client'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'
import { applicationUnderTest } from '../end-to-end/setup'

const __TYPE = '__Type'
const SCALAR = 'SCALAR'
const JSON_OBJECT = 'JSON'
const NON_NULL = 'NON_NULL'
const __Field = '__Field'
const ID = 'ID'
const DATE = 'Date'
const LIST = 'LIST'
const STRING = 'String'
const OBJECT_KIND = 'OBJECT'
describe('schemas', async () => {
  let client: ApolloClient<NormalizedCacheObject>

  before(async () => {
    client = await applicationUnderTest.graphql.client()
  })

  describe('should return the expected schema for each search items', async () => {
    let fields: Array<unknown>
    beforeEach(async () => {
      const queryResult = await client.query({
        query: gql`
          query UniversalQuery {
            __type(name: "SchemaReadModel") {
              name
              __typename
              fields {
                __typename
                name
                type {
                  __typename
                  name
                  kind
                  ofType {
                    __typename
                    name
                    kind
                  }
                }
              }
            }
          }
        `,
      })

      const data = queryResult.data
      const type = data.__type
      expect(type.__typename).to.be.eq(__TYPE)
      expect(type.name).to.be.eq('SchemaReadModel')
      fields = type.fields
    })

    it('For id (UUID)', () => {
      const expectedResult = {
        __typename: __Field,
        name: 'id',
        type: {
          __typename: __TYPE,
          name: undefined,
          kind: NON_NULL,
          ofType: {
            __typename: __TYPE,
            name: ID,
            kind: SCALAR,
          },
        },
      }
      expect(fields[0]).to.be.eql(expectedResult)
    })

    it('For dates', () => {
      const expectedResult = {
        __typename: __Field,
        name: 'date',
        type: {
          __typename: __TYPE,
          kind: NON_NULL,
          name: undefined,
          ofType: {
            __typename: __TYPE,
            name: DATE,
            kind: SCALAR,
          },
        },
      }
      expect(fields[1]).to.be.eql(expectedResult)
    })

    it('For string arrays defined with brackets', () => {
      const expectedResult = {
        __typename: __Field,
        name: 'array0',
        type: {
          __typename: __TYPE,
          kind: NON_NULL,
          name: undefined,
          ofType: {
            __typename: __TYPE,
            name: undefined,
            kind: LIST,
          },
        },
      }
      expect(fields[2]).to.be.eql(expectedResult)
    })

    it('For string arrays defined with Array', () => {
      const expectedResult = {
        __typename: __Field,
        name: 'array1',
        type: {
          __typename: __TYPE,
          kind: NON_NULL,
          name: undefined,
          ofType: {
            __typename: __TYPE,
            name: undefined,
            kind: LIST,
          },
        },
      }
      expect(fields[3]).to.be.eql(expectedResult)
    })

    it('For union arrays', () => {
      const expectedResult = {
        __typename: __Field,
        name: 'unionArrays',
        type: {
          __typename: __TYPE,
          kind: NON_NULL,
          name: undefined,
          ofType: {
            __typename: __TYPE,
            name: JSON_OBJECT,
            kind: SCALAR,
          },
        },
      }
      expect(fields[4]).to.be.eql(expectedResult)
    })

    it('For union with undefined', () => {
      const expectedResult = {
        __typename: __Field,
        name: 'unionWithNull',
        type: {
          __typename: __TYPE,
          kind: SCALAR,
          name: STRING,
          ofType: undefined,
        },
      }
      expect(fields[5]).to.be.eql(expectedResult)
    })

    it('For union with undefined', () => {
      const expectedResult = {
        __typename: __Field,
        name: 'unionWithUndefined',
        type: {
          __typename: __TYPE,
          kind: SCALAR,
          name: STRING,
          ofType: undefined,
        },
      }
      expect(fields[6]).to.be.eql(expectedResult)
    })

    it('For union with any', () => {
      const expectedResult = {
        __typename: __Field,
        name: 'unionWithAny',
        type: {
          __typename: __TYPE,
          kind: NON_NULL,
          name: undefined,
          ofType: {
            __typename: __TYPE,
            name: JSON_OBJECT,
            kind: SCALAR,
          },
        },
      }
      expect(fields[7]).to.be.eql(expectedResult)
    })

    it('For union with object', () => {
      const expectedResult = {
        __typename: __Field,
        name: 'unionWithObject',
        type: {
          __typename: __TYPE,
          kind: NON_NULL,
          name: undefined,
          ofType: {
            __typename: __TYPE,
            name: JSON_OBJECT,
            kind: SCALAR,
          },
        },
      }
      expect(fields[8]).to.be.eql(expectedResult)
    })

    it('For union with unknown', () => {
      const expectedResult = {
        __typename: __Field,
        name: 'unionWithUnknown',
        type: {
          __typename: __TYPE,
          kind: NON_NULL,
          name: undefined,
          ofType: {
            __typename: __TYPE,
            name: JSON_OBJECT,
            kind: SCALAR,
          },
        },
      }
      expect(fields[9]).to.be.eql(expectedResult)
    })

    it('For functions', () => {
      const expectedResult = {
        __typename: __Field,
        name: 'func0',
        type: {
          __typename: __TYPE,
          kind: NON_NULL,
          name: undefined,
          ofType: {
            __typename: __TYPE,
            name: JSON_OBJECT,
            kind: SCALAR,
          },
        },
      }
      expect(fields[10]).to.be.eql(expectedResult)
    })

    it('For any', () => {
      const expectedResult = {
        __typename: __Field,
        name: 'any0',
        type: {
          __typename: __TYPE,
          kind: NON_NULL,
          name: undefined,
          ofType: {
            __typename: __TYPE,
            name: JSON_OBJECT,
            kind: SCALAR,
          },
        },
      }
      expect(fields[11]).to.be.eql(expectedResult)
    })

    it('For unknown', () => {
      const expectedResult = {
        __typename: __Field,
        name: 'unknown0',
        type: {
          __typename: __TYPE,
          kind: NON_NULL,
          name: undefined,
          ofType: {
            __typename: __TYPE,
            name: JSON_OBJECT,
            kind: SCALAR,
          },
        },
      }
      expect(fields[12]).to.be.eql(expectedResult)
    })

    it('For record', () => {
      const expectedResult = {
        __typename: __Field,
        name: 'record',
        type: {
          __typename: __TYPE,
          kind: NON_NULL,
          name: undefined,
          ofType: {
            __typename: __TYPE,
            name: JSON_OBJECT,
            kind: SCALAR,
          },
        },
      }
      expect(fields[13]).to.be.eql(expectedResult)
    })

    it('For generic', async () => {
      // @ts-ignore
      expect(fields[14].name).to.be.eql('generic')
      // @ts-ignore
      expect(fields[14].__typename).to.be.eql(__Field)
      // @ts-ignore
      expect(fields[14].type.kind).to.be.eql(NON_NULL)
      // @ts-ignore
      expect(fields[14].type.name).to.be.undefined
      // @ts-ignore
      expect(fields[14].type.ofType.__typename).to.be.eql(__TYPE)
      // @ts-ignore
      expect(fields[14].type.ofType.name).to.be.eql(JSON_OBJECT)
      // @ts-ignore
      expect(fields[14].type.ofType.kind).to.be.eql(SCALAR)
    })

    it('For base class', () => {
      const expectedResult = {
        __typename: __Field,
        name: 'child',
        type: {
          __typename: __TYPE,
          kind: NON_NULL,
          name: undefined,
          ofType: {
            __typename: __TYPE,
            name: 'BaseClass',
            kind: OBJECT_KIND,
          },
        },
      }
      expect(fields[15]).to.be.eql(expectedResult)
    })

    it('For optional string', () => {
      const expectedResult = {
        __typename: __Field,
        name: 'optionalString',
        type: {
          __typename: __TYPE,
          kind: SCALAR,
          name: STRING,
          ofType: undefined,
        },
      }
      expect(fields[16]).to.be.eql(expectedResult)
    })

    it('For optional undefined', () => {
      const expectedResult = {
        __typename: __Field,
        name: 'optionalNull',
        type: {
          __typename: __TYPE,
          kind: SCALAR,
          name: STRING,
          ofType: undefined,
        },
      }
      expect(fields[17]).to.be.eql(expectedResult)
    })

    it('For optional undefined', () => {
      const expectedResult = {
        __typename: __Field,
        name: 'optionalUndefined',
        type: {
          __typename: __TYPE,
          kind: SCALAR,
          name: JSON_OBJECT,
          ofType: undefined,
        },
      }
      expect(fields[18]).to.be.eql(expectedResult)
    })

    it('For optional unknown', () => {
      const expectedResult = {
        __typename: __Field,
        name: 'optionalUnknown',
        type: {
          __typename: __TYPE,
          kind: SCALAR,
          name: JSON_OBJECT,
          ofType: undefined,
        },
      }
      expect(fields[19]).to.be.eql(expectedResult)
    })

    it('For optional any', () => {
      const expectedResult = {
        __typename: __Field,
        name: 'optionalAny',
        type: {
          __typename: __TYPE,
          kind: SCALAR,
          name: JSON_OBJECT,
          ofType: undefined,
        },
      }
      expect(fields[20]).to.be.eql(expectedResult)
    })

    it('For optional record', () => {
      const expectedResult = {
        __typename: __Field,
        name: 'optionalRecord',
        type: {
          __typename: __TYPE,
          kind: SCALAR,
          name: JSON_OBJECT,
          ofType: undefined,
        },
      }
      expect(fields[21]).to.be.eql(expectedResult)
    })

    it('For optional generic', async () => {
      const expectedResult = {
        __typename: __Field,
        name: 'optionalGeneric',
        type: {
          __typename: __TYPE,
          kind: SCALAR,
          name: JSON_OBJECT,
          ofType: undefined,
        },
      }
      expect(fields[22]).to.be.eql(expectedResult)
    })

    it('For optional base class', async () => {
      const expectedResult = {
        __typename: __Field,
        name: 'optionalChild',
        type: {
          __typename: __TYPE,
          kind: OBJECT_KIND,
          name: 'BaseClass',
          ofType: undefined,
        },
      }
      expect(fields[23]).to.be.eql(expectedResult)
    })

    it('For readonly array', () => {
      const expectedResult = {
        __typename: __Field,
        name: 'readonlyArray',
        type: {
          __typename: __TYPE,
          kind: LIST,
          name: undefined,
          ofType: {
            __typename: __TYPE,
            name: STRING,
            kind: SCALAR,
          },
        },
      }
      expect(fields[24]).to.be.eql(expectedResult)
    })
  })
})
