"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const client_1 = require("@apollo/client");
const setup_1 = require("./setup");
const faker_1 = require("faker");
const expect_1 = require("./expect");
const constants_1 = require("../../../src/constants");
describe('Global error handler', async () => {
    let client;
    before(async () => {
        const adminEmail = faker_1.internet.email();
        const authToken = setup_1.applicationUnderTest.token.forUser(adminEmail, 'Admin');
        client = setup_1.applicationUnderTest.graphql.client(authToken);
    });
    context('CommandHandler', async () => {
        it('should update error object when handler fails', async () => {
            const expectedErrorMessage = `${constants_1.commandHandlerErrorCartMessage}-onCommandHandlerError-onError`;
            await (0, expect_1.expect)(client.mutate({
                variables: {
                    cartId: constants_1.commandHandlerErrorCartId,
                    productId: faker_1.random.uuid(),
                    quantity: faker_1.random.number({ min: 1 }),
                },
                mutation: (0, client_1.gql) `
            mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
              ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
            }
          `,
            })).to.be.eventually.rejectedWith(expectedErrorMessage);
        });
        it('should ignore error object when handler returns undefined', async () => {
            await (0, expect_1.expect)(client.mutate({
                variables: {
                    cartId: constants_1.commandHandlerErrorIgnoredCartId,
                    productId: faker_1.random.uuid(),
                    quantity: faker_1.random.number({ min: 1 }),
                },
                mutation: (0, client_1.gql) `
            mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
              ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
            }
          `,
            })).to.be.eventually.eql({ data: { ChangeCartItem: true } });
        });
        it('should update error object when onBefore fails', async () => {
            const expectedErrorMessage = `${constants_1.commandHandlerBeforeErrorCartMessage}-onBeforeCommandHandlerError-onError`;
            await (0, expect_1.expect)(client.mutate({
                variables: {
                    cartId: constants_1.commandHandlerBeforeErrorCartId,
                    productId: faker_1.random.uuid(),
                    quantity: faker_1.random.number({ min: 1 }),
                },
                mutation: (0, client_1.gql) `
            mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
              ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
            }
          `,
            })).to.be.eventually.rejectedWith(expectedErrorMessage);
        });
    });
    context('QueryHandler', async () => {
        it('should update error object when handler fails', async () => {
            const expectedErrorMessage = `${constants_1.queryHandlerErrorCartMessage}-onQueryHandlerError-onError`;
            await (0, expect_1.expect)(client.mutate({
                variables: {
                    cartId: constants_1.queryHandlerErrorCartId,
                },
                mutation: (0, client_1.gql) `
            query CartTotalQuantity($cartId: ID!) {
              CartTotalQuantity(input: { cartId: $cartId })
            }
          `,
            })).to.be.eventually.rejectedWith(expectedErrorMessage);
        });
    });
    // TODO dispatch doesn't returns an error but catch it and log it in the console
    // context('DispatchEventHandler', async () => {}
    // TODO reducer doesn't returns an error but catch it and log it in the console
    // context('onReducerError', async () => {}
    // TODO projection doesn't returns an error but catch it and log it in the console
    // context('onProjectionError', async () => {}
});
