"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/ban-ts-comment */
const client_1 = require("@apollo/client");
const faker_1 = require("faker");
const expect_1 = require("../../helper/expect");
const sleep_1 = require("../../helper/sleep");
const setup_1 = require("./setup");
const constants_1 = require("../../../src/constants");
describe('Read models end-to-end tests', () => {
    let client;
    before(async () => {
        client = setup_1.applicationUnderTest.graphql.client();
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
                (0, expect_1.expect)(cartData.id).to.be.equal(mockCartId);
                (0, expect_1.expect)(cartData.cartItems).to.have.length(1);
                (0, expect_1.expect)(cartData.cartItems[0]).to.deep.equal({
                    __typename: 'CartItem',
                    productId: mockProductId,
                    quantity: mockQuantity,
                });
            });
            it('should apply modified filter by before hooks', async () => {
                // We create a cart with id 'before-fn-test-modified', but we query for
                // 'before-fn-test', which will then change the filter after two "before" functions
                // to return the original cart (id 'before-fn-test-modified')
                const variables = {
                    cartId: 'before-fn-test-modified',
                    productId: constants_1.beforeHookProductId,
                    quantity: 1,
                };
                await client.mutate({
                    variables,
                    mutation: (0, client_1.gql) `
            mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
              ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
            }
          `,
                });
                const queryResult = await (0, sleep_1.waitForIt)(() => {
                    return client.query({
                        variables: {
                            cartId: 'before-fn-test',
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
                (0, expect_1.expect)(cartData.id).to.be.equal(variables.cartId);
            });
            it('should return exceptions thrown by before functions', async () => {
                try {
                    await (0, sleep_1.waitForIt)(() => {
                        return client.query({
                            variables: {
                                cartId: constants_1.throwExceptionId,
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
                    }, (_) => true);
                }
                catch (e) {
                    (0, expect_1.expect)(e.graphQLErrors[0].message).to.be.eq(constants_1.beforeHookException);
                    (0, expect_1.expect)(e.graphQLErrors[0].path).to.deep.eq(['CartReadModel']);
                }
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
                }, (result) => { var _a, _b, _c; return ((_c = (_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.CartReadModel) === null || _b === void 0 ? void 0 : _b.cartItems) === null || _c === void 0 ? void 0 : _c.length) == mockCartItemsCount; });
                const cartData = queryResult.data.CartReadModel;
                (0, expect_1.expect)(cartData.id).to.be.equal(mockCartId);
                (0, expect_1.expect)(cartData.cartItems).to.have.length(mockCartItemsCount);
                mockCartItems.forEach((mockCartItem) => {
                    const hasCartItem = cartData.cartItems.some((cartItem) => cartItem.productId === mockCartItem.productId && cartItem.quantity === mockCartItem.quantity);
                    (0, expect_1.expect)(hasCartItem).to.be.true;
                });
            });
        });
        context('query lists of carts', () => {
            let mockCartId;
            const mockCartItems = [];
            let mockProductId;
            let mockQuantity;
            const mockConfirmationToken = faker_1.random.alphaNumeric(10);
            let mockAddress;
            beforeEach(async () => {
                mockCartId = faker_1.random.uuid();
                mockProductId = faker_1.random.uuid();
                mockQuantity = 2;
                mockCartItems.push({ productId: mockProductId, quantity: mockQuantity });
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
                await (0, sleep_1.waitForIt)(() => {
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
                }, (result) => { var _a, _b; return ((_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.CartReadModel) === null || _b === void 0 ? void 0 : _b.id) === mockCartId; });
                mockAddress = {
                    firstName: faker_1.random.word(),
                    lastName: faker_1.random.word(),
                    country: faker_1.random.word(),
                    state: faker_1.random.word(),
                    postalCode: faker_1.random.word(),
                    address: faker_1.random.word(),
                };
                await client.mutate({
                    variables: {
                        cartId: mockCartId,
                        address: mockAddress,
                    },
                    mutation: (0, client_1.gql) `
            mutation UpdateShippingAddress($cartId: ID!, $address: AddressInput!) {
              UpdateShippingAddress(input: { cartId: $cartId, address: $address })
            }
          `,
                });
                const filter = {
                    shippingAddress: {
                        firstName: { eq: mockAddress.firstName },
                    },
                };
                await (0, sleep_1.waitForIt)(() => {
                    return client.query({
                        variables: {
                            filter: filter,
                        },
                        query: (0, client_1.gql) `
                query CartReadModels($filter: CartReadModelFilter) {
                  CartReadModels(filter: $filter) {
                    id
                    shippingAddress {
                      firstName
                    }
                  }
                }
              `,
                    });
                }, (result) => {
                    var _a, _b;
                    const carts = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.CartReadModels;
                    return (carts === null || carts === void 0 ? void 0 : carts.length) >= 1 && ((_b = carts[0].shippingAddress) === null || _b === void 0 ? void 0 : _b.firstName) === mockAddress.firstName;
                });
            });
            it('should retrieve a list of carts using deprecated methods', async () => {
                const queryResult = await (0, sleep_1.waitForIt)(() => {
                    return client.query({
                        query: (0, client_1.gql) `
                query CartReadModels {
                  CartReadModels {
                    id
                  }
                }
              `,
                    });
                }, (result) => { var _a, _b; return ((_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.CartReadModels) === null || _b === void 0 ? void 0 : _b.length) >= 1; });
                const cartData = queryResult.data.CartReadModels;
                (0, expect_1.expect)(cartData).to.be.an('array');
                (0, expect_1.expect)(cartData.length).to.be.gte(1);
            });
            it('should retrieve a specific cart using filters using deprecated methods', async () => {
                const filter = { id: { eq: mockCartId } };
                const queryResult = await (0, sleep_1.waitForIt)(() => {
                    return client.query({
                        variables: {
                            filter: filter,
                        },
                        query: (0, client_1.gql) `
                query CartReadModels($filter: CartReadModelFilter) {
                  CartReadModels(filter: $filter) {
                    id
                  }
                }
              `,
                    });
                }, (result) => { var _a, _b; return ((_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.CartReadModels) === null || _b === void 0 ? void 0 : _b.length) >= 1; });
                const cartData = queryResult.data.CartReadModels;
                (0, expect_1.expect)(cartData).to.be.an('array');
                (0, expect_1.expect)(cartData.length).to.equal(1);
                (0, expect_1.expect)(cartData[0].id).to.equal(mockCartId);
            });
            it('should retrieve a list of carts using nested filters', async () => {
                const filter = { shippingAddress: { firstName: { eq: mockAddress.firstName } } };
                const queryResult = await (0, sleep_1.waitForIt)(() => {
                    return client.query({
                        variables: {
                            filter: filter,
                        },
                        query: (0, client_1.gql) `
                query CartReadModels($filter: CartReadModelFilter) {
                  CartReadModels(filter: $filter) {
                    id
                  }
                }
              `,
                    });
                }, (result) => { var _a, _b; return ((_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.CartReadModels) === null || _b === void 0 ? void 0 : _b.length) >= 1; });
                const cartData = queryResult.data.CartReadModels;
                (0, expect_1.expect)(cartData).to.be.an('array');
                (0, expect_1.expect)(cartData.length).to.equal(1);
                (0, expect_1.expect)(cartData[0].id).to.equal(mockCartId);
            });
            it('should retrieve a list of carts using  complex filters and deprecated methods', async () => {
                const filter = { cartItems: { includes: { productId: mockProductId, quantity: 2 } } };
                const queryResult = await (0, sleep_1.waitForIt)(() => {
                    return client.query({
                        variables: {
                            filter: filter,
                        },
                        query: (0, client_1.gql) `
                query CartReadModels($filter: CartReadModelFilter) {
                  CartReadModels(filter: $filter) {
                    id
                  }
                }
              `,
                    });
                }, (result) => { var _a, _b; return ((_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.CartReadModels) === null || _b === void 0 ? void 0 : _b.length) >= 1; });
                const cartData = queryResult.data.CartReadModels;
                (0, expect_1.expect)(cartData).to.be.an('array');
                (0, expect_1.expect)(cartData.length).to.equal(1);
                (0, expect_1.expect)(cartData[0].id).to.equal(mockCartId);
            });
            it('should retrieve a list of carts when filter by isDefined true', async () => {
                const filter = {
                    and: [
                        {
                            id: { eq: mockCartId },
                        },
                        {
                            shippingAddress: {
                                firstName: {
                                    isDefined: true,
                                },
                            },
                        },
                    ],
                };
                const queryResult = await (0, sleep_1.waitForIt)(() => {
                    return client.query({
                        variables: {
                            filter: filter,
                        },
                        query: (0, client_1.gql) `
                query ListCartReadModels($filter: ListCartReadModelFilter) {
                  ListCartReadModels(filter: $filter) {
                    items {
                      id
                      shippingAddress {
                        firstName
                      }
                    }
                  }
                }
              `,
                    });
                }, (result) => {
                    var _a, _b, _c;
                    const carts = (_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.ListCartReadModels) === null || _b === void 0 ? void 0 : _b.items;
                    return (carts === null || carts === void 0 ? void 0 : carts.length) >= 1 && ((_c = carts[0].shippingAddress) === null || _c === void 0 ? void 0 : _c.firstName) === mockAddress.firstName;
                });
                const cartData = queryResult.data.ListCartReadModels.items;
                (0, expect_1.expect)(cartData).to.be.an('array');
                (0, expect_1.expect)(cartData.length).to.equal(1);
                (0, expect_1.expect)(cartData[0].id).to.equal(mockCartId);
                (0, expect_1.expect)(cartData[0].shippingAddress.firstName).to.be.eq(mockAddress.firstName);
            });
            it('should retrieve a list of carts when filter by isDefined false', async () => {
                const filter = {
                    and: [
                        {
                            id: { eq: mockCartId },
                        },
                        {
                            payment: {
                                id: {
                                    isDefined: false,
                                },
                            },
                        },
                    ],
                };
                const queryResult = await (0, sleep_1.waitForIt)(() => {
                    return client.query({
                        variables: {
                            filter: filter,
                        },
                        query: (0, client_1.gql) `
                query ListCartReadModels($filter: ListCartReadModelFilter) {
                  ListCartReadModels(filter: $filter) {
                    items {
                      id
                      shippingAddress {
                        firstName
                      }
                    }
                  }
                }
              `,
                    });
                }, (result) => {
                    var _a, _b, _c;
                    const carts = (_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.ListCartReadModels) === null || _b === void 0 ? void 0 : _b.items;
                    return (carts === null || carts === void 0 ? void 0 : carts.length) >= 1 && ((_c = carts[0].shippingAddress) === null || _c === void 0 ? void 0 : _c.firstName) === mockAddress.firstName;
                });
                const cartData = queryResult.data.ListCartReadModels.items;
                (0, expect_1.expect)(cartData).to.be.an('array');
                (0, expect_1.expect)(cartData.length).to.equal(1);
                (0, expect_1.expect)(cartData[0].id).to.equal(mockCartId);
                (0, expect_1.expect)(cartData[0].shippingAddress.firstName).to.be.eq(mockAddress.firstName);
            });
            it('should retrieve a list of carts when filter by isDefined true with AND', async () => {
                const filter = {
                    and: [
                        {
                            id: { eq: mockCartId },
                        },
                        {
                            and: [
                                {
                                    shippingAddress: {
                                        firstName: {
                                            isDefined: true,
                                        },
                                    },
                                },
                                {
                                    shippingAddress: {
                                        firstName: { eq: mockAddress.firstName },
                                    },
                                },
                            ],
                        },
                    ],
                };
                const queryResult = await (0, sleep_1.waitForIt)(() => {
                    return client.query({
                        variables: {
                            filter: filter,
                        },
                        query: (0, client_1.gql) `
                query ListCartReadModels($filter: ListCartReadModelFilter) {
                  ListCartReadModels(filter: $filter) {
                    items {
                      id
                      shippingAddress {
                        firstName
                      }
                    }
                  }
                }
              `,
                    });
                }, (result) => {
                    var _a, _b, _c;
                    const carts = (_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.ListCartReadModels) === null || _b === void 0 ? void 0 : _b.items;
                    return (carts === null || carts === void 0 ? void 0 : carts.length) >= 1 && ((_c = carts[0].shippingAddress) === null || _c === void 0 ? void 0 : _c.firstName) === mockAddress.firstName;
                });
                const cartData = queryResult.data.ListCartReadModels.items;
                (0, expect_1.expect)(cartData).to.be.an('array');
                (0, expect_1.expect)(cartData.length).to.equal(1);
                (0, expect_1.expect)(cartData[0].id).to.equal(mockCartId);
                (0, expect_1.expect)(cartData[0].shippingAddress.firstName).to.be.eq(mockAddress.firstName);
            });
            it('should retrieve a list of carts when filter by isDefined true with OR', async () => {
                const filter = {
                    and: [
                        {
                            id: { eq: mockCartId },
                        },
                        {
                            or: [
                                {
                                    shippingAddress: {
                                        lastName: {
                                            isDefined: false,
                                        },
                                    },
                                },
                                {
                                    shippingAddress: {
                                        firstName: { eq: mockAddress.firstName },
                                    },
                                },
                            ],
                        },
                    ],
                };
                const queryResult = await (0, sleep_1.waitForIt)(() => {
                    return client.query({
                        variables: {
                            filter: filter,
                        },
                        query: (0, client_1.gql) `
                query ListCartReadModels($filter: ListCartReadModelFilter) {
                  ListCartReadModels(filter: $filter) {
                    items {
                      id
                      shippingAddress {
                        firstName
                      }
                    }
                  }
                }
              `,
                    });
                }, (result) => {
                    var _a, _b, _c;
                    const carts = (_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.ListCartReadModels) === null || _b === void 0 ? void 0 : _b.items;
                    return (carts === null || carts === void 0 ? void 0 : carts.length) >= 1 && ((_c = carts[0].shippingAddress) === null || _c === void 0 ? void 0 : _c.firstName) === mockAddress.firstName;
                });
                const cartData = queryResult.data.ListCartReadModels.items;
                (0, expect_1.expect)(cartData).to.be.an('array');
                (0, expect_1.expect)(cartData.length).to.equal(1);
                (0, expect_1.expect)(cartData[0].id).to.equal(mockCartId);
                (0, expect_1.expect)(cartData[0].shippingAddress.firstName).to.be.eq(mockAddress.firstName);
            });
            it('should retrieve a list of carts when filter by isDefined for Objects', async () => {
                const filter = {
                    and: [
                        {
                            id: { eq: mockCartId },
                        },
                        {
                            payment: {
                                isDefined: false,
                            },
                        },
                    ],
                };
                const queryResult = await (0, sleep_1.waitForIt)(() => {
                    return client.query({
                        variables: {
                            filter: filter,
                        },
                        query: (0, client_1.gql) `
                query ListCartReadModels($filter: ListCartReadModelFilter) {
                  ListCartReadModels(filter: $filter) {
                    items {
                      id
                      shippingAddress {
                        firstName
                      }
                    }
                  }
                }
              `,
                    });
                }, (result) => {
                    var _a, _b, _c;
                    const carts = (_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.ListCartReadModels) === null || _b === void 0 ? void 0 : _b.items;
                    return (carts === null || carts === void 0 ? void 0 : carts.length) >= 1 && ((_c = carts[0].shippingAddress) === null || _c === void 0 ? void 0 : _c.firstName) === mockAddress.firstName;
                });
                const cartData = queryResult.data.ListCartReadModels.items;
                (0, expect_1.expect)(cartData).to.be.an('array');
                (0, expect_1.expect)(cartData.length).to.equal(1);
                (0, expect_1.expect)(cartData[0].id).to.equal(mockCartId);
                (0, expect_1.expect)(cartData[0].shippingAddress.firstName).to.be.eq(mockAddress.firstName);
            });
            it('should retrieve a list of carts when filter by isDefined with complex queries', async () => {
                const mockPaymentId = faker_1.random.uuid();
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
                const filter = {
                    and: [
                        {
                            id: { eq: mockCartId },
                        },
                        {
                            shippingAddress: {
                                firstName: {
                                    eq: mockAddress.firstName,
                                },
                            },
                        },
                        {
                            or: [
                                {
                                    cartItems: {
                                        isDefined: false,
                                    },
                                },
                                {
                                    cartItems: {
                                        includes: { productId: mockProductId, quantity: 2 },
                                    },
                                },
                            ],
                        },
                        {
                            payment: {
                                confirmationToken: { eq: mockConfirmationToken },
                            },
                        },
                        {
                            payment: {
                                id: { ne: null },
                            },
                        },
                    ],
                };
                const queryResult = await (0, sleep_1.waitForIt)(() => {
                    return client.query({
                        variables: {
                            filter: filter,
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
                      shippingAddress {
                        firstName
                      }
                      payment {
                        id
                        confirmationToken
                      }
                    }
                  }
                }
              `,
                    });
                }, (result) => {
                    var _a, _b, _c, _d, _e, _f;
                    return ((_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.ListCartReadModels) === null || _b === void 0 ? void 0 : _b.items.length) >= 1 &&
                        ((_f = (_e = (_d = (_c = result === null || result === void 0 ? void 0 : result.data) === null || _c === void 0 ? void 0 : _c.ListCartReadModels) === null || _d === void 0 ? void 0 : _d.items[0]) === null || _e === void 0 ? void 0 : _e.payment) === null || _f === void 0 ? void 0 : _f.id) !== undefined;
                });
                const cartData = queryResult.data.ListCartReadModels.items;
                (0, expect_1.expect)(cartData).to.be.an('array');
                (0, expect_1.expect)(cartData.length).to.equal(1);
                (0, expect_1.expect)(cartData[0].id).to.equal(mockCartId);
                (0, expect_1.expect)(cartData[0].shippingAddress.firstName).to.be.eq(mockAddress.firstName);
                (0, expect_1.expect)(cartData[0].cartItems[0].productId).to.be.eq(mockProductId);
                (0, expect_1.expect)(cartData[0].payment.id).to.be.eq(mockPaymentId);
            });
            it('should retrieve a list of carts when filter by contains with UUID fields', async () => {
                const partialMockCartId = mockCartId.slice(1, -1);
                const filter = {
                    and: [
                        {
                            id: { eq: mockCartId },
                        },
                        {
                            id: {
                                contains: partialMockCartId,
                            },
                        },
                    ],
                };
                const queryResult = await (0, sleep_1.waitForIt)(() => {
                    return client.query({
                        variables: {
                            filter: filter,
                        },
                        query: (0, client_1.gql) `
                query ListCartReadModels($filter: ListCartReadModelFilter) {
                  ListCartReadModels(filter: $filter) {
                    items {
                      id
                      shippingAddress {
                        firstName
                      }
                    }
                  }
                }
              `,
                    });
                }, (result) => {
                    var _a, _b;
                    const carts = (_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.ListCartReadModels) === null || _b === void 0 ? void 0 : _b.items;
                    return (carts === null || carts === void 0 ? void 0 : carts.length) >= 1 && carts[0].id === mockCartId;
                });
                const cartData = queryResult.data.ListCartReadModels.items;
                (0, expect_1.expect)(cartData).to.be.an('array');
                (0, expect_1.expect)(cartData.length).to.equal(1);
                (0, expect_1.expect)(cartData[0].id).to.equal(mockCartId);
            });
            it('should retrieve a list of carts when filter by regex', async () => {
                if (process.env.TESTED_PROVIDER !== 'AZURE' && process.env.TESTED_PROVIDER !== 'LOCAL') {
                    console.log('****************** Warning **********************');
                    console.log('Only Azure and Local provider implement the regex filter. Skipping');
                    console.log('*************************************************');
                    return;
                }
                const filter = {
                    and: [
                        {
                            id: { eq: mockCartId },
                        },
                        {
                            shippingAddress: {
                                firstName: {
                                    regex: `^${mockAddress.firstName.at(0)}.*`,
                                },
                            },
                        },
                    ],
                };
                const queryResult = await (0, sleep_1.waitForIt)(() => {
                    return client.query({
                        variables: {
                            filter: filter,
                        },
                        query: (0, client_1.gql) `
                query ListCartReadModels($filter: ListCartReadModelFilter) {
                  ListCartReadModels(filter: $filter) {
                    items {
                      id
                      shippingAddress {
                        firstName
                      }
                    }
                  }
                }
              `,
                    });
                }, (result) => {
                    var _a, _b, _c;
                    const carts = (_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.ListCartReadModels) === null || _b === void 0 ? void 0 : _b.items;
                    return (carts === null || carts === void 0 ? void 0 : carts.length) >= 1 && ((_c = carts[0].shippingAddress) === null || _c === void 0 ? void 0 : _c.firstName) === mockAddress.firstName;
                });
                const cartData = queryResult.data.ListCartReadModels.items;
                (0, expect_1.expect)(cartData).to.be.an('array');
                (0, expect_1.expect)(cartData.length).to.equal(1);
                (0, expect_1.expect)(cartData[0].id).to.equal(mockCartId);
                (0, expect_1.expect)(cartData[0].shippingAddress.firstName).to.be.eq(mockAddress.firstName);
            });
            it('should retrieve a list of carts when filter by iRegex', async () => {
                if (process.env.TESTED_PROVIDER !== 'AZURE' && process.env.TESTED_PROVIDER !== 'LOCAL') {
                    console.log('****************** Warning **********************');
                    console.log('Only Azure and Local provider implement the iRegex filter. Skipping');
                    console.log('*************************************************');
                    return;
                }
                const firstLetter = mockAddress.firstName.at(0);
                const inverseFirstLetter = firstLetter === (firstLetter === null || firstLetter === void 0 ? void 0 : firstLetter.toUpperCase()) ? firstLetter === null || firstLetter === void 0 ? void 0 : firstLetter.toLowerCase() : firstLetter === null || firstLetter === void 0 ? void 0 : firstLetter.toUpperCase();
                const filter = {
                    and: [
                        {
                            id: { eq: mockCartId },
                        },
                        {
                            shippingAddress: {
                                firstName: {
                                    iRegex: `^${inverseFirstLetter}.*`,
                                },
                            },
                        },
                    ],
                };
                const queryResult = await (0, sleep_1.waitForIt)(() => {
                    return client.query({
                        variables: {
                            filter: filter,
                        },
                        query: (0, client_1.gql) `
                query ListCartReadModels($filter: ListCartReadModelFilter) {
                  ListCartReadModels(filter: $filter) {
                    items {
                      id
                      shippingAddress {
                        firstName
                      }
                    }
                  }
                }
              `,
                    });
                }, (result) => {
                    var _a, _b, _c;
                    const carts = (_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.ListCartReadModels) === null || _b === void 0 ? void 0 : _b.items;
                    return (carts === null || carts === void 0 ? void 0 : carts.length) >= 1 && ((_c = carts[0].shippingAddress) === null || _c === void 0 ? void 0 : _c.firstName) === mockAddress.firstName;
                });
                const cartData = queryResult.data.ListCartReadModels.items;
                (0, expect_1.expect)(cartData).to.be.an('array');
                (0, expect_1.expect)(cartData.length).to.equal(1);
                (0, expect_1.expect)(cartData[0].id).to.equal(mockCartId);
                (0, expect_1.expect)(cartData[0].shippingAddress.firstName).to.be.eq(mockAddress.firstName);
            });
            it('should retrieve a list of carts when filter by null', async () => {
                const mockPaymentId = faker_1.random.uuid();
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
                const filter = {
                    and: [
                        {
                            id: { eq: mockCartId },
                        },
                        {
                            payment: {
                                confirmationToken: { ne: null },
                            },
                        },
                        {
                            payment: {
                                id: { ne: null },
                            },
                        },
                    ],
                };
                const queryResult = await (0, sleep_1.waitForIt)(() => {
                    return client.query({
                        variables: {
                            filter: filter,
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
                      shippingAddress {
                        firstName
                      }
                      payment {
                        id
                        confirmationToken
                      }
                    }
                  }
                }
              `,
                    });
                }, (result) => {
                    var _a, _b, _c, _d, _e, _f;
                    return ((_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.ListCartReadModels) === null || _b === void 0 ? void 0 : _b.items.length) >= 1 &&
                        ((_f = (_e = (_d = (_c = result === null || result === void 0 ? void 0 : result.data) === null || _c === void 0 ? void 0 : _c.ListCartReadModels) === null || _d === void 0 ? void 0 : _d.items[0]) === null || _e === void 0 ? void 0 : _e.payment) === null || _f === void 0 ? void 0 : _f.id) !== undefined;
                });
                const cartData = queryResult.data.ListCartReadModels.items;
                (0, expect_1.expect)(cartData).to.be.an('array');
                (0, expect_1.expect)(cartData.length).to.equal(1);
                (0, expect_1.expect)(cartData[0].id).to.equal(mockCartId);
                (0, expect_1.expect)(cartData[0].shippingAddress.firstName).to.be.eq(mockAddress.firstName);
                (0, expect_1.expect)(cartData[0].cartItems[0].productId).to.be.eq(mockProductId);
                (0, expect_1.expect)(cartData[0].payment.id).to.be.eq(mockPaymentId);
            });
            it('should retrieve a list of carts using paginated read model', async () => {
                const queryResult = await (0, sleep_1.waitForIt)(() => {
                    return client.query({
                        query: (0, client_1.gql) `
                query ListCartReadModels {
                  ListCartReadModels {
                    items {
                      id
                    }
                  }
                }
              `,
                    });
                }, (result) => { var _a, _b; return ((_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.ListCartReadModels) === null || _b === void 0 ? void 0 : _b.items.length) >= 1; });
                const cartData = queryResult.data.ListCartReadModels.items;
                (0, expect_1.expect)(cartData).to.be.an('array');
                (0, expect_1.expect)(cartData.length).to.be.gte(1);
            });
            it('should retrieve a specific cart using filters using paginated read model', async () => {
                const filter = { id: { eq: mockCartId } };
                const queryResult = await (0, sleep_1.waitForIt)(() => {
                    return client.query({
                        variables: {
                            filter: filter,
                        },
                        query: (0, client_1.gql) `
                query ListCartReadModels($filter: ListCartReadModelFilter) {
                  ListCartReadModels(filter: $filter) {
                    items {
                      id
                    }
                  }
                }
              `,
                    });
                }, (result) => { var _a, _b; return ((_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.ListCartReadModels) === null || _b === void 0 ? void 0 : _b.items.length) >= 1; });
                const cartData = queryResult.data.ListCartReadModels.items;
                (0, expect_1.expect)(cartData).to.be.an('array');
                (0, expect_1.expect)(cartData.length).to.equal(1);
                (0, expect_1.expect)(cartData[0].id).to.equal(mockCartId);
            });
            it('should retrieve a list of carts using complex filters using paginated read model', async () => {
                const filter = { cartItems: { includes: { productId: mockProductId, quantity: 2 } } };
                const queryResult = await (0, sleep_1.waitForIt)(() => {
                    return client.query({
                        variables: {
                            filter: filter,
                        },
                        query: (0, client_1.gql) `
                query ListCartReadModels($filter: ListCartReadModelFilter) {
                  ListCartReadModels(filter: $filter) {
                    items {
                      id
                    }
                  }
                }
              `,
                    });
                }, (result) => { var _a, _b, _c; return ((_c = (_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.ListCartReadModels) === null || _b === void 0 ? void 0 : _b.items) === null || _c === void 0 ? void 0 : _c.length) >= 1; });
                const cartData = queryResult.data.ListCartReadModels.items;
                (0, expect_1.expect)(cartData).to.be.an('array');
                (0, expect_1.expect)(cartData.length).to.equal(1);
                (0, expect_1.expect)(cartData[0].id).to.equal(mockCartId);
            });
            it('should retrieve a list of carts using AND by default with paginated read model', async () => {
                const filter = {
                    shippingAddress: {
                        firstName: {
                            eq: mockAddress.firstName,
                        },
                    },
                    cartItems: {
                        includes: {
                            productId: mockProductId,
                            quantity: mockQuantity,
                        },
                    },
                    cartItemsIds: {
                        includes: mockProductId,
                    },
                };
                const queryResult = await (0, sleep_1.waitForIt)(() => {
                    return client.query({
                        variables: {
                            filter: filter,
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
                      checks
                      shippingAddress {
                        firstName
                      }
                      payment {
                        cartId
                      }
                      cartItemsIds
                    }
                    cursor
                  }
                }
              `,
                    });
                }, (result) => { var _a, _b, _c; return ((_c = (_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.ListCartReadModels) === null || _b === void 0 ? void 0 : _b.items) === null || _c === void 0 ? void 0 : _c.length) === 1; });
                const cartData = queryResult.data.ListCartReadModels.items;
                (0, expect_1.expect)(cartData).to.be.an('array');
                (0, expect_1.expect)(cartData.length).to.equal(1);
                (0, expect_1.expect)(cartData[0].id).to.equal(mockCartId);
            });
            it('should not fail if search a list of carts with empty results', async () => {
                var _a;
                const filter = { cartItems: { includes: { productId: mockProductId, quantity: 200 } } };
                const queryResult = await client.query({
                    variables: {
                        filter: filter,
                    },
                    query: (0, client_1.gql) `
            query ListCartReadModels($filter: ListCartReadModelFilter) {
              ListCartReadModels(filter: $filter) {
                items {
                  id
                }
              }
            }
          `,
                });
                const cartData = (_a = queryResult === null || queryResult === void 0 ? void 0 : queryResult.data) === null || _a === void 0 ? void 0 : _a.ListCartReadModels.items;
                (0, expect_1.expect)(cartData.length).to.equal(0);
            });
        });
        context('query sorted lists of carts', () => {
            const mockCartItems = [];
            const cartItems = 5;
            let mockAddress;
            beforeEach(async () => {
                for (let i = 0; i < cartItems; i++) {
                    const mockQuantity = i;
                    const mockProductId = faker_1.random.uuid();
                    const mockCartId = faker_1.random.uuid();
                    const mockFirstName = String.fromCharCode(i + 65);
                    mockAddress = {
                        firstName: mockFirstName,
                        lastName: faker_1.random.word(),
                        country: faker_1.random.word(),
                        state: faker_1.random.word(),
                        postalCode: faker_1.random.word(),
                        address: faker_1.random.word(),
                    };
                    mockCartItems.push({
                        id: mockCartId,
                        productId: mockProductId,
                        quantity: mockQuantity,
                        firstName: mockFirstName,
                    });
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
                    await (0, sleep_1.waitForIt)(() => {
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
                    await client.mutate({
                        variables: {
                            cartId: mockCartId,
                            address: mockAddress,
                        },
                        mutation: (0, client_1.gql) `
              mutation UpdateShippingAddress($cartId: ID!, $address: AddressInput!) {
                UpdateShippingAddress(input: { cartId: $cartId, address: $address })
              }
            `,
                    });
                    await (0, sleep_1.waitForIt)(() => {
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
                      shippingAddress {
                        firstName
                      }
                    }
                  }
                `,
                        });
                    }, (result) => { var _a, _b, _c; return ((_c = (_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.CartReadModel) === null || _b === void 0 ? void 0 : _b.shippingAddress) === null || _c === void 0 ? void 0 : _c.firstName) != null; });
                }
            });
            afterEach(async () => {
                mockCartItems.length = 0;
            });
            it('should retrieve a sorted list of carts using paginated read model', async () => {
                if (process.env.TESTED_PROVIDER !== 'AZURE' && process.env.TESTED_PROVIDER !== 'LOCAL') {
                    console.log('****************** Warning **********************');
                    console.log('Only Azure and Local provider implement the sort option');
                    console.log('*************************************************');
                    return;
                }
                const expectedIds = mockCartItems.map((item) => item.id);
                const mockedSortBy = { id: 'DESC' };
                const queryResult = await (0, sleep_1.waitForIt)(() => {
                    return client.query({
                        variables: {
                            filterBy: { id: { in: expectedIds } },
                            sortBy: mockedSortBy,
                        },
                        query: (0, client_1.gql) `
                query ListCartReadModels($filterBy: ListCartReadModelFilter, $sortBy: CartReadModelSortBy) {
                  ListCartReadModels(filter: $filterBy, sortBy: $sortBy) {
                    items {
                      id
                    }
                  }
                }
              `,
                    });
                }, (result) => { var _a, _b; return ((_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.ListCartReadModels) === null || _b === void 0 ? void 0 : _b.items.length) === cartItems; });
                const cartData = queryResult.data.ListCartReadModels.items;
                (0, expect_1.expect)(cartData).to.be.an('array');
                const reverseExpectedIds = expectedIds.sort((a, b) => {
                    return a > b ? -1 : a < b ? 1 : 0;
                });
                // @ts-ignore
                (0, expect_1.expect)(cartData.map((item) => item.id)).to.be.eql(reverseExpectedIds);
            });
            it('should retrieve a sorted list of carts using nested fields', async () => {
                if (process.env.TESTED_PROVIDER !== 'AZURE' && process.env.TESTED_PROVIDER !== 'LOCAL') {
                    console.log('****************** Warning **********************');
                    console.log('Only Azure and Local provider implement the sort option');
                    console.log('*************************************************');
                    return;
                }
                const expectedIds = mockCartItems.map((item) => item.id);
                const mockedSortBy = { shippingAddress: { firstName: 'DESC' } };
                const queryResult = await (0, sleep_1.waitForIt)(() => {
                    return client.query({
                        variables: {
                            filterBy: { id: { in: expectedIds } },
                            sortBy: mockedSortBy,
                        },
                        query: (0, client_1.gql) `
                query ListCartReadModels($filterBy: ListCartReadModelFilter, $sortBy: CartReadModelSortBy) {
                  ListCartReadModels(filter: $filterBy, sortBy: $sortBy) {
                    items {
                      id
                      shippingAddress {
                        firstName
                      }
                    }
                  }
                }
              `,
                    });
                }, (result) => { var _a, _b; return ((_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.ListCartReadModels) === null || _b === void 0 ? void 0 : _b.items.length) === cartItems; });
                const cartData = queryResult.data.ListCartReadModels.items;
                (0, expect_1.expect)(cartData).to.be.an('array');
                const expectedFirstNames = mockCartItems.map((item) => item.firstName);
                const reverseExpectedFirstNames = expectedFirstNames.sort((a, b) => {
                    return a > b ? -1 : a < b ? 1 : 0;
                });
                // @ts-ignore
                const names = cartData.map((item) => item.shippingAddress.firstName);
                (0, expect_1.expect)(names).to.be.eql(reverseExpectedFirstNames);
            });
            it('should retrieve a sorted list of carts using two fields', async () => {
                if (process.env.TESTED_PROVIDER !== 'LOCAL') {
                    console.log('****************** Warning **********************');
                    console.log('Only Local provider implement the sort option for more than one sort field');
                    console.log('*************************************************');
                    return;
                }
                const expectedIds = mockCartItems.map((item) => item.id);
                const mockedSortBy = { id: 'DESC', shippingAddress: { firstName: 'ASC' } };
                const queryResult = await (0, sleep_1.waitForIt)(() => {
                    return client.query({
                        variables: {
                            filterBy: { id: { in: expectedIds } },
                            sortBy: mockedSortBy,
                        },
                        query: (0, client_1.gql) `
                query ListCartReadModels($filterBy: ListCartReadModelFilter, $sortBy: CartReadModelSortBy) {
                  ListCartReadModels(filter: $filterBy, sortBy: $sortBy) {
                    items {
                      id
                    }
                  }
                }
              `,
                    });
                }, (result) => { var _a, _b; return ((_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.ListCartReadModels) === null || _b === void 0 ? void 0 : _b.items.length) === cartItems; });
                const cartData = queryResult.data.ListCartReadModels.items;
                (0, expect_1.expect)(cartData).to.be.an('array');
                const reverseExpectedIds = expectedIds.sort((a, b) => {
                    return a > b ? -1 : a < b ? 1 : 0;
                });
                // @ts-ignore
                (0, expect_1.expect)(cartData.map((item) => item.id)).to.be.eql(reverseExpectedIds);
            });
        });
        context('query using pagination', () => {
            const mockCartIds = [];
            const mockCartItems = [];
            let mockProductId;
            let mockQuantity;
            const changeCartPromises = [];
            const cartsNumber = 3;
            beforeEach(async () => {
                mockProductId = faker_1.random.uuid();
                mockQuantity = 2;
                mockCartItems.push({ productId: mockProductId, quantity: mockQuantity });
                for (let i = 0; i < cartsNumber; i++) {
                    const mockCartId = faker_1.random.uuid();
                    mockCartIds.push(mockCartId);
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
            it('should retrieve a list of carts limited to 2 items', async () => {
                const limit = 2;
                const queryResult = await (0, sleep_1.waitForIt)(() => {
                    return client.query({
                        variables: {
                            limit: limit,
                        },
                        query: (0, client_1.gql) `
                query ListCartReadModels($limit: Int) {
                  ListCartReadModels(limit: $limit) {
                    items {
                      id
                    }
                  }
                }
              `,
                    });
                }, (result) => { var _a, _b; return ((_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.ListCartReadModels) === null || _b === void 0 ? void 0 : _b.items.length) == 2; });
                const cartData = queryResult.data.ListCartReadModels.items;
                (0, expect_1.expect)(cartData).to.be.an('array');
                (0, expect_1.expect)(cartData.length).to.equal(2);
            });
            it('should retrieve a list of carts paginated', async () => {
                const limit = 1;
                let cursor = undefined;
                for (let i = 0; i < cartsNumber; i++) {
                    const queryResult = await (0, sleep_1.waitForIt)(() => {
                        return client.query({
                            variables: {
                                limit: limit,
                                afterCursor: cursor,
                            },
                            query: (0, client_1.gql) `
                  query ListCartReadModels($limit: Int, $afterCursor: JSON) {
                    ListCartReadModels(limit: $limit, afterCursor: $afterCursor) {
                      cursor
                      items {
                        id
                      }
                    }
                  }
                `,
                        });
                    }, (result) => { var _a, _b; return ((_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.ListCartReadModels) === null || _b === void 0 ? void 0 : _b.items.length) === 1; });
                    const currentPageCartData = queryResult.data.ListCartReadModels.items;
                    cursor = queryResult.data.ListCartReadModels.cursor;
                    if (cursor) {
                        if (process.env.TESTED_PROVIDER === 'AZURE' || process.env.TESTED_PROVIDER === 'LOCAL') {
                            (0, expect_1.expect)(cursor.id).to.equal((i + 1).toString());
                        }
                        else {
                            (0, expect_1.expect)(cursor.id).to.equal(currentPageCartData[0].id);
                        }
                    }
                    (0, expect_1.expect)(currentPageCartData).to.be.an('array');
                    (0, expect_1.expect)(currentPageCartData.length).to.equal(1);
                    (0, expect_1.expect)(cursor).to.not.be.undefined;
                }
            });
        });
        context('projecting fields', () => {
            if (process.env.TESTED_PROVIDER === 'AWS') {
                console.log('AWS Provider ReadModel projecting field is not supported');
                return;
            }
            const mockCartId = faker_1.random.uuid();
            const mockProductId = faker_1.random.uuid();
            const mockQuantity = faker_1.random.number({ min: 1 });
            const mockAddress = {
                firstName: faker_1.random.word(),
                lastName: faker_1.random.word(),
                country: faker_1.random.word(),
                state: faker_1.random.word(),
                postalCode: faker_1.random.word(),
                address: faker_1.random.word(),
            };
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
                await (0, sleep_1.waitForIt)(() => {
                    return client.query({
                        variables: {
                            cartId: mockCartId,
                        },
                        query: (0, client_1.gql) `
                query CartReadModel($cartId: ID!) {
                  CartReadModel(id: $cartId) {
                    id
                  }
                }
              `,
                    });
                }, (result) => { var _a; return ((_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.CartReadModel) != null; });
                // update shipping address
                await client.mutate({
                    variables: {
                        cartId: mockCartId,
                        address: mockAddress,
                    },
                    mutation: (0, client_1.gql) `
            mutation UpdateShippingAddress($cartId: ID!, $address: AddressInput!) {
              UpdateShippingAddress(input: { cartId: $cartId, address: $address })
            }
          `,
                });
                await (0, sleep_1.waitForIt)(() => {
                    return client.query({
                        variables: {
                            cartId: mockCartId,
                        },
                        query: (0, client_1.gql) `
                query CartReadModel($cartId: ID!) {
                  CartReadModel(id: $cartId) {
                    id
                    shippingAddress {
                      firstName
                    }
                  }
                }
              `,
                    });
                }, (result) => {
                    var _a, _b, _c, _d;
                    return ((_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.CartReadModel) != null &&
                        ((_d = (_c = (_b = result === null || result === void 0 ? void 0 : result.data) === null || _b === void 0 ? void 0 : _b.CartReadModel) === null || _c === void 0 ? void 0 : _c.shippingAddress) === null || _d === void 0 ? void 0 : _d.firstName) === mockAddress.firstName;
                });
            });
            it('with paginatedVersion true', async () => {
                const queryResult = await (0, sleep_1.waitForIt)(() => {
                    return client.mutate({
                        variables: {
                            cartId: mockCartId,
                            paginatedVersion: true,
                        },
                        mutation: (0, client_1.gql) `
                mutation CartShippingAddress($cartId: ID!, $paginatedVersion: Boolean!) {
                  CartShippingAddress(input: { cartId: $cartId, paginatedVersion: $paginatedVersion })
                }
              `,
                    });
                }, (result) => { var _a; return ((_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.CartShippingAddress) != null; });
                const cartShippingAddress = queryResult.data.CartShippingAddress;
                (0, expect_1.expect)(cartShippingAddress).to.deep.equal({
                    items: [
                        {
                            id: mockCartId,
                            shippingAddress: {
                                firstName: mockAddress.firstName,
                                lastName: mockAddress.lastName,
                                country: mockAddress.country,
                                state: mockAddress.state,
                                postalCode: mockAddress.postalCode,
                                address: mockAddress.address,
                            },
                        },
                    ],
                    count: 1,
                    cursor: {
                        id: '1',
                    },
                });
            });
            it('with paginatedVersion false', async () => {
                const queryResult = await (0, sleep_1.waitForIt)(() => {
                    return client.mutate({
                        variables: {
                            cartId: mockCartId,
                            paginatedVersion: false,
                        },
                        mutation: (0, client_1.gql) `
                mutation CartShippingAddress($cartId: ID!, $paginatedVersion: Boolean!) {
                  CartShippingAddress(input: { cartId: $cartId, paginatedVersion: $paginatedVersion })
                }
              `,
                    });
                }, (result) => { var _a; return ((_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.CartShippingAddress) != null; });
                const cartShippingAddress = queryResult.data.CartShippingAddress;
                (0, expect_1.expect)(cartShippingAddress).to.deep.equal([
                    {
                        id: mockCartId,
                        shippingAddress: {
                            firstName: mockAddress.firstName,
                            lastName: mockAddress.lastName,
                            country: mockAddress.country,
                            state: mockAddress.state,
                            postalCode: mockAddress.postalCode,
                            address: mockAddress.address,
                        },
                    },
                ]);
            });
        });
    });
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
            (0, expect_1.expect)(cartData).to.be.deep.equal(expectedResult);
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
            (0, expect_1.expect)(updatedCartData).to.be.deep.equal(expectedUpdatedResult);
        });
    });
    describe('projecting two entities with array joinKey', () => {
        let client;
        let userToken;
        before(async () => {
            const userEmail = faker_1.internet.email();
            userToken = setup_1.applicationUnderTest.token.forUser(userEmail, 'UserWithEmail');
            client = setup_1.applicationUnderTest.graphql.client(userToken);
        });
        const oneMockProductId = faker_1.random.uuid();
        const twoMockProductId = faker_1.random.uuid();
        beforeEach(async () => {
            // Add item one
            await client.mutate({
                variables: {
                    productID: oneMockProductId,
                    sku: faker_1.random.uuid(),
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
                productID: $productID
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
            // Add item two
            await client.mutate({
                variables: {
                    productID: twoMockProductId,
                    sku: faker_1.random.uuid(),
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
                productID: $productID
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
        });
        it('should project changes for both entities', async () => {
            // Check that new product is available in read model
            const products = await (0, sleep_1.waitForIt)(() => {
                return client.query({
                    variables: {
                        products: [oneMockProductId, twoMockProductId],
                    },
                    query: (0, client_1.gql) `
              query ProductReadModels($products: [ID!]!) {
                ProductReadModels(filter: { id: { in: $products } }) {
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
                  packs {
                    id
                  }
                  productDetails
                  productType
                }
              }
            `,
                });
            }, (result) => {
                var _a, _b, _c, _d;
                return ((_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.ProductReadModels) === null || _b === void 0 ? void 0 : _b.length) == 2 &&
                    ((_d = (_c = result === null || result === void 0 ? void 0 : result.data) === null || _c === void 0 ? void 0 : _c.ProductReadModels) === null || _d === void 0 ? void 0 : _d.every((product) => Array.isArray(product.packs) && product.packs.length == 0));
            });
            (0, expect_1.expect)(products.data.ProductReadModels.length).to.be.equal(2);
            const mockPackId = faker_1.random.uuid();
            const mockPackName = faker_1.commerce.productName();
            const mockPackProducts = [oneMockProductId, twoMockProductId];
            // Create Pack
            await client.mutate({
                variables: {
                    packID: mockPackId,
                    name: mockPackName,
                    products: mockPackProducts,
                },
                mutation: (0, client_1.gql) `
          mutation CreatePack($packID: ID!, $name: String!, $products: [ID!]!) {
            CreatePack(input: { packID: $packID, name: $name, products: $products })
          }
        `,
            });
            const updatedQueryResults = await (0, sleep_1.waitForIt)(() => {
                return client.query({
                    variables: {
                        products: [oneMockProductId, twoMockProductId],
                    },
                    query: (0, client_1.gql) `
              query ProductReadModels($products: [ID!]!) {
                ProductReadModels(filter: { id: { in: $products } }) {
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
                  packs {
                    id
                    name
                    products
                  }
                  productDetails
                  productType
                }
              }
            `,
                });
            }, (result) => {
                var _a, _b, _c, _d;
                return ((_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.ProductReadModels) === null || _b === void 0 ? void 0 : _b.length) == 2 &&
                    ((_d = (_c = result === null || result === void 0 ? void 0 : result.data) === null || _c === void 0 ? void 0 : _c.ProductReadModels) === null || _d === void 0 ? void 0 : _d.every((product) => Array.isArray(product.packs) && product.packs.length == 1));
            });
            const updatedProducts = updatedQueryResults.data.ProductReadModels;
            (0, expect_1.expect)(updatedProducts.length).to.be.equal(2);
            updatedProducts.forEach((product) => {
                (0, expect_1.expect)(product.packs).to.be.deep.equal([
                    {
                        __typename: 'Pack',
                        id: mockPackId,
                        name: mockPackName,
                        products: mockPackProducts,
                    },
                ]);
            });
        });
    });
    describe('read model authorization', () => {
        context('with an anonymous user', () => {
            let anonymousClient;
            beforeEach(() => {
                anonymousClient = setup_1.applicationUnderTest.graphql.client();
            });
            context('with a public read model', () => {
                it('should be accessible', async () => {
                    const resultPromise = anonymousClient.query({
                        variables: {
                            cartId: 'mockCartId',
                        },
                        query: (0, client_1.gql) `
              query CartReadModel($cartId: ID!) {
                CartReadModel(id: $cartId) {
                  id
                }
              }
            `,
                    });
                    await (0, expect_1.expect)(resultPromise).to.be.eventually.fulfilled;
                });
            });
            context('with a read model authorized for certain roles', () => {
                it('should not be accessible', async () => {
                    const resultPromise = anonymousClient.query({
                        variables: {
                            id: 'mockId',
                        },
                        query: (0, client_1.gql) `
              query ProductReadModel($id: ID!) {
                ProductReadModel(id: $id) {
                  id
                }
              }
            `,
                    });
                    await (0, expect_1.expect)(resultPromise).to.eventually.be.rejectedWith(/Access denied for this resource/);
                });
            });
            context('with a read model with a custom authorizer', () => {
                it('should not be accessible', async () => {
                    const resultPromise = anonymousClient.query({
                        variables: {
                            id: 'mockId',
                        },
                        query: (0, client_1.gql) `
              query SpecialReportsReadModel($id: ID!) {
                SpecialReportsReadModel(id: $id) {
                  id
                }
              }
            `,
                    });
                    await (0, expect_1.expect)(resultPromise).to.eventually.be.rejectedWith(/You are not allowed to see such insightful report/);
                });
            });
        });
        context('with a user with a role', () => {
            let loggedClient;
            beforeEach(() => {
                const userToken = setup_1.applicationUnderTest.token.forUser(faker_1.internet.email(), 'UserWithEmail');
                loggedClient = setup_1.applicationUnderTest.graphql.client(userToken);
            });
            context('with a public read model', () => {
                it('should be accessible', async () => {
                    const resultPromise = loggedClient.query({
                        variables: {
                            cartId: 'mockCartId',
                        },
                        query: (0, client_1.gql) `
              query CartReadModel($cartId: ID!) {
                CartReadModel(id: $cartId) {
                  id
                }
              }
            `,
                    });
                    await (0, expect_1.expect)(resultPromise).to.eventually.be.fulfilled;
                });
            });
            context('with a read model authorized for matching roles', () => {
                it('should be accessible', async () => {
                    const resultPromise = loggedClient.query({
                        variables: {
                            id: 'mockId',
                        },
                        query: (0, client_1.gql) `
              query ProductReadModel($id: ID!) {
                ProductReadModel(id: $id) {
                  id
                }
              }
            `,
                    });
                    await (0, expect_1.expect)(resultPromise).to.eventually.be.fulfilled;
                });
            });
            context('with a read model with a custom authorizer', () => {
                it('should not be accessible', async () => {
                    const resultPromise = loggedClient.query({
                        variables: {
                            id: 'mockId',
                        },
                        query: (0, client_1.gql) `
              query SpecialReportsReadModel($id: ID!) {
                SpecialReportsReadModel(id: $id) {
                  id
                }
              }
            `,
                    });
                    await (0, expect_1.expect)(resultPromise).to.eventually.be.rejectedWith(/You are not allowed to see such insightful report/);
                });
            });
        });
        context('with a user that fulfills the custom authorizer', () => {
            let knowledgeableClient;
            beforeEach(() => {
                const tokenWithSpecialAccess = setup_1.applicationUnderTest.token.forUser(faker_1.internet.email(), undefined, {
                    customClaims: {
                        specialReportAccess: 'true',
                    },
                });
                knowledgeableClient = setup_1.applicationUnderTest.graphql.client(tokenWithSpecialAccess);
            });
            context('with a public read model', () => {
                it('should be accessible', async () => {
                    const resultPromise = knowledgeableClient.query({
                        variables: {
                            cartId: 'mockCartId',
                        },
                        query: (0, client_1.gql) `
              query CartReadModel($cartId: ID!) {
                CartReadModel(id: $cartId) {
                  id
                }
              }
            `,
                    });
                    await (0, expect_1.expect)(resultPromise).to.eventually.be.fulfilled;
                });
            });
            context('with a read model authorized for certain roles', () => {
                it('should not be accessible', async () => {
                    const resultPromise = knowledgeableClient.query({
                        variables: {
                            id: 'mockId',
                        },
                        query: (0, client_1.gql) `
              query ProductReadModel($id: ID!) {
                ProductReadModel(id: $id) {
                  id
                }
              }
            `,
                    });
                    await (0, expect_1.expect)(resultPromise).to.eventually.be.rejectedWith(/Access denied for this resource/);
                });
            });
            context('with a read model with a custom authorizer', () => {
                it('should be accessible', async () => {
                    const resultPromise = knowledgeableClient.query({
                        variables: {
                            id: 'mockId',
                        },
                        query: (0, client_1.gql) `
              query SpecialReportsReadModel($id: ID!) {
                SpecialReportsReadModel(id: $id) {
                  id
                }
              }
            `,
                    });
                    await (0, expect_1.expect)(resultPromise).to.eventually.be.fulfilled;
                });
            });
        });
    });
});
