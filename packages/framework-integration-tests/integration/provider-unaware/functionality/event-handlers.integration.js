"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const client_1 = require("@apollo/client");
const faker_1 = require("faker");
const chai_1 = require("chai");
const sleep_1 = require("../../helper/sleep");
const setup_1 = require("./setup");
describe('Event handlers', () => {
    let adminEmail;
    let authToken;
    let client;
    before(async () => {
        adminEmail = faker_1.internet.email();
        authToken = setup_1.applicationUnderTest.token.forUser(adminEmail, 'Admin');
        client = setup_1.applicationUnderTest.graphql.client(authToken);
    });
    context('move product', () => {
        it('should trigger a new ProductAvailabilityChanged event', async () => {
            const mockProductId = faker_1.random.uuid();
            const mockOrigin = 'provider';
            const mockDestination = faker_1.address.city();
            const mockQuantity = faker_1.random.number({ min: 1 });
            await createProductAndWaitForIt(client, mockProductId);
            await client.mutate({
                variables: {
                    productID: mockProductId,
                    origin: mockOrigin,
                    destination: mockDestination,
                    quantity: mockQuantity,
                },
                mutation: (0, client_1.gql) `
          mutation MoveStock($productID: String!, $origin: String!, $destination: String!, $quantity: Float!) {
            MoveStock(input: { productID: $productID, origin: $origin, destination: $destination, quantity: $quantity })
          }
        `,
            });
            const stockEvents = await (0, sleep_1.waitForIt)(() => setup_1.applicationUnderTest.query.events(`Stock-${mockProductId}-event`), (events) => {
                return (events === null || events === void 0 ? void 0 : events.length) === 1;
            });
            const productEvents = await (0, sleep_1.waitForIt)(() => setup_1.applicationUnderTest.query.events(`Product-${mockProductId}-event`), (events) => {
                return (events === null || events === void 0 ? void 0 : events.length) === 2;
            });
            const stockMovedEvent = stockEvents[0];
            const expectedStockMovedEvent = {
                // eslint-disable-next-line @typescript-eslint/camelcase
                entityTypeName_entityID_kind: `Stock-${mockProductId}-event`,
                version: 1,
                value: {
                    destination: mockDestination,
                    quantity: mockQuantity,
                    productID: mockProductId,
                    origin: mockOrigin,
                },
                kind: 'event',
                superKind: 'domain',
                entityTypeName: 'Stock',
                typeName: 'StockMoved',
                entityID: mockProductId,
                currentUser: {
                    claims: {
                        'booster:role': 'Admin',
                        email: adminEmail,
                        iat: stockMovedEvent.currentUser.claims.iat,
                        id: adminEmail,
                        iss: 'booster',
                        sub: adminEmail,
                    },
                    header: {
                        alg: 'RS256',
                        kid: 'booster',
                        typ: 'JWT',
                    },
                    username: adminEmail,
                    roles: ['Admin'],
                    id: adminEmail,
                },
            };
            (0, chai_1.expect)(stockMovedEvent).to.deep.contain(expectedStockMovedEvent);
            const productAvailabilityChangedEvent = productEvents[0];
            const expectedProductAvailabilityChangedEvent = {
                // eslint-disable-next-line @typescript-eslint/camelcase
                entityTypeName_entityID_kind: `Product-${mockProductId}-event`,
                version: 1,
                value: {
                    productID: mockProductId,
                    quantity: mockQuantity,
                },
                kind: 'event',
                superKind: 'domain',
                entityTypeName: 'Product',
                typeName: 'ProductAvailabilityChanged',
                entityID: mockProductId,
                currentUser: {
                    claims: {
                        'booster:role': 'Admin',
                        email: adminEmail,
                        iat: productAvailabilityChangedEvent.currentUser.claims.iat,
                        id: adminEmail,
                        iss: 'booster',
                        sub: adminEmail,
                    },
                    header: {
                        alg: 'RS256',
                        kid: 'booster',
                        typ: 'JWT',
                    },
                    username: adminEmail,
                    roles: ['Admin'],
                    id: adminEmail,
                },
            };
            (0, chai_1.expect)(productAvailabilityChangedEvent).to.deep.contain(expectedProductAvailabilityChangedEvent);
        });
    });
});
async function createProductAndWaitForIt(client, mockProductId) {
    await client.mutate({
        variables: {
            productID: mockProductId,
            sku: faker_1.random.alpha({ count: 10 }),
            displayName: faker_1.commerce.productName(),
            description: faker_1.lorem.paragraph(),
            priceInCents: faker_1.random.number({ min: 1 }),
            currency: faker_1.finance.currencyCode(),
        },
        mutation: (0, client_1.gql) `
      mutation CreateProduct(
        $productID: ID!
        $sku: String!
        $displayName: String!
        $description: String!
        $priceInCents: Float!
        $currency: String!
      ) {
        CreateProduct(
          input: {
            sku: $sku
            productID: $productID
            displayName: $displayName
            description: $description
            priceInCents: $priceInCents
            currency: $currency
          }
        )
      }
    `,
    });
    await (0, sleep_1.waitForIt)(() => setup_1.applicationUnderTest.query.events(`Product-${mockProductId}-event`), (events) => {
        return (events === null || events === void 0 ? void 0 : events.length) === 1;
    });
}
