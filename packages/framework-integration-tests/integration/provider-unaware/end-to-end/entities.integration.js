"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const client_1 = require("@apollo/client");
const faker_1 = require("faker");
const chai_1 = require("chai");
const sleep_1 = require("../../helper/sleep");
const setup_1 = require("./setup");
const constants_1 = require("../../../src/constants");
describe('Entities end-to-end tests', () => {
    let client;
    let userToken;
    before(async () => {
        const userEmail = faker_1.internet.email();
        userToken = setup_1.applicationUnderTest.token.forUser(userEmail, 'UserWithEmail');
        client = setup_1.applicationUnderTest.graphql.client(userToken);
    });
    context('Reducers', () => {
        let mockSku;
        let mockDisplayName;
        let mockDescription;
        let mockPriceInCents;
        let mockCurrency;
        let mockProductType;
        let mockProductDetails;
        let productId;
        beforeEach(async () => {
            mockSku = faker_1.random.uuid();
            mockDisplayName = faker_1.commerce.productName();
            mockDescription = faker_1.lorem.paragraph();
            mockPriceInCents = faker_1.random.number({ min: 1 });
            mockCurrency = faker_1.finance.currencyCode();
            mockProductType = 'Clothing';
            mockProductDetails = {
                color: faker_1.commerce.color(),
                material: faker_1.commerce.productMaterial(),
            };
            // Add one item
            await client.mutate({
                variables: {
                    sku: mockSku,
                    displayName: mockDisplayName,
                    description: mockDescription,
                    priceInCents: mockPriceInCents,
                    currency: mockCurrency,
                    productDetails: mockProductDetails,
                    productType: mockProductType,
                },
                mutation: (0, client_1.gql) `
          mutation CreateProduct(
            $sku: String!
            $displayName: String!
            $description: String!
            $priceInCents: Float!
            $currency: String!
            $productDetails: JSON
            $productType: JSON
          ) {
            CreateProduct(
              input: {
                sku: $sku
                displayName: $displayName
                description: $description
                priceInCents: $priceInCents
                currency: $currency
                productDetails: $productDetails
                productType: $productType
              }
            )
          }
        `,
            });
            // Check that new product is available in read model
            const products = await (0, sleep_1.waitForIt)(() => {
                return client.query({
                    query: (0, client_1.gql) `
              query {
                ProductReadModels {
                  id
                  sku
                  displayName
                  description
                  price {
                    cents
                    currency
                  }
                  availability
                  deleted
                  productDetails
                  productType
                }
              }
            `,
                });
            }, (result) => { var _a, _b; return (_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.ProductReadModels) === null || _b === void 0 ? void 0 : _b.some((product) => product.sku === mockSku); });
            const product = products.data.ProductReadModels.find((product) => product.sku === mockSku);
            productId = product.id;
            const expectedResult = {
                __typename: 'ProductReadModel',
                id: productId,
                sku: mockSku,
                displayName: mockDisplayName,
                description: mockDescription,
                price: {
                    __typename: 'Money',
                    cents: mockPriceInCents,
                    currency: mockCurrency,
                },
                availability: 0,
                deleted: false,
                productDetails: mockProductDetails,
                productType: mockProductType,
            };
            (0, chai_1.expect)(product).to.be.deep.equal(expectedResult);
        });
        it('should reduce the entity as expected', async () => {
            // TODO: Make retrieval of auth token cloud agnostic
            // provision admin user to delete a product
            const adminEmail = faker_1.internet.email();
            const adminToken = setup_1.applicationUnderTest.token.forUser(adminEmail, 'Admin');
            client = setup_1.applicationUnderTest.graphql.client(adminToken);
            // Delete a product given an id
            await client.mutate({
                variables: {
                    productId: productId,
                },
                mutation: (0, client_1.gql) `
          mutation DeleteProduct($productId: ID!) {
            DeleteProduct(input: { productId: $productId })
          }
        `,
            });
            client = setup_1.applicationUnderTest.graphql.client(userToken);
            // Retrieve updated entity
            const queryResult = await (0, sleep_1.waitForIt)(() => {
                return client.query({
                    variables: {
                        productId: productId,
                    },
                    query: (0, client_1.gql) `
              query ProductReadModel($productId: ID!) {
                ProductReadModel(id: $productId) {
                  id
                  sku
                  displayName
                  description
                  price {
                    cents
                    currency
                  }
                  availability
                  deleted
                  productDetails
                  productType
                }
              }
            `,
                });
            }, (result) => { var _a; return !((_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.ProductReadModel); });
            const productData = queryResult.data.ProductReadModel;
            (0, chai_1.expect)(productData).to.be.null;
        });
    });
    context('Data migration', () => {
        //TODO: AWS provider doesn't support entityIds Interface so these tests are skipped for AWS
        if (process.env.TESTED_PROVIDER === 'AWS') {
            console.log('****************** Warning **********************');
            console.log('AWS provider does not support entityIds Interface so these tests are skipped for AWS');
            console.log('*************************************************');
            return;
        }
        context('with different id and values', () => {
            const mockCartCount = 3;
            const mockCartItems = [];
            beforeEach(async () => {
                const changeCartPromises = [];
                for (let i = 0; i < mockCartCount; i++) {
                    const mockCartId = faker_1.random.uuid();
                    const mockProductId = faker_1.random.uuid();
                    const mockQuantity = constants_1.QUANTITY_TO_MIGRATE_DATA;
                    mockCartItems.push(mockCartId);
                    changeCartPromises.push(client.mutate({
                        variables: {
                            cartId: mockCartId,
                            productId: mockProductId,
                            quantity: mockQuantity,
                        },
                        mutation: (0, client_1.gql) `
                mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
                  ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
                }
              `,
                    }));
                }
                await Promise.all(changeCartPromises);
                await (0, sleep_1.waitForIt)(() => {
                    return client.query({
                        variables: {
                            filter: {
                                id: { in: [...mockCartItems] },
                            },
                        },
                        query: (0, client_1.gql) `
                query ListCartReadModels($filter: ListCartReadModelFilter) {
                  ListCartReadModels(filter: $filter) {
                    items {
                      id
                      cartItems {
                        productId
                        quantity
                      }
                    }
                  }
                }
              `,
                    });
                }, (result) => { var _a; return ((_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.ListCartReadModels.items.length) == mockCartCount; });
            });
            it('find migrated entities', async () => {
                await client.mutate({
                    mutation: (0, client_1.gql) `
            mutation MigrateCommand {
              MigrateCommand
            }
          `,
                });
                const queryResult = await (0, sleep_1.waitForIt)(() => {
                    return client.query({
                        variables: {
                            filter: {
                                id: { in: [...constants_1.NEW_CART_IDS] },
                            },
                        },
                        query: (0, client_1.gql) `
                query ListCartReadModels($filter: ListCartReadModelFilter) {
                  ListCartReadModels(filter: $filter) {
                    items {
                      id
                      cartItems {
                        productId
                        quantity
                      }
                    }
                  }
                }
              `,
                    });
                }, (result) => {
                    var _a, _b;
                    const items = (_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.ListCartReadModels) === null || _b === void 0 ? void 0 : _b.items;
                    const jsonItems = JSON.stringify(items);
                    if (!items || items.length !== mockCartCount) {
                        return `Waiting for ${mockCartCount} items. ${jsonItems} `;
                    }
                    if (!allItemsHasQuantity(items, constants_1.QUANTITY_AFTER_DATA_MIGRATION_V2)) {
                        return `Waiting for quantities to be equal to ${constants_1.QUANTITY_AFTER_DATA_MIGRATION_V2}. ${jsonItems}`;
                    }
                    return true;
                });
                const readModels = queryResult.data.ListCartReadModels;
                readModels.items.forEach((item) => (0, chai_1.expect)(item.cartItems[0].quantity, `Not matching quantity on cartId ${item.id} and productId: ${item.cartItems[0].productId}`).to.be.eq(constants_1.QUANTITY_AFTER_DATA_MIGRATION_V2));
                await (0, sleep_1.waitForIt)(() => {
                    return client.query({
                        variables: {
                            filter: {},
                        },
                        query: (0, client_1.gql) `
                query ListDataMigrationsReadModels($filter: ListDataMigrationsReadModelFilter) {
                  ListDataMigrationsReadModels(filter: $filter) {
                    items {
                      id
                      status
                      lastUpdated
                    }
                    count
                    cursor
                  }
                }
              `,
                    });
                }, (result) => {
                    var _a, _b;
                    const count = (_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.ListDataMigrationsReadModels) === null || _b === void 0 ? void 0 : _b.count;
                    if (count < 2) {
                        return `Waiting for ${count} migrations. Done ${count} migrations`;
                    }
                    return true;
                });
            });
        });
    });
    function allItemsHasQuantity(items, quantity) {
        return items.every((item) => item.cartItems[0].quantity === quantity);
    }
});
