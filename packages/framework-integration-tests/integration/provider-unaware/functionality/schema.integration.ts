/* eslint-disable @typescript-eslint/ban-ts-comment */
import { expect } from 'chai'
import gql from 'graphql-tag'
import { ApolloClient } from 'apollo-client'
import { NormalizedCacheObject } from 'apollo-cache-inmemory'
import { applicationUnderTest } from '../end-to-end/setup'

function expectInputField(
  inputField: unknown,
  name: string,
  typeDotName: string,
  typeDotKind = 'INPUT_OBJECT',
  typeDotTypeName = '__Type',
  typeName = '__InputValue'
): void {
  // @ts-ignore
  expect(inputField.__typename, `with inputField ${JSON.stringify(inputField)}`).to.be.eq(typeName)
  // @ts-ignore
  expect(inputField.name, `with inputField ${JSON.stringify(inputField)}`).to.be.eq(name)

  // @ts-ignore
  expect(inputField.type.__typename, `with inputField ${JSON.stringify(inputField)}`).to.be.eq(typeDotTypeName)
  // @ts-ignore
  expect(inputField.type.name, `with inputField ${JSON.stringify(inputField)}`).to.be.eq(typeDotName)
  // @ts-ignore
  expect(inputField.type.kind, `with inputField ${JSON.stringify(inputField)}`).to.be.eq(typeDotKind)
}

function expectNonNullInputField(
  inputField: unknown,
  name: string,
  typeDotOfTypeDotName: string | null,
  typeDotOfTypeDotKind: string
): void {
  // @ts-ignore
  expect(inputField.__typename, `with inputField ${JSON.stringify(inputField)}`).to.be.eq('__Field')
  // @ts-ignore
  expect(inputField.name, `with inputField ${JSON.stringify(inputField)}`).to.be.eq(name)

  // @ts-ignore
  expect(inputField.type.__typename, `with inputField ${JSON.stringify(inputField)}`).to.be.eq('__Type')
  // @ts-ignore
  expect(inputField.type.name, `with inputField ${JSON.stringify(inputField)}`).to.be.null
  // @ts-ignore
  expect(inputField.type.kind, `with inputField ${JSON.stringify(inputField)}`).to.be.eq('NON_NULL')

  // @ts-ignore
  expect(inputField.type.ofType.__typename, `with inputField ${JSON.stringify(inputField)}`).to.be.eq('__Type')
  if (typeDotOfTypeDotName) {
    // @ts-ignore
    expect(inputField.type.ofType.name, `with inputField ${JSON.stringify(inputField)}`).to.be.eq(typeDotOfTypeDotName)
  } else {
    // @ts-ignore
    expect(inputField.type.ofType.name, `with inputField ${JSON.stringify(inputField)}`).to.be.null
  }
  // @ts-ignore
  expect(inputField.type.ofType.kind, `with inputField ${JSON.stringify(inputField)}`).to.be.eq(typeDotOfTypeDotKind)
}

describe('schemas', async () => {
  let client: ApolloClient<NormalizedCacheObject>

  before(async () => {
    client = await applicationUnderTest.graphql.client()
  })

  it('should return the expected schema for filter fields', async () => {
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
    expect(type.__typename).to.be.eq('__Type')
    expect(type.name).to.be.eq('ListSchemaReadModelFilter')
    expect(type.kind).to.be.eq('INPUT_OBJECT')
    const inputFields = type.inputFields
    expectInputField(inputFields[0], 'id', 'UUIDPropertyFilter')
    expectInputField(inputFields[1], 'date', 'DatePropertyFilter')
    expectInputField(inputFields[2], 'array0', 'StringArrayPropertyFilter')
    expectInputField(inputFields[3], 'array1', 'StringArrayPropertyFilter')
    expectInputField(inputFields[4], 'unionArrays', 'JSONObject', 'SCALAR')
    expectInputField(inputFields[5], 'unionWithNull', 'StringPropertyFilter')
    expectInputField(inputFields[6], 'unionWithUndefined', 'StringPropertyFilter')
    expectInputField(inputFields[7], 'unionWithAny', 'JSONObject', 'SCALAR')
    expectInputField(inputFields[8], 'unionWithObject', 'JSONObject', 'SCALAR')
    expectInputField(inputFields[9], 'unionWithUnknown', 'JSONObject', 'SCALAR')
    expectInputField(inputFields[10], 'func0', 'JSONObject', 'SCALAR')
    expectInputField(inputFields[11], 'any0', 'JSONObject', 'SCALAR')
    expectInputField(inputFields[12], 'unknown0', 'JSONObject', 'SCALAR')
    expectInputField(inputFields[13], 'record', 'JSONObject', 'SCALAR')
    expectInputField(inputFields[14], 'generic', 'JSONObject', 'SCALAR')
    expectInputField(inputFields[15], 'optionalString', 'StringPropertyFilter')
    expectInputField(inputFields[16], 'optionalNull', 'StringPropertyFilter')
    expectInputField(inputFields[17], 'optionalUndefined', 'JSONObject', 'SCALAR')
    expectInputField(inputFields[18], 'optionalUnknown', 'JSONObject', 'SCALAR')
    expectInputField(inputFields[19], 'optionalAny', 'JSONObject', 'SCALAR')
    expectInputField(inputFields[20], 'optionalRecord', 'JSONObject', 'SCALAR')
    expectInputField(inputFields[21], 'optionalGeneric', 'JSONObject', 'SCALAR')
    expectInputField(inputFields[22], 'readonlyArray', 'JSONObject', 'SCALAR')
  })

  it('should return the expected schema for sort fields', async () => {
    const queryResult = await client.query({
      query: gql`
        query UniversalQuery {
          __type(name: "SchemaReadModelSortBy") {
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
    expect(type.__typename).to.be.eq('__Type')
    expect(type.name).to.be.eq('SchemaReadModelSortBy')
    expect(type.kind).to.be.eq('INPUT_OBJECT')
    const inputFields = type.inputFields
    expectInputField(inputFields[0], 'id', 'orderProperty', 'ENUM')
    expectInputField(inputFields[1], 'date', 'orderProperty', 'ENUM')
    expectInputField(inputFields[2], 'array0', 'orderProperty', 'ENUM')
    expectInputField(inputFields[3], 'array1', 'orderProperty', 'ENUM')
    expectInputField(inputFields[4], 'unionArrays', 'orderProperty', 'ENUM')
    expectInputField(inputFields[5], 'unionWithNull', 'orderProperty', 'ENUM')
    expectInputField(inputFields[6], 'unionWithUndefined', 'orderProperty', 'ENUM')
    expectInputField(inputFields[7], 'unionWithAny', 'orderProperty', 'ENUM')
    expectInputField(inputFields[8], 'unionWithObject', 'orderProperty', 'ENUM')
    expectInputField(inputFields[9], 'unionWithUnknown', 'orderProperty', 'ENUM')
    expectInputField(inputFields[10], 'func0', 'orderProperty', 'ENUM')
    expectInputField(inputFields[11], 'any0', 'orderProperty', 'ENUM')
    expectInputField(inputFields[12], 'unknown0', 'orderProperty', 'ENUM')
    expectInputField(inputFields[13], 'record', 'orderProperty', 'ENUM')
    expectInputField(inputFields[14], 'generic', 'orderProperty', 'ENUM')
    expectInputField(inputFields[15], 'optionalString', 'orderProperty', 'ENUM')
    expectInputField(inputFields[16], 'optionalNull', 'orderProperty', 'ENUM')
    expectInputField(inputFields[17], 'optionalUndefined', 'orderProperty', 'ENUM')
    expectInputField(inputFields[18], 'optionalUnknown', 'orderProperty', 'ENUM')
    expectInputField(inputFields[19], 'optionalAny', 'orderProperty', 'ENUM')
    expectInputField(inputFields[20], 'optionalRecord', 'orderProperty', 'ENUM')
    expectInputField(inputFields[21], 'optionalGeneric', 'orderProperty', 'ENUM')
    expectInputField(inputFields[22], 'readonlyArray', 'orderProperty', 'ENUM')
  })

  it('should return the expected schema for search items', async () => {
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
    expect(type.__typename).to.be.eq('__Type')
    expect(type.name).to.be.eq('SchemaReadModel')
    const fields = type.fields

    expectNonNullInputField(fields[0], 'id', 'ID', 'SCALAR')
    expectNonNullInputField(fields[1], 'date', 'Date', 'SCALAR')
    expectNonNullInputField(fields[2], 'array0', null, 'LIST')
    expectNonNullInputField(fields[3], 'array1', null, 'LIST')
    expectNonNullInputField(fields[4], 'unionArrays', 'JSONObject', 'SCALAR')

    expectInputField(fields[5], 'unionWithNull', 'String', 'SCALAR', '__Type', '__Field')
    expectInputField(fields[6], 'unionWithUndefined', 'String', 'SCALAR', '__Type', '__Field')

    expectNonNullInputField(fields[7], 'unionWithAny', 'JSONObject', 'SCALAR')
    expectNonNullInputField(fields[8], 'unionWithObject', 'JSONObject', 'SCALAR')
    expectNonNullInputField(fields[9], 'unionWithUnknown', 'JSONObject', 'SCALAR')
    expectNonNullInputField(fields[10], 'func0', 'JSONObject', 'SCALAR')
    expectNonNullInputField(fields[11], 'any0', 'JSONObject', 'SCALAR')
    expectNonNullInputField(fields[12], 'unknown0', 'JSONObject', 'SCALAR')
    expectNonNullInputField(fields[13], 'record', 'JSONObject', 'SCALAR')
    expectNonNullInputField(fields[14], 'generic', 'JSONObject', 'SCALAR')

    expectInputField(fields[15], 'optionalString', 'String', 'SCALAR', '__Type', '__Field')
    expectInputField(fields[16], 'optionalNull', 'String', 'SCALAR', '__Type', '__Field')
    expectInputField(fields[17], 'optionalUndefined', 'JSONObject', 'SCALAR', '__Type', '__Field')
    expectInputField(fields[18], 'optionalUnknown', 'JSONObject', 'SCALAR', '__Type', '__Field')
    expectInputField(fields[19], 'optionalAny', 'JSONObject', 'SCALAR', '__Type', '__Field')
    expectInputField(fields[20], 'optionalRecord', 'JSONObject', 'SCALAR', '__Type', '__Field')
    expectInputField(fields[21], 'optionalGeneric', 'JSONObject', 'SCALAR', '__Type', '__Field')

    expect(fields[22].__typename).to.be.eq('__Field')
    expect(fields[22].name).to.be.eq('readonlyArray')
    expect(fields[22].type.__typename).to.be.eq('__Type')
    expect(fields[22].type.name).to.be.null
    expect(fields[22].type.kind).to.be.eq('LIST')
    expect(fields[22].type.ofType.__typename).to.be.eq('__Type')
    expect(fields[22].type.ofType.name).to.be.eq('String')
    expect(fields[22].type.ofType.kind).to.be.eq('SCALAR')
  })
})
