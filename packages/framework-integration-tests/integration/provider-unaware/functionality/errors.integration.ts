import { ApolloClient, NormalizedCacheObject, gql } from '@apollo/client'
import { applicationUnderTest } from './setup'
import { internet, random } from 'faker'
import { expect } from './expect'
import {
  commandHandlerBeforeErrorCartId,
  commandHandlerBeforeErrorCartMessage,
  commandHandlerErrorCartId,
  commandHandlerErrorCartMessage,
  commandHandlerErrorIgnoredCartId,
  queryHandlerErrorCartId,
  queryHandlerErrorCartMessage,
} from '../../../src/constants'
import 'mocha'

describe('Global error handler', async () => {
  let client: ApolloClient<NormalizedCacheObject>

  before(async () => {
    const adminEmail = internet.email()
    const authToken = applicationUnderTest.token.forUser(adminEmail, 'Admin')
    client = applicationUnderTest.graphql.client(authToken)
  })

  context('CommandHandler', async () => {
    it('should update error object when handler fails', async () => {
      const expectedErrorMessage = `${commandHandlerErrorCartMessage}-onCommandHandlerError with metadata: {"before":[null,null],"properties":[{"name":"cartId","typeInfo":{"name":"UUID","typeGroup":"Class","isNullable":false,"isGetAccessor":false,"parameters":[],"importPath":"@boostercloud/framework-types","typeName":"UUID"}},{"name":"productId","typeInfo":{"name":"UUID","typeGroup":"Class","isNullable":false,"isGetAccessor":false,"parameters":[],"importPath":"@boostercloud/framework-types","typeName":"UUID"}},{"name":"quantity","typeInfo":{"name":"number","typeGroup":"Number","isNullable":false,"isGetAccessor":false,"parameters":[],"typeName":"Number"}},{"name":"test","typeInfo":{"name":"number","typeGroup":"Number","isNullable":false,"isGetAccessor":false,"parameters":[],"typeName":"Number"}}],"methods":[{"name":"beforeFn","typeInfo":{"name":"Promise<CommandInput>","typeGroup":"Object","isNullable":false,"isGetAccessor":false,"parameters":[{"name":"CommandInput","typeGroup":"Type","isNullable":false,"isGetAccessor":false,"parameters":[]}],"typeName":"Promise"}},{"name":"beforeFnV2","typeInfo":{"name":"Promise<CommandInput>","typeGroup":"Object","isNullable":false,"isGetAccessor":false,"parameters":[{"name":"CommandInput","typeGroup":"Type","isNullable":false,"isGetAccessor":false,"parameters":[]}],"typeName":"Promise"}},{"name":"handle","typeInfo":{"name":"Promise<void>","typeGroup":"Object","isNullable":false,"isGetAccessor":false,"parameters":[{"name":"void","typeGroup":"Other","isNullable":false,"isGetAccessor":false,"parameters":[]}],"typeName":"Promise"}}]}-onError`
      await expect(
        client.mutate({
          variables: {
            cartId: commandHandlerErrorCartId,
            productId: random.uuid(),
            quantity: random.number({ min: 1 }),
          },
          mutation: gql`
            mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
              ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
            }
          `,
        })
      ).to.be.eventually.rejectedWith(expectedErrorMessage)
    })

    it('should ignore error object when handler returns undefined', async () => {
      await expect(
        client.mutate({
          variables: {
            cartId: commandHandlerErrorIgnoredCartId,
            productId: random.uuid(),
            quantity: random.number({ min: 1 }),
          },
          mutation: gql`
            mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
              ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
            }
          `,
        })
      ).to.be.eventually.eql({ data: { ChangeCartItem: true } })
    })

    it('should update error object when onBefore fails', async () => {
      const expectedErrorMessage = `${commandHandlerBeforeErrorCartMessage}-onBeforeCommandHandlerError with metadata: {"before":[null,null],"properties":[{"name":"cartId","typeInfo":{"name":"UUID","typeGroup":"Class","isNullable":false,"isGetAccessor":false,"parameters":[],"importPath":"@boostercloud/framework-types","typeName":"UUID"}},{"name":"productId","typeInfo":{"name":"UUID","typeGroup":"Class","isNullable":false,"isGetAccessor":false,"parameters":[],"importPath":"@boostercloud/framework-types","typeName":"UUID"}},{"name":"quantity","typeInfo":{"name":"number","typeGroup":"Number","isNullable":false,"isGetAccessor":false,"parameters":[],"typeName":"Number"}},{"name":"test","typeInfo":{"name":"number","typeGroup":"Number","isNullable":false,"isGetAccessor":false,"parameters":[],"typeName":"Number"}}],"methods":[{"name":"beforeFn","typeInfo":{"name":"Promise<CommandInput>","typeGroup":"Object","isNullable":false,"isGetAccessor":false,"parameters":[{"name":"CommandInput","typeGroup":"Type","isNullable":false,"isGetAccessor":false,"parameters":[]}],"typeName":"Promise"}},{"name":"beforeFnV2","typeInfo":{"name":"Promise<CommandInput>","typeGroup":"Object","isNullable":false,"isGetAccessor":false,"parameters":[{"name":"CommandInput","typeGroup":"Type","isNullable":false,"isGetAccessor":false,"parameters":[]}],"typeName":"Promise"}},{"name":"handle","typeInfo":{"name":"Promise<void>","typeGroup":"Object","isNullable":false,"isGetAccessor":false,"parameters":[{"name":"void","typeGroup":"Other","isNullable":false,"isGetAccessor":false,"parameters":[]}],"typeName":"Promise"}}]}-onError`
      await expect(
        client.mutate({
          variables: {
            cartId: commandHandlerBeforeErrorCartId,
            productId: random.uuid(),
            quantity: random.number({ min: 1 }),
          },
          mutation: gql`
            mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
              ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
            }
          `,
        })
      ).to.be.eventually.rejectedWith(expectedErrorMessage)
    })
  })

  context('QueryHandler', async () => {
    it('should update error object when handler fails', async () => {
      const expectedErrorMessage = `${queryHandlerErrorCartMessage}-onQueryHandlerError-onError`
      await expect(
        client.mutate({
          variables: {
            cartId: queryHandlerErrorCartId,
          },
          mutation: gql`
            query CartTotalQuantity($cartId: ID!) {
              CartTotalQuantity(input: { cartId: $cartId })
            }
          `,
        })
      ).to.be.eventually.rejectedWith(expectedErrorMessage)
    })
  })

  // TODO dispatch doesn't returns an error but catch it and log it in the console
  // context('DispatchEventHandler', async () => {}

  // TODO reducer doesn't returns an error but catch it and log it in the console
  // context('onReducerError', async () => {}

  // TODO projection doesn't returns an error but catch it and log it in the console
  // context('onProjectionError', async () => {}
})
