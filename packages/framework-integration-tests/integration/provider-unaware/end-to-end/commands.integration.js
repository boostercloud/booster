"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const client_1 = require("@apollo/client");
const faker_1 = require("faker");
const expect_1 = require("../../helper/expect");
const setup_1 = require("./setup");
describe('Commands end-to-end tests', () => {
    context('with public commands', () => {
        let client;
        before(async () => {
            client = setup_1.applicationUnderTest.graphql.client();
        });
        it('accepts a command successfully', async () => {
            var _a;
            const response = await client.mutate({
                variables: {
                    cartId: faker_1.random.uuid(),
                    productId: faker_1.random.uuid(),
                    quantity: faker_1.random.number({ min: 1 }),
                },
                mutation: (0, client_1.gql) `
          mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
            ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
          }
        `,
            });
            (0, expect_1.expect)(response).not.to.be.null;
            (0, expect_1.expect)((_a = response === null || response === void 0 ? void 0 : response.data) === null || _a === void 0 ? void 0 : _a.ChangeCartItem).to.be.true;
        });
        it('accepts an empty command', async () => {
            var _a;
            const response = await client.mutate({
                variables: {},
                mutation: (0, client_1.gql) `
          mutation {
            EmptyCommand
          }
        `,
            });
            (0, expect_1.expect)(response).not.to.be.null;
            (0, expect_1.expect)((_a = response === null || response === void 0 ? void 0 : response.data) === null || _a === void 0 ? void 0 : _a.EmptyCommand).to.be.equal('Empty command executed');
        });
    });
    context('when the command requires a specific role', () => {
        it('rejects the command if the user does not have the required role', async () => {
            const authToken = setup_1.applicationUnderTest.token.forUser('admin@example.com', 'User');
            const client = setup_1.applicationUnderTest.graphql.client(authToken);
            const resultPromise = client.mutate({
                variables: {
                    sku: faker_1.random.uuid(),
                    displayName: faker_1.commerce.product(),
                    description: faker_1.commerce.productDescription(),
                    priceInCents: Math.floor(Math.random() * 100 + 1),
                    currency: faker_1.finance.currencyName(),
                },
                mutation: (0, client_1.gql) `
          mutation CreateProduct(
            $sku: String!
            $displayName: String!
            $description: String!
            $priceInCents: Float!
            $currency: String!
          ) {
            CreateProduct(
              input: {
                sku: $sku
                displayName: $displayName
                description: $description
                priceInCents: $priceInCents
                currency: $currency
              }
            )
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
                variables: {
                    sku: faker_1.random.uuid(),
                    displayName: faker_1.commerce.product(),
                    description: faker_1.commerce.productDescription(),
                    priceInCents: Math.floor(Math.random() * 100 + 1),
                    currency: faker_1.finance.currencyName(),
                },
                mutation: (0, client_1.gql) `
          mutation CreateProduct(
            $sku: String!
            $displayName: String!
            $description: String!
            $priceInCents: Float!
            $currency: String!
          ) {
            CreateProduct(
              input: {
                sku: $sku
                displayName: $displayName
                description: $description
                priceInCents: $priceInCents
                currency: $currency
              }
            )
          }
        `,
            });
            (0, expect_1.expect)(result).not.to.be.null;
            (0, expect_1.expect)((_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.CreateProduct).to.be.true;
        });
    });
    context('when the command requires a custom authorization policy', () => {
        it('rejects the command when the policy is not satisfied', async () => {
            const authToken = setup_1.applicationUnderTest.token.forUser('logger@example.com', 'User');
            const client = setup_1.applicationUnderTest.graphql.client(authToken);
            const resultPromise = client.mutate({
                variables: {
                    sku: faker_1.random.uuid(),
                    displayName: faker_1.commerce.product(),
                    description: faker_1.commerce.productDescription(),
                    priceInCents: Math.floor(Math.random() * 100 + 1),
                    currency: faker_1.finance.currencyName(),
                },
                mutation: (0, client_1.gql) `
          mutation {
            LogSomething
          }
        `,
            });
            await (0, expect_1.expect)(resultPromise).to.be.eventually.rejectedWith(/You are not allowed to log something/);
        });
        it('accepts the command when the policy is satisfied', async () => {
            var _a;
            const authToken = setup_1.applicationUnderTest.token.forUser('logger@example.com', undefined, {
                customClaims: { canLogSomething: true },
            });
            const client = setup_1.applicationUnderTest.graphql.client(authToken);
            const result = await client.mutate({
                variables: {
                    sku: faker_1.random.uuid(),
                    displayName: faker_1.commerce.product(),
                    description: faker_1.commerce.productDescription(),
                    priceInCents: Math.floor(Math.random() * 100 + 1),
                    currency: faker_1.finance.currencyName(),
                },
                mutation: (0, client_1.gql) `
          mutation {
            LogSomething
          }
        `,
            });
            (0, expect_1.expect)(result).not.to.be.null;
            (0, expect_1.expect)((_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.LogSomething).to.be.true;
        });
    });
});
