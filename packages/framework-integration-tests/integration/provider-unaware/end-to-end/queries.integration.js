"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const client_1 = require("@apollo/client");
const faker_1 = require("faker");
const expect_1 = require("../../helper/expect");
const setup_1 = require("./setup");
const sleep_1 = require("../../helper/sleep");
const framework_types_1 = require("@boostercloud/framework-types");
const constants_1 = require("../../../src/constants");
describe('Queries end-to-end tests', () => {
    context('with public queries', () => {
        let client;
        before(async () => {
            client = setup_1.applicationUnderTest.graphql.client();
        });
        it('accepts a query successfully', async () => {
            var _a;
            const cartId = faker_1.random.uuid();
            const quantity = faker_1.random.number({ min: 1 });
            await client.mutate({
                variables: {
                    cartId: cartId,
                    productId: faker_1.random.uuid(),
                    quantity: quantity,
                },
                mutation: (0, client_1.gql) `
          mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
            ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
          }
        `,
            });
            const response = await (0, sleep_1.waitForIt)(() => client.query({
                variables: {
                    cartId: cartId,
                },
                query: (0, client_1.gql) `
              query CartTotalQuantity($cartId: ID!) {
                CartTotalQuantity(input: { cartId: $cartId })
              }
            `,
            }), (result) => { var _a; return ((_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.CartTotalQuantity) != 0; });
            (0, expect_1.expect)(response).not.to.be.null;
            (0, expect_1.expect)((_a = response === null || response === void 0 ? void 0 : response.data) === null || _a === void 0 ? void 0 : _a.CartTotalQuantity).to.be.eq(quantity);
        });
        it('accepts a query with an object as result', async () => {
            var _a, _b;
            const cartIds = [];
            const countries = ['spain', undefined, 'india', 'spain'];
            // create 4 carts
            for (let i = 0; i < 4; i++) {
                cartIds.push(framework_types_1.UUID.generate());
                await client.mutate({
                    variables: {
                        cartId: cartIds[i],
                        productId: faker_1.random.uuid(),
                        quantity: 10,
                    },
                    mutation: (0, client_1.gql) `
            mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
              ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
            }
          `,
                });
                if (countries[i]) {
                    await client.mutate({
                        variables: {
                            cartId: cartIds[i],
                            address: {
                                firstName: 'firstName',
                                lastName: 'lastName',
                                country: countries[i],
                                state: 'state',
                                postalCode: '11111',
                                address: 'address',
                            },
                            quantity: 10,
                        },
                        mutation: (0, client_1.gql) `
              mutation UpdateShippingAddress($cartId: ID!, $address: AddressInput!) {
                UpdateShippingAddress(input: { cartId: $cartId, address: $address })
              }
            `,
                    });
                }
            }
            const response = await (0, sleep_1.waitForIt)(() => client.query({
                variables: {},
                query: (0, client_1.gql) `
              query CartsByCountry {
                CartsByCountry
              }
            `,
            }), (result) => { var _a, _b, _c, _d; return ((_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.CartsByCountry) === null || _b === void 0 ? void 0 : _b.length) !== 0 && ((_d = (_c = result === null || result === void 0 ? void 0 : result.data) === null || _c === void 0 ? void 0 : _c.CartsByCountry['spain']) === null || _d === void 0 ? void 0 : _d.length) === 2; });
            (0, expect_1.expect)(response).not.to.be.null;
            (0, expect_1.expect)((_a = response === null || response === void 0 ? void 0 : response.data) === null || _a === void 0 ? void 0 : _a.CartsByCountry['spain'].length).to.be.eq(2);
            (0, expect_1.expect)((_b = response === null || response === void 0 ? void 0 : response.data) === null || _b === void 0 ? void 0 : _b.CartsByCountry['india'].length).to.be.eq(1);
        });
        it('before hook multiply the value by beforeHookQueryMultiply', async () => {
            var _a;
            const cartId = constants_1.beforeHookQueryID;
            const quantity = faker_1.random.number({ min: 1 });
            await client.mutate({
                variables: {
                    cartId: cartId,
                    productId: faker_1.random.uuid(),
                    quantity: quantity,
                },
                mutation: (0, client_1.gql) `
          mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
            ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
          }
        `,
            });
            const response = await (0, sleep_1.waitForIt)(() => client.query({
                variables: {
                    cartId: cartId,
                },
                query: (0, client_1.gql) `
              query CartTotalQuantity($cartId: ID!) {
                CartTotalQuantity(input: { cartId: $cartId })
              }
            `,
            }), (result) => { var _a; return ((_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.CartTotalQuantity) != 0; });
            (0, expect_1.expect)(response).not.to.be.null;
            (0, expect_1.expect)((_a = response === null || response === void 0 ? void 0 : response.data) === null || _a === void 0 ? void 0 : _a.CartTotalQuantity).to.be.eq(constants_1.beforeHookQueryMultiply * quantity);
        });
    });
    context('when the query requires a specific role', () => {
        it('rejects the command if the user does not have the required role', async () => {
            const authToken = setup_1.applicationUnderTest.token.forUser('admin@example.com', 'User');
            const client = setup_1.applicationUnderTest.graphql.client(authToken);
            const resultPromise = client.mutate({
                variables: {},
                mutation: (0, client_1.gql) `
          query CartWithRole {
            CartWithRole
          }
        `,
            });
            await (0, expect_1.expect)(resultPromise).to.be.eventually.rejectedWith(/Access denied for this resource/);
        });
        it('accepts the command if the user has the required role', async () => {
            var _a;
            const authToken = setup_1.applicationUnderTest.token.forUser('admin@example.com', 'Admin');
            const client = setup_1.applicationUnderTest.graphql.client(authToken);
            const result = await client.mutate({
                variables: {},
                mutation: (0, client_1.gql) `
          query CartWithRole {
            CartWithRole
          }
        `,
            });
            (0, expect_1.expect)(result).not.to.be.null;
            (0, expect_1.expect)((_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.CartWithRole).to.be.true;
        });
    });
});
