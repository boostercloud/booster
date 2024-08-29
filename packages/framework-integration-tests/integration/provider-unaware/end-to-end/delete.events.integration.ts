import { ApolloClient, ApolloQueryResult, gql, NormalizedCacheObject } from '@apollo/client'
import { internet, random } from 'faker'
import { expect } from '../../helper/expect'
import { waitForIt } from '../../helper/sleep'
import { applicationUnderTest } from './setup'
import {
  EventSearchResponse,
  EventTimeParameterFilter,
  PaginatedEntitiesIdsResult,
} from '@boostercloud/framework-types'
import 'mocha'

describe('Remove Events end-to-end tests', async () => {
  if (process.env.TESTED_PROVIDER === 'AWS') {
    console.log('****************** Warning **********************')
    console.log('AWS provider does not support remove events so these tests are skipped for AWS')
    console.log('*************************************************')
    return
  }
  let anonymousClient: ApolloClient<NormalizedCacheObject>
  let loggedClient: ApolloClient<NormalizedCacheObject>
  let adminTokenClient: ApolloClient<NormalizedCacheObject>
  let specialTokenClient: ApolloClient<NormalizedCacheObject>
  const firstCartId = random.uuid()
  const firstProductId = random.uuid()
  const firstProductSku = random.uuid()
  const secondProductId = random.uuid()
  const secondProductSku = random.uuid()

  before(async () => {
    anonymousClient = applicationUnderTest.graphql.client()
    const userEmail = internet.email()
    const userToken = applicationUnderTest.token.forUser(userEmail, 'UserWithEmail')
    loggedClient = applicationUnderTest.graphql.client(userToken)
    const adminToken = applicationUnderTest.token.forUser('admin@example.com', 'Admin')
    adminTokenClient = applicationUnderTest.graphql.client(adminToken)
    const tokenWithSpecialAccess = applicationUnderTest.token.forUser(userEmail, undefined, {
      customClaims: {
        specialReportAccess: 'true',
      },
    })
    specialTokenClient = applicationUnderTest.graphql.client(tokenWithSpecialAccess)
  })

  it('When there are not events to delete', async () => {
    const deleted = await deleteEvent(anonymousClient, '1', 'Product', '')
    expect(deleted).to.be.false
  })

  describe('When there are events to delete', async () => {
    let firstCartReadModel
    let firstProduct
    let firstProductBySkuReadModel
    let firstProductUpdatesReadModel
    let firstSpecialReportsReadModel
    let secondProduct
    let secondProductBySkuReadModel
    let secondProductUpdatesReadModel
    let secondSpecialReportsReadModel
    let events: Array<any>
    let productEvent
    let firstProductEvent: any
    let secondProductEvent: any
    let firstCartEvent: any

    it('Create a cart', async () => {
      await createCart(anonymousClient, firstCartId, firstProductId)
      firstCartReadModel = await getCart(anonymousClient, firstCartId)
      expect(firstCartReadModel.id).to.be.eq(firstCartId)
    })

    it('and create a first product', async () => {
      await createProduct(loggedClient, firstProductId, firstProductSku)
    })

    it('then the related ReadModels are created', async () => {
      firstProduct = await getProduct(loggedClient, firstProductId)
      expect(firstProduct.id).to.be.eq(firstProductId)
      const firstProductBySkuReadModel = await getProductBySku(loggedClient, firstProductSku)
      expect(firstProductBySkuReadModel.id).to.be.eq(firstProductSku)
      const firstProductUpdatesReadModel = await getProductUpdates(adminTokenClient, firstProductId)
      expect(firstProductUpdatesReadModel.id).to.be.eq(firstProductId)
      const firstSpecialReportsReadModel = await getSpecialReportsReadModel(specialTokenClient, firstProductId)
      expect(firstSpecialReportsReadModel.id).to.be.eq(firstProductId)
    })

    it('when a second product is created', async () => {
      await createProduct(loggedClient, secondProductId, secondProductSku)
    })

    it('then the related ReadModels for the second product are created', async () => {
      secondProduct = await getProduct(loggedClient, secondProductId)
      expect(secondProduct.id).to.be.eq(secondProductId)
      const secondProductBySkuReadModel = await getProductBySku(loggedClient, secondProductSku)
      expect(secondProductBySkuReadModel.id).to.be.eq(secondProductSku)
      const secondProductUpdatesReadModel = await getProductUpdates(adminTokenClient, secondProductId)
      expect(secondProductUpdatesReadModel.id).to.be.eq(secondProductId)
      const secondSpecialReportsReadModel = await getSpecialReportsReadModel(specialTokenClient, secondProductId)
      expect(secondSpecialReportsReadModel.id).to.be.eq(secondProductId)
    })

    it('when find the event for the first product', async () => {
      events = await queryProductByEntity(loggedClient, firstProductId)
      productEvent = events.filter((event) => event.entityID === firstProductId)
      firstProductEvent = productEvent[0]
    })

    it('and delete it', async () => {
      const deleted = await deleteEvent(
        anonymousClient,
        firstProductEvent.entityID.toString(),
        firstProductEvent.entity,
        firstProductEvent.createdAt
      )
      expect(deleted).to.be.true
    })

    it('when find the event for the first product after delete', async () => {
      events = await queryProductByEntity(loggedClient, firstProductId)
      productEvent = events.filter((event) => event.entityID === firstProductId)
      firstProductEvent = productEvent[0]
    })

    it('then the first event is set as deleted', async () => {
      expect(firstProductEvent.deletedAt).not.to.be.undefined
      expect(firstProductEvent.value.productId).to.be.undefined
    })

    it('and there are not ReadModels for first product id', async () => {
      firstProduct = await getProductUndefined(loggedClient, firstProductId)
      expect(firstProduct).to.be.null
      firstProductBySkuReadModel = await getProductBySkuUndefined(loggedClient, firstProductSku)
      expect(firstProductBySkuReadModel).to.be.null
      firstProductUpdatesReadModel = await getProductUpdatesUndefined(adminTokenClient, firstProductId)
      expect(firstProductUpdatesReadModel).to.be.null
      firstSpecialReportsReadModel = await getSpecialReportsReadModelUndefined(specialTokenClient, firstProductId)
      expect(firstSpecialReportsReadModel).to.be.null
    })

    it('when find the event for the second product', async () => {
      events = await queryProductByEntity(loggedClient, secondProductId)
      productEvent = events.filter((event) => event.entityID === secondProductId)
      secondProductEvent = productEvent[0]
    })

    it('then the second event is not changed', async () => {
      expect(secondProductEvent.deletedAt).to.be.null
      expect(secondProductEvent.value.productId).to.be.eq(secondProductId)
    })

    it('and ReadModels for second product id are not deleted', async () => {
      secondProduct = await getProduct(loggedClient, secondProductId)
      expect(secondProduct.id).to.be.eq(secondProductId)
      secondProductBySkuReadModel = await getProductBySku(loggedClient, secondProductSku)
      expect(secondProductBySkuReadModel.id).to.be.eq(secondProductSku)
      secondProductUpdatesReadModel = await getProductUpdates(adminTokenClient, secondProductId)
      expect(secondProductUpdatesReadModel.id).to.be.eq(secondProductId)
      secondSpecialReportsReadModel = await getSpecialReportsReadModel(specialTokenClient, secondProductId)
      expect(secondSpecialReportsReadModel.id).to.be.eq(secondProductId)
    })

    it('and there are not entities for first product but for product 2', async () => {
      const productsEntities = await findEntities(loggedClient, 'Product')
      expect(productsEntities.items.some((product) => product.entityID === firstProductId)).to.be.false
      expect(productsEntities.items.some((product) => product.entityID === secondProductId)).to.be.true
    })

    it('when find the event for the cart', async () => {
      events = await queryCartByEntity(loggedClient, firstCartId)
      const cartEvent = events.filter((event) => event.entityID === firstCartId)
      firstCartEvent = cartEvent[0]
    })

    it('then the cart event is not changed', async () => {
      expect(firstCartEvent.deletedAt).to.be.null
      expect(firstCartEvent.value.cartId).to.be.eq(firstCartId)
    })

    it('and ReadModels for the cart id are not deleted', async () => {
      firstCartReadModel = await getCart(loggedClient, firstCartId)
      expect(firstCartReadModel.id).to.be.eq(firstCartId)
    })
  })
})

async function createCart(client: ApolloClient<unknown>, cartId: string, productId: string): Promise<void> {
  await client.mutate({
    variables: {
      cartId: cartId,
      productId: productId,
    },
    mutation: gql`
      mutation ChangeCartItem($cartId: ID!, $productId: ID!) {
        ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: 1 })
      }
    `,
  })
}

async function createProduct(client: ApolloClient<unknown>, productId: string, sku: string): Promise<void> {
  await client.mutate({
    variables: {
      productId: productId,
      sku: sku,
    },
    mutation: gql`
      mutation CreateProduct($productId: ID!, $sku: String!) {
        CreateProduct(
          input: {
            productID: $productId
            sku: $sku
            displayName: "any"
            description: "any"
            currency: "any"
            priceInCents: 1
          }
        )
      }
    `,
  })
}

async function getCart(client: ApolloClient<unknown>, id: string): Promise<any> {
  const result = await waitForIt(
    () => {
      return client.query({
        variables: {
          id: id,
        },
        query: gql`
          query CartReadModel($id: ID!) {
            CartReadModel(id: $id) {
              id
            }
          }
        `,
      })
    },
    (result) => {
      const resultId = result?.data?.CartReadModel?.id
      return resultId === id
    }
  )
  return result.data.CartReadModel
}

async function waitForProduct(
  client: ApolloClient<unknown>,
  id: string,
  checkFunction: (result: any) => boolean | string
): Promise<any> {
  return await waitForIt(() => {
    return client.query({
      variables: {
        id: id,
      },
      query: gql`
        query ProductReadModel($id: ID!) {
          ProductReadModel(id: $id) {
            id
          }
        }
      `,
    })
  }, checkFunction)
}

async function getProduct(client: ApolloClient<unknown>, id: string): Promise<any> {
  const product = await waitForProduct(client, id, (result) => {
    const resultId = result?.data?.ProductReadModel?.id
    return resultId === id
  })
  return product.data.ProductReadModel
}

async function getProductUndefined(client: ApolloClient<unknown>, id: string): Promise<any> {
  const product = await waitForProduct(client, id, (result) => {
    const resultId = result?.data?.ProductReadModel
    return resultId === null
  })
  return product.data.ProductReadModel
}

async function awaitForProductUpdates(
  client: ApolloClient<unknown>,
  id: string,
  checkResult: (result: any) => boolean | string
) {
  return await waitForIt(() => {
    return client.query({
      variables: {
        id: id,
      },
      query: gql`
        query ProductUpdatesReadModel($id: ID!) {
          ProductUpdatesReadModel(id: $id) {
            id
          }
        }
      `,
    })
  }, checkResult)
}

async function getProductUpdates(client: ApolloClient<unknown>, id: string): Promise<any> {
  const result = await awaitForProductUpdates(client, id, (result) => {
    const resultId = result?.data?.ProductUpdatesReadModel?.id
    return resultId === id
  })
  return result.data.ProductUpdatesReadModel
}

async function getProductUpdatesUndefined(client: ApolloClient<unknown>, id: string): Promise<any> {
  const result = await awaitForProductUpdates(client, id, (result) => {
    const resultId = result?.data?.ProductUpdatesReadModel
    return resultId === null
  })
  return result.data.ProductUpdatesReadModel
}

async function waitForProductBySku(
  client: ApolloClient<unknown>,
  id: string,
  checkResult: (result: any) => boolean | string
) {
  return await waitForIt(() => {
    return client.query({
      variables: {
        id: id,
      },
      query: gql`
        query ProductsBySKU($id: ID!) {
          ProductsBySKU(id: $id) {
            id
          }
        }
      `,
    })
  }, checkResult)
}

async function getProductBySku(client: ApolloClient<unknown>, id: string): Promise<any> {
  const result = await waitForProductBySku(client, id, (result) => {
    const resultId = result?.data?.ProductsBySKU?.id
    return resultId === id
  })
  return result.data.ProductsBySKU
}

async function getProductBySkuUndefined(client: ApolloClient<unknown>, id: string): Promise<any> {
  const result = await waitForProductBySku(client, id, (result) => {
    const resultId = result?.data?.ProductsBySKU
    return resultId === null
  })
  return result.data.ProductsBySKU
}

async function waitForSpecialReports(
  client: ApolloClient<unknown>,
  id: string,
  checkResult: (result: any) => boolean | string
) {
  return await waitForIt(() => {
    return client.query({
      variables: {
        id: id,
      },
      query: gql`
        query SpecialReportsReadModel($id: ID!) {
          SpecialReportsReadModel(id: $id) {
            id
          }
        }
      `,
    })
  }, checkResult)
}

async function getSpecialReportsReadModel(client: ApolloClient<unknown>, id: string): Promise<any> {
  const result = await waitForSpecialReports(client, id, (result) => {
    const resultId = result?.data?.SpecialReportsReadModel?.id
    return resultId === id
  })
  return result.data.SpecialReportsReadModel
}

async function getSpecialReportsReadModelUndefined(client: ApolloClient<unknown>, id: string): Promise<any> {
  const result = await waitForSpecialReports(client, id, (result) => {
    const resultId = result?.data?.SpecialReportsReadModel
    return resultId === null
  })
  return result.data.SpecialReportsReadModel
}

async function deleteEvent(
  client: ApolloClient<unknown>,
  entityId: string,
  entityTypeName: string,
  createdAt: string
): Promise<boolean> {
  const result = await client.mutate({
    variables: {
      entityId: entityId,
      entityTypeName: entityTypeName,
      createdAt: createdAt,
    },
    mutation: gql`
      mutation HardDelete($entityId: String!, $entityTypeName: String!, $createdAt: String!) {
        HardDelete(input: { entityId: $entityId, entityTypeName: $entityTypeName, createdAt: $createdAt })
      }
    `,
  })
  return result?.data.HardDelete
}

function queryByEntity(
  client: ApolloClient<unknown>,
  entity: string,
  timeFilters?: EventTimeParameterFilter,
  entityID?: string,
  limit?: number
): Promise<ApolloQueryResult<any>> {
  const queryTimeFilters = timeFilters ? `, from:"${timeFilters.from}" to:"${timeFilters.to}"` : ''
  const queryEntityID = entityID ? `, entityID:"${entityID}"` : ''
  const queryLimit = limit ? `, limit:${limit}` : ''
  return client.query({
    query: gql`
      query {
        eventsByEntity(entity: ${entity}${queryEntityID}${queryTimeFilters}${queryLimit}) {
            createdAt
            entity
            entityID
            requestID
            type
            user {
                id
                roles
                username
            }
            value
            deletedAt
        }
      }
    `,
  })
}

async function queryProductByEntity(client: ApolloClient<unknown>, entityID: string): Promise<any> {
  const result = await waitForIt(
    () => queryByEntity(client, 'Product', undefined, entityID),
    (result) => {
      const events: Array<EventSearchResponse> = result.data['eventsByEntity']
      const found = events?.find((event) => event.entityID === entityID)
      return found !== undefined
    }
  )
  return result.data['eventsByEntity']
}

async function queryCartByEntity(client: ApolloClient<unknown>, entityID: string): Promise<any> {
  const result = await waitForIt(
    () => queryByEntity(client, 'Cart', undefined, entityID),
    (result) => {
      const events: Array<EventSearchResponse> = result.data['eventsByEntity']
      const found = events?.find((event) => event.entityID === entityID)
      return found !== undefined
    }
  )
  return result.data['eventsByEntity']
}

async function findEntities(client: ApolloClient<unknown>, entityName: string): Promise<PaginatedEntitiesIdsResult> {
  const result = await client.mutate({
    variables: {
      entityName: entityName,
      limit: 99999,
    },
    mutation: gql`
      mutation EntitiesIdsFinder($entityName: String!, $limit: Float!) {
        EntitiesIdsFinder(input: { entityName: $entityName, limit: $limit })
      }
    `,
  })
  return result?.data?.EntitiesIdsFinder
}
