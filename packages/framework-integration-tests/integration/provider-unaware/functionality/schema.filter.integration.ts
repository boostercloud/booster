/* eslint-disable @typescript-eslint/ban-ts-comment */
import { expect } from 'chai'
import gql from 'graphql-tag'
import { ApolloClient } from 'apollo-client'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'
import { applicationUnderTest } from '../end-to-end/setup'

const __TYPE = '__Type'
const INPUT_OBJECT = 'INPUT_OBJECT'
const __INPUT_VALUE = '__InputValue'
const UUID_PROPERTY_FILTER = 'UUIDPropertyFilter'
const DATE_PROPERTY_FILTER = 'DatePropertyFilter'
const STRING_ARRAY_PROPERTY_FILTER = 'StringArrayPropertyFilter'
const STRING_PROPERTY_FILTER = 'StringPropertyFilter'
const SCALAR = 'SCALAR'
const JSON_OBJECT = 'JSON'
const BASE_CLASS_PROPERTY_FILTER = 'BaseClassPropertyFilter'

describe('schemas', async () => {
  let client: ApolloClient<NormalizedCacheObject>

  before(async () => {
    client = await applicationUnderTest.graphql.client()
  })

  describe('should return the expected schema for each filter fields', async () => {
    let inputFields: Array<unknown>
    beforeEach(async () => {
      const queryResult = await client.query({
        query: gql`
          query UniversalQuery {
            __type(name: "ListSchemaReadModelFilter") {
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

      const data = queryResult.data
      const type = data.__type
      expect(type.__typename).to.be.eq(__TYPE)
      expect(type.name).to.be.eq('ListSchemaReadModelFilter')
      expect(type.kind).to.be.eq(INPUT_OBJECT)
      inputFields = type.inputFields
    })

    it('For id (UUID)', () => {
      const expectedResult = {
        __typename: __INPUT_VALUE,
        name: 'id',
        type: {
          __typename: __TYPE,
          name: UUID_PROPERTY_FILTER,
          kind: INPUT_OBJECT,
        },
      }
      expect(inputFields[0]).to.be.eql(expectedResult)
    })

    it('For dates', () => {
      const expectedResult = {
        __typename: __INPUT_VALUE,
        name: 'date',
        type: {
          __typename: __TYPE,
          kind: INPUT_OBJECT,
          name: DATE_PROPERTY_FILTER,
        },
      }
      expect(inputFields[1]).to.be.eql(expectedResult)
    })

    it('For string arrays defined with brackets', () => {
      const expectedResult = {
        __typename: __INPUT_VALUE,
        name: 'array0',
        type: {
          __typename: __TYPE,
          kind: INPUT_OBJECT,
          name: STRING_ARRAY_PROPERTY_FILTER,
        },
      }
      expect(inputFields[2]).to.be.eql(expectedResult)
    })

    it('For string arrays defined with Array', () => {
      const expectedResult = {
        __typename: __INPUT_VALUE,
        name: 'array1',
        type: {
          __typename: __TYPE,
          kind: INPUT_OBJECT,
          name: STRING_ARRAY_PROPERTY_FILTER,
        },
      }
      expect(inputFields[3]).to.be.eql(expectedResult)
    })

    it('For union arrays', () => {
      const expectedResult = {
        __typename: __INPUT_VALUE,
        name: 'unionArrays',
        type: {
          __typename: __TYPE,
          kind: SCALAR, // INPUT_OBJECT
          name: JSON_OBJECT, // UndefinedPropertyFilter
        },
      }
      expect(inputFields[4]).to.be.eql(expectedResult)
    })

    it('For union with undefined', () => {
      const expectedResult = {
        __typename: __INPUT_VALUE,
        name: 'unionWithNull',
        type: {
          __typename: __TYPE,
          kind: INPUT_OBJECT,
          name: STRING_PROPERTY_FILTER,
        },
      }
      expect(inputFields[5]).to.be.eql(expectedResult)
    })

    it('For union with undefined', () => {
      const expectedResult = {
        __typename: __INPUT_VALUE,
        name: 'unionWithUndefined',
        type: {
          __typename: __TYPE,
          kind: INPUT_OBJECT,
          name: STRING_PROPERTY_FILTER,
        },
      }
      expect(inputFields[6]).to.be.eql(expectedResult)
    })

    it('For union with any', () => {
      const expectedResult = {
        __typename: __INPUT_VALUE,
        name: 'unionWithAny',
        type: {
          __typename: __TYPE,
          kind: SCALAR,
          name: JSON_OBJECT,
        },
      }
      expect(inputFields[7]).to.be.eql(expectedResult)
    })

    it('For union with object', () => {
      const expectedResult = {
        __typename: __INPUT_VALUE,
        name: 'unionWithObject',
        type: {
          __typename: __TYPE,
          kind: SCALAR,
          name: JSON_OBJECT,
        },
      }
      expect(inputFields[8]).to.be.eql(expectedResult)
    })

    it('For union with unknown', () => {
      const expectedResult = {
        __typename: __INPUT_VALUE,
        name: 'unionWithUnknown',
        type: {
          __typename: __TYPE,
          kind: SCALAR,
          name: JSON_OBJECT,
        },
      }
      expect(inputFields[9]).to.be.eql(expectedResult)
    })

    it('For functions', () => {
      const expectedResult = {
        __typename: __INPUT_VALUE,
        name: 'func0',
        type: {
          __typename: __TYPE,
          kind: SCALAR,
          name: JSON_OBJECT,
        },
      }
      expect(inputFields[10]).to.be.eql(expectedResult)
    })

    it('For any', () => {
      const expectedResult = {
        __typename: __INPUT_VALUE,
        name: 'any0',
        type: {
          __typename: __TYPE,
          kind: SCALAR,
          name: JSON_OBJECT,
        },
      }
      expect(inputFields[11]).to.be.eql(expectedResult)
    })

    it('For unknown', () => {
      const expectedResult = {
        __typename: __INPUT_VALUE,
        name: 'unknown0',
        type: {
          __typename: __TYPE,
          kind: SCALAR,
          name: JSON_OBJECT,
        },
      }
      expect(inputFields[12]).to.be.eql(expectedResult)
    })

    it('For record', () => {
      const expectedResult = {
        __typename: __INPUT_VALUE,
        name: 'record',
        type: {
          __typename: __TYPE,
          kind: SCALAR,
          name: JSON_OBJECT,
        },
      }
      expect(inputFields[13]).to.be.eql(expectedResult)
    })

    it('For generic', async () => {
      const queryResult = await client.query({
        query: gql`
          query UniversalQuery {
            __type(name: "ListSchemaReadModelFilter") {
              kind
              name
              inputFields(includeDeprecated: false) {
                name
                type {
                  kind
                  name
                  inputFields {
                    name
                    type {
                      kind
                      name
                    }
                  }
                }
              }
            }
          }
        `,
      })
      inputFields = queryResult.data.__type.inputFields
      // @ts-ignore
      expect(inputFields[14].name).to.be.eql('generic')
      // @ts-ignore
      expect(inputFields[14].__typename).to.be.eql(__INPUT_VALUE)
      // @ts-ignore
      expect(inputFields[14].type.kind).to.be.eql(INPUT_OBJECT)
      // @ts-ignore
      expect(inputFields[14].type.name).to.be.eql('GenericClass_string_PropertyFilter')
      // @ts-ignore
      expect(inputFields[14].type.__typename).to.be.eql(__TYPE)
      // @ts-ignore
      expect(inputFields[14].type.inputFields[0].name).to.be.eql('genericValue')
      // @ts-ignore
      expect(inputFields[14].type.inputFields[0].type.kind).to.be.eql(SCALAR)
      // @ts-ignore
      expect(inputFields[14].type.inputFields[0].type.name).to.be.eql(JSON_OBJECT)
      // @ts-ignore
      expect(inputFields[14].type.inputFields[0].type.__typename).to.be.eql(__TYPE)
    })

    it('For base class', () => {
      const expectedResult = {
        __typename: __INPUT_VALUE,
        name: 'child',
        type: {
          __typename: __TYPE,
          kind: INPUT_OBJECT,
          name: BASE_CLASS_PROPERTY_FILTER,
        },
      }
      expect(inputFields[15]).to.be.eql(expectedResult)
    })

    it('For optional string', () => {
      const expectedResult = {
        __typename: __INPUT_VALUE,
        name: 'optionalString',
        type: {
          __typename: __TYPE,
          kind: INPUT_OBJECT,
          name: STRING_PROPERTY_FILTER,
        },
      }
      expect(inputFields[16]).to.be.eql(expectedResult)
    })

    it('For optional undefined', () => {
      const expectedResult = {
        __typename: __INPUT_VALUE,
        name: 'optionalNull',
        type: {
          __typename: __TYPE,
          kind: INPUT_OBJECT,
          name: STRING_PROPERTY_FILTER,
        },
      }
      expect(inputFields[17]).to.be.eql(expectedResult)
    })

    it('For optional undefined', () => {
      const expectedResult = {
        __typename: __INPUT_VALUE,
        name: 'optionalUndefined',
        type: {
          __typename: __TYPE,
          kind: SCALAR,
          name: JSON_OBJECT,
        },
      }
      expect(inputFields[18]).to.be.eql(expectedResult)
    })

    it('For optional unknown', () => {
      const expectedResult = {
        __typename: __INPUT_VALUE,
        name: 'optionalUnknown',
        type: {
          __typename: __TYPE,
          kind: SCALAR,
          name: JSON_OBJECT,
        },
      }
      expect(inputFields[19]).to.be.eql(expectedResult)
    })

    it('For optional any', () => {
      const expectedResult = {
        __typename: __INPUT_VALUE,
        name: 'optionalAny',
        type: {
          __typename: __TYPE,
          kind: SCALAR,
          name: JSON_OBJECT,
        },
      }
      expect(inputFields[20]).to.be.eql(expectedResult)
    })

    it('For optional record', () => {
      const expectedResult = {
        __typename: __INPUT_VALUE,
        name: 'optionalRecord',
        type: {
          __typename: __TYPE,
          kind: SCALAR,
          name: JSON_OBJECT,
        },
      }
      expect(inputFields[21]).to.be.eql(expectedResult)
    })

    it('For optional generic', async () => {
      const queryResult = await client.query({
        query: gql`
          query UniversalQuery {
            __type(name: "ListSchemaReadModelFilter") {
              kind
              name
              inputFields(includeDeprecated: false) {
                name
                type {
                  kind
                  name
                  inputFields {
                    name
                    type {
                      kind
                      name
                    }
                  }
                }
              }
            }
          }
        `,
      })
      inputFields = queryResult.data.__type.inputFields
      // @ts-ignore
      expect(inputFields[22].name).to.be.eql('optionalGeneric')
      // @ts-ignore
      expect(inputFields[22].__typename).to.be.eql(__INPUT_VALUE)
      // @ts-ignore
      expect(inputFields[22].type.kind).to.be.eql(INPUT_OBJECT)
      // @ts-ignore
      expect(inputFields[22].type.name).to.be.eql('GenericClass_Cart_PropertyFilter')
      // @ts-ignore
      expect(inputFields[22].type.__typename).to.be.eql(__TYPE)
      // @ts-ignore
      expect(inputFields[22].type.inputFields[0].name).to.be.eql('genericValue')
      // @ts-ignore
      expect(inputFields[22].type.inputFields[0].type.kind).to.be.eql(SCALAR)
      // @ts-ignore
      expect(inputFields[22].type.inputFields[0].type.name).to.be.eql(JSON_OBJECT)
      // @ts-ignore
      expect(inputFields[22].type.inputFields[0].type.__typename).to.be.eql(__TYPE)
    })

    it('For optional base class', async () => {
      const queryResult = await client.query({
        query: gql`
          query UniversalQuery {
            __type(name: "ListSchemaReadModelFilter") {
              kind
              name
              inputFields(includeDeprecated: false) {
                name
                type {
                  kind
                  name
                  inputFields {
                    name
                    type {
                      kind
                      name
                    }
                  }
                }
              }
            }
          }
        `,
      })
      inputFields = queryResult.data.__type.inputFields

      // @ts-ignore
      expect(inputFields[23].name).to.be.eql('optionalChild')
      // @ts-ignore
      expect(inputFields[23].__typename).to.be.eql(__INPUT_VALUE)
      // @ts-ignore
      expect(inputFields[23].type.kind).to.be.eql(INPUT_OBJECT)
      // @ts-ignore
      expect(inputFields[23].type.name).to.be.eql('BaseClassPropertyFilter')
      // @ts-ignore
      expect(inputFields[23].type.inputFields[0].name).to.be.eql('base')
      // @ts-ignore
      expect(inputFields[23].type.inputFields[0].__typename).to.be.eql(__INPUT_VALUE)
      // @ts-ignore
      expect(inputFields[23].type.inputFields[0].type.kind).to.be.eql(INPUT_OBJECT)
      // @ts-ignore
      expect(inputFields[23].type.inputFields[0].type.name).to.be.eql(STRING_PROPERTY_FILTER)
      // @ts-ignore
      expect(inputFields[23].type.inputFields[0].type.__typename).to.be.eql(__TYPE)
      // @ts-ignore
      expect(inputFields[23].type.__typename).to.be.eql(__TYPE)
    })

    it('For readonly array', () => {
      const expectedResult = {
        __typename: __INPUT_VALUE,
        name: 'readonlyArray',
        type: {
          __typename: __TYPE,
          kind: SCALAR,
          name: JSON_OBJECT,
        },
      }
      expect(inputFields[24]).to.be.eql(expectedResult)
    })
  })
})
