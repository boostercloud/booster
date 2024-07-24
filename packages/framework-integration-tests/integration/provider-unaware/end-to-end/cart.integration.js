"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const client_1 = require("@apollo/client");
const faker_1 = require("faker");
const chai_1 = require("chai");
const sleep_1 = require("../../helper/sleep");
const setup_1 = require("./setup");
const constants_1 = require("../../../src/constants");
const secs = 10;
describe('Cart end-to-end tests', () => {
    let client;
    before(async () => {
        client = setup_1.applicationUnderTest.graphql.client();
    });
    describe('Commands', () => {
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
            (0, chai_1.expect)(response).not.to.be.null;
            (0, chai_1.expect)((_a = response === null || response === void 0 ? void 0 : response.data) === null || _a === void 0 ? void 0 : _a.ChangeCartItem).to.be.true;
        });
        it('changes input before calling handle', async () => {
            var _a;
            const response = await client.mutate({
                variables: {
                    cartId: constants_1.beforeHookMutationID,
                    productId: faker_1.random.uuid(),
                    quantity: faker_1.random.number({ min: 1 }),
                },
                mutation: (0, client_1.gql) `
          mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
            ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
          }
        `,
            });
            (0, chai_1.expect)(response).not.to.be.null;
            (0, chai_1.expect)((_a = response === null || response === void 0 ? void 0 : response.data) === null || _a === void 0 ? void 0 : _a.ChangeCartItem).to.be.true;
            const queryResult = await (0, sleep_1.waitForIt)(() => {
                return client.query({
                    variables: {
                        cartId: constants_1.beforeHookMutationID + '-modified',
                    },
                    query: (0, client_1.gql) `
              query CartReadModel($cartId: ID!) {
                CartReadModel(id: $cartId) {
                  id
                  cartItems {
                    productId
                    quantity
                  }
                }
              }
            `,
                });
            }, (result) => { var _a; return ((_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.CartReadModel) != null; });
            const cartData = queryResult.data.CartReadModel;
            (0, chai_1.expect)(cartData.id).to.be.equal(constants_1.beforeHookMutationIDModified);
            (0, chai_1.expect)(cartData.cartItems[0].quantity).to.be.equal(constants_1.beforeHookQuantity);
        });
        it('throws an exception when before hook throws', async () => {
            try {
                await client.mutate({
                    variables: {
                        cartId: constants_1.throwExceptionId,
                        productId: faker_1.random.uuid(),
                        quantity: faker_1.random.number({ min: 1 }),
                    },
                    mutation: (0, client_1.gql) `
            mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
              ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
            }
          `,
                });
            }
            catch (e) {
                (0, chai_1.expect)(e.graphQLErrors[0].message).to.be.eq(`${constants_1.beforeHookException}-onError`);
                (0, chai_1.expect)(e.graphQLErrors[0].path).to.deep.eq(['ChangeCartItem']);
            }
        });
        describe('Query read models', () => {
            context('1 cart item', () => {
                const mockCartId = faker_1.random.uuid();
                const mockProductId = faker_1.random.uuid();
                const mockQuantity = faker_1.random.number({ min: 1 });
                beforeEach(async () => {
                    // provisioning a cart
                    await client.mutate({
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
                    });
                });
                it('should retrieve expected cart', async () => {
                    const queryResult = await (0, sleep_1.waitForIt)(() => {
                        return client.query({
                            variables: {
                                cartId: mockCartId,
                            },
                            query: (0, client_1.gql) `
                  query CartReadModel($cartId: ID!) {
                    CartReadModel(id: $cartId) {
                      id
                      cartItems {
                        productId
                        quantity
                      }
                    }
                  }
                `,
                        });
                    }, (result) => { var _a; return ((_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.CartReadModel) != null; });
                    const cartData = queryResult.data.CartReadModel;
                    (0, chai_1.expect)(cartData.id).to.be.equal(mockCartId);
                    (0, chai_1.expect)(cartData.cartItems).to.have.length(1);
                    (0, chai_1.expect)(cartData.cartItems[0]).to.deep.equal({
                        __typename: 'CartItem',
                        productId: mockProductId,
                        quantity: mockQuantity,
                    });
                });
            });
            context('several cart items', () => {
                let mockCartId;
                let mockCartItemsCount;
                const mockCartItems = [];
                beforeEach(async () => {
                    mockCartId = faker_1.random.uuid();
                    mockCartItemsCount = faker_1.random.number({ min: 2, max: 5 });
                    const changeCartPromises = [];
                    for (let i = 0; i < mockCartItemsCount; i++) {
                        const mockProductId = faker_1.random.uuid();
                        const mockQuantity = faker_1.random.number({ min: 1 });
                        mockCartItems.push({ productId: mockProductId, quantity: mockQuantity });
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
                });
                it('should retrieve expected cart', async () => {
                    const queryResult = await (0, sleep_1.waitForIt)(() => {
                        return client.query({
                            variables: {
                                cartId: mockCartId,
                            },
                            query: (0, client_1.gql) `
                  query CartReadModel($cartId: ID!) {
                    CartReadModel(id: $cartId) {
                      id
                      cartItems {
                        productId
                        quantity
                      }
                    }
                  }
                `,
                        });
                    }, (result) => {
                        var _a, _b, _c;
                        return ((_c = (_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.CartReadModel) === null || _b === void 0 ? void 0 : _b.cartItems) === null || _c === void 0 ? void 0 : _c.length) == mockCartItemsCount;
                    });
                    const cartData = queryResult.data.CartReadModel;
                    (0, chai_1.expect)(cartData.id).to.be.equal(mockCartId);
                    (0, chai_1.expect)(cartData.cartItems).to.have.length(mockCartItemsCount);
                    mockCartItems.forEach((mockCartItem) => {
                        const hasCartItem = cartData.cartItems.some((cartItem) => cartItem.productId === mockCartItem.productId && cartItem.quantity === mockCartItem.quantity);
                        (0, chai_1.expect)(hasCartItem).to.be.true;
                    });
                });
            });
        });
    });
    describe('Entities', () => {
        let userEmail;
        let authToken;
        before(async () => {
            userEmail = faker_1.internet.email();
            authToken = setup_1.applicationUnderTest.token.forUser(userEmail, 'UserWithEmail');
            client = setup_1.applicationUnderTest.graphql.client(authToken);
        });
        context('Reducers', () => {
            let mockSku;
            let mockDisplayName;
            let mockDescription;
            let mockPriceInCents;
            let mockCurrency;
            let mockProductDetails;
            let mockProductType;
            let productId;
            beforeEach(async () => {
                mockSku = faker_1.random.uuid();
                mockDisplayName = faker_1.commerce.productName();
                mockDescription = faker_1.lorem.paragraph();
                mockPriceInCents = faker_1.random.number({ min: 1 });
                mockCurrency = faker_1.finance.currencyCode();
                mockProductDetails = {
                    color: faker_1.commerce.color(),
                    size: faker_1.commerce.productAdjective(),
                };
                mockProductType = 'Furniture';
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
                }, (result) => {
                    var _a, _b;
                    return (_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.ProductReadModels) === null || _b === void 0 ? void 0 : _b.some((product) => product.sku === mockSku);
                });
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
                const adminEmail = faker_1.internet.email();
                const adminAuthToken = setup_1.applicationUnderTest.token.forUser(adminEmail, 'Admin');
                client = setup_1.applicationUnderTest.graphql.client(adminAuthToken);
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
                console.log(`Waiting ${secs} second${secs > 1 ? 's' : ''} for deletion to complete...`);
                await (0, sleep_1.sleep)(secs * 1000);
                client = setup_1.applicationUnderTest.graphql.client(authToken);
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
                  }
                }
              `,
                    });
                }, () => true);
                const productData = queryResult.data.ProductReadModel;
                (0, chai_1.expect)(productData).to.be.null;
            });
        });
    });
    describe('Read models', () => {
        describe('projecting two entities', () => {
            const mockCartId = faker_1.random.uuid();
            const mockPaymentId = faker_1.random.uuid();
            const mockProductId = faker_1.random.uuid();
            const mockQuantity = faker_1.random.number({ min: 1 });
            const mockConfirmationToken = faker_1.random.alphaNumeric(10);
            beforeEach(async () => {
                // provisioning a cart
                await client.mutate({
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
                });
            });
            it('should project changes for both entities', async () => {
                const queryResult = await (0, sleep_1.waitForIt)(() => {
                    return client.query({
                        variables: {
                            cartId: mockCartId,
                        },
                        query: (0, client_1.gql) `
                query CartReadModel($cartId: ID!) {
                  CartReadModel(id: $cartId) {
                    id
                    cartItems {
                      productId
                      quantity
                    }
                  }
                }
              `,
                    });
                }, (result) => { var _a; return ((_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.CartReadModel) != null; });
                const cartData = queryResult.data.CartReadModel;
                const expectedResult = {
                    __typename: 'CartReadModel',
                    id: mockCartId,
                    cartItems: [
                        {
                            __typename: 'CartItem',
                            productId: mockProductId,
                            quantity: mockQuantity,
                        },
                    ],
                };
                (0, chai_1.expect)(cartData).to.be.deep.equal(expectedResult);
                // Make payment
                await client.mutate({
                    variables: {
                        paymentId: mockPaymentId,
                        cartId: mockCartId,
                        confirmationToken: mockConfirmationToken,
                    },
                    mutation: (0, client_1.gql) `
            mutation ConfirmPayment($paymentId: ID!, $cartId: ID!, $confirmationToken: String!) {
              ConfirmPayment(input: { paymentId: $paymentId, cartId: $cartId, confirmationToken: $confirmationToken })
            }
          `,
                });
                // Retrieve updated read model
                const updatedQueryResult = await (0, sleep_1.waitForIt)(() => {
                    return client.query({
                        variables: {
                            cartId: mockCartId,
                        },
                        query: (0, client_1.gql) `
                query CartReadModel($cartId: ID!) {
                  CartReadModel(id: $cartId) {
                    id
                    cartItems {
                      productId
                      quantity
                    }
                    payment {
                      confirmationToken
                      id
                      cartId
                    }
                  }
                }
              `,
                    });
                }, (result) => { var _a, _b; return ((_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.CartReadModel) === null || _b === void 0 ? void 0 : _b.payment) != null; });
                const updatedCartData = updatedQueryResult.data.CartReadModel;
                const expectedUpdatedResult = {
                    __typename: 'CartReadModel',
                    id: mockCartId,
                    cartItems: [
                        {
                            __typename: 'CartItem',
                            productId: mockProductId,
                            quantity: mockQuantity,
                        },
                    ],
                    payment: {
                        __typename: 'Payment',
                        confirmationToken: mockConfirmationToken,
                        id: mockPaymentId,
                        cartId: mockCartId,
                    },
                };
                (0, chai_1.expect)(updatedCartData).to.be.deep.equal(expectedUpdatedResult);
            });
        });
    });
});
