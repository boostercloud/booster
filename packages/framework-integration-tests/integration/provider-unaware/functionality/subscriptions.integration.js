"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const faker_1 = require("faker");
const expect_1 = require("../../helper/expect");
const sleep_1 = require("../../helper/sleep");
const setup_1 = require("./setup");
const constants_1 = require("../../../src/constants");
const client_1 = require("@apollo/client");
describe('subscriptions', () => {
    let countSubscriptions;
    let countConnections;
    before(async () => {
        countSubscriptions = setup_1.applicationUnderTest.count.subscriptions.bind(setup_1.applicationUnderTest.count);
        countConnections = setup_1.applicationUnderTest.count.connections.bind(setup_1.applicationUnderTest.count);
    });
    describe('the "unsubscribe" operation', () => {
        let client;
        before(async () => {
            client = await setup_1.applicationUnderTest.graphql.clientWithSubscriptions();
        });
        after(() => {
            client.disconnect();
        });
        it('should delete a subscription when the client calls "unsubscribe"', async () => {
            const originalSubscriptionsCount = await countSubscriptions();
            // Let's create two subscriptions to the same read model
            cartSubscription(client, faker_1.random.uuid()).subscribe(() => { });
            const subscriptionObservable = cartSubscription(client, faker_1.random.uuid()).subscribe(() => { });
            // Wait for the subscriptions to arrive
            await (0, sleep_1.waitForIt)(countSubscriptions, (newCount) => newCount == originalSubscriptionsCount + 2);
            // Stop one of the subscription
            subscriptionObservable.unsubscribe();
            // And now check that the new subscriptions count down by one
            await (0, sleep_1.waitForIt)(countSubscriptions, (newCount) => newCount == originalSubscriptionsCount + 1);
        });
    });
    describe('the "terminate" operation', () => {
        it('should delete all subscription of the connectionID when socket is disconnected', async () => {
            const clientA = await setup_1.applicationUnderTest.graphql.clientWithSubscriptions();
            const clientB = await setup_1.applicationUnderTest.graphql.clientWithSubscriptions();
            try {
                const originalSubscriptionsCount = await countSubscriptions();
                // Let's create one subscription for one client
                cartSubscription(clientA, faker_1.random.uuid()).subscribe(() => { });
                // Let's create two subscriptions for another client
                cartSubscription(clientB, faker_1.random.uuid()).subscribe(() => { });
                cartSubscription(clientB, faker_1.random.uuid()).subscribe(() => { });
                // Wait for the subscriptions to arrive
                await (0, sleep_1.waitForIt)(countSubscriptions, (newCount) => newCount == originalSubscriptionsCount + 3);
                // Now we close the socket of client B and check its 2 subscriptions were deleted
                clientB.disconnect();
                await (0, sleep_1.waitForIt)(countSubscriptions, (newCount) => newCount == originalSubscriptionsCount + 1);
                // Finally, close the socket of client A and check that we are back to the original count of subscriptions
                clientA.disconnect();
                await (0, sleep_1.waitForIt)(countSubscriptions, (newCount) => newCount == originalSubscriptionsCount);
            }
            catch (e) {
                clientA.disconnect();
                clientB.disconnect();
            }
        });
        it('should delete connection data when socket is disconnected', async () => {
            const connectionsCount = await countConnections();
            const client = await setup_1.applicationUnderTest.graphql.clientWithSubscriptions();
            try {
                await (0, sleep_1.waitForIt)(countConnections, (newCount) => newCount == connectionsCount + 1);
                client.disconnect();
                await (0, sleep_1.waitForIt)(countConnections, (newCount) => newCount == connectionsCount);
            }
            catch {
                client.disconnect();
            }
        });
    });
    describe('when socket reconnects ', () => {
        let clients;
        const clientCount = 2;
        before(async () => {
            clients = [];
            for (let i = 0; i < clientCount; i++)
                clients.push(await setup_1.applicationUnderTest.graphql.clientWithSubscriptions());
        });
        after(() => {
            clients.forEach(c => c.disconnect());
        });
        it('keeps the same subscriptions', async () => {
            const cartID = faker_1.random.uuid();
            const originalSubscriptionsCount = await countSubscriptions();
            const observables = clients.map(c => cartSubscription(c, cartID));
            observables.forEach(o => o.subscribe(() => { }));
            await (0, sleep_1.waitForIt)(countSubscriptions, (newCount) => newCount == originalSubscriptionsCount + clientCount);
            await verifySubscriptionsActive();
            await Promise.all(clients.map(c => c.reconnect()));
            await verifySubscriptionsActive();
            async function verifySubscriptionsActive() {
                await cartMutation(clients[0], cartID);
                await (0, expect_1.expect)(Promise.all(observables.map(promisifyNextSubscriptionResult))).to.eventually.be.fulfilled;
            }
        });
    });
    describe('with filters', () => {
        let client;
        before(async () => {
            client = await setup_1.applicationUnderTest.graphql.clientWithSubscriptions();
        });
        after(() => {
            client.disconnect();
        });
        it('get a carts with a specific ID', async () => {
            const cartID = faker_1.random.uuid();
            const originalSubscriptionsCount = await countSubscriptions();
            // Let's create two subscriptions to the same read model
            const observable = cartFilteredSubscription(client, { id: { eq: cartID } });
            // Call the subscribe function to send the subscription to server
            observable.subscribe(() => { });
            // Wait for the subscriptions to arrive
            await (0, sleep_1.waitForIt)(countSubscriptions, (newCount) => newCount == originalSubscriptionsCount + 1);
            // Check we receive data when the read model is modified
            await cartMutation(client, cartID);
            const result = await promisifyNextSubscriptionResult(observable);
            const cart = result.data.CartReadModels;
            (0, expect_1.expect)(cart.id).to.equal(cartID);
        });
        it('filters based on before hooks when user sends filters in the subscription query', async () => {
            const cartID = 'before-fn-test';
            const originalSubscriptionsCount = await countSubscriptions();
            // Let's create two subscriptions to the same read model
            const observable = cartFilteredSubscription(client, { id: { eq: cartID } });
            // Call the subscribe function to send the subscription to server
            const subscriptionPromise = promisifyNextSubscriptionResult(observable);
            // Wait for for the subscriptions to arrive
            await (0, sleep_1.waitForIt)(countSubscriptions, (newCount) => newCount == originalSubscriptionsCount + 1);
            // Check we receive data when the read model is modified
            await cartMutation(client, 'before-fn-test-modified', constants_1.beforeHookProductId);
            const result = await subscriptionPromise;
            const cart = result.data.CartReadModels;
            (0, expect_1.expect)(cart.id).to.equal('before-fn-test-modified');
        });
        it('filters based on single before hooks when user don not send filters but a specific id', async () => {
            const cartID = 'before-fn-test';
            const originalSubscriptionsCount = await countSubscriptions();
            // Let's create two subscriptions to the same read model
            const observable = cartFilteredSingleIDSubscription(client, cartID);
            // Call the subscribe function to send the subscription to server
            const subscriptionPromise = promisifyNextSubscriptionResult(observable);
            // Wait for for the subscriptions to arrive
            await (0, sleep_1.waitForIt)(countSubscriptions, (newCount) => newCount == originalSubscriptionsCount + 1);
            // Check we receive data when the read model is modified
            await cartMutation(client, 'before-fn-test-modified', constants_1.beforeHookProductId);
            const result = await subscriptionPromise;
            const cart = result.data.CartReadModel;
            (0, expect_1.expect)(cart.id).to.equal('before-fn-test-modified');
        });
        it('get the carts with an specific product id', async () => {
            const cartID = faker_1.random.uuid();
            const productId = faker_1.random.uuid();
            const originalSubscriptionsCount = await countSubscriptions();
            // Let's create two subscriptions to the same read model
            const observable = cartFilteredSubscription(client, {
                cartItems: { includes: { productId: productId, quantity: 2 } },
            });
            // Call the subscribe function to send the subscription to server
            observable.subscribe(() => { });
            // Wait for for the subscriptions to arrive
            await (0, sleep_1.waitForIt)(countSubscriptions, (newCount) => newCount == originalSubscriptionsCount + 1);
            // Check we receive data when the read model is modified
            await cartMutation(client, cartID, productId);
            const result = await promisifyNextSubscriptionResult(observable);
            const cart = result.data.CartReadModels;
            (0, expect_1.expect)(cart.id).to.equal(cartID);
            const isWellFiltered = cart.cartItems.some((cartItem) => cartItem.productId === productId);
            (0, expect_1.expect)(isWellFiltered).to.be.true;
        });
        it('get the cart filtering by an array of strings', async () => {
            const cartID = faker_1.random.uuid();
            const productId = faker_1.random.uuid();
            const originalSubscriptionsCount = await countSubscriptions();
            // Let's create two subscriptions to the same read model
            const observable = cartFilteredSubscription(client, {
                cartItemsIds: { includes: productId },
            });
            // Call the subscribe function to send the subscription to server
            observable.subscribe(() => { });
            // Wait for for the subscriptions to arrive
            await (0, sleep_1.waitForIt)(countSubscriptions, (newCount) => newCount == originalSubscriptionsCount + 1);
            // Check we receive data when the read model is modified
            await cartMutation(client, cartID, productId);
            const result = await promisifyNextSubscriptionResult(observable);
            const cart = result.data.CartReadModels;
            (0, expect_1.expect)(cart.id).to.equal(cartID);
            const isWellFiltered = cart.cartItemsIds.some((cartItemId) => cartItemId === productId);
            (0, expect_1.expect)(isWellFiltered).to.be.true;
        });
    });
    describe('readmodel authorization', () => {
        context('with an anonymous user', () => {
            let client;
            beforeEach(async () => {
                client = await setup_1.applicationUnderTest.graphql.clientWithSubscriptions();
            });
            afterEach(() => {
                client.disconnect();
            });
            context('with a read model authorized for matching roles', () => {
                it('should not be accessible', async () => {
                    const productId = faker_1.random.uuid();
                    const observable = productSubscription(client, productId);
                    let error;
                    observable.subscribe(() => { }, (err) => {
                        error = err;
                    });
                    await (0, sleep_1.waitForIt)(() => Promise.resolve(error), (error) => error !== undefined && error.message === 'Access denied for this resource');
                });
            });
        });
        context('with a user without the required role', () => {
            let loggedClient;
            beforeEach(async () => {
                const userToken = setup_1.applicationUnderTest.token.forUser(faker_1.internet.email(), 'UserThatHasNoBusinesWithProducts');
                loggedClient = await setup_1.applicationUnderTest.graphql.clientWithSubscriptions(userToken);
            });
            afterEach(() => {
                loggedClient.disconnect();
            });
            context('with a read model authorized for matching roles', () => {
                it('should not be accessible', async () => {
                    const productId = faker_1.random.uuid();
                    const observable = productSubscription(loggedClient, productId);
                    let error;
                    observable.subscribe(() => { }, (err) => {
                        error = err;
                    });
                    await (0, sleep_1.waitForIt)(() => Promise.resolve(error), (error) => error !== undefined && error.message === 'Access denied for this resource');
                });
            });
        });
        context('with a user with the required role', () => {
            let loggedClient;
            beforeEach(async () => {
                const userToken = setup_1.applicationUnderTest.token.forUser(faker_1.internet.email(), 'UserWithEmail');
                loggedClient = await setup_1.applicationUnderTest.graphql.clientWithSubscriptions(userToken);
            });
            afterEach(() => {
                loggedClient.disconnect();
            });
            context('with a read model authorized for matching roles', () => {
                it('should be accessible', async () => {
                    const productId = faker_1.random.uuid();
                    const originalSubscriptionsCount = await countSubscriptions();
                    const observable = productSubscription(loggedClient, productId);
                    observable.subscribe(() => { });
                    await (0, sleep_1.waitForIt)(countSubscriptions, (newCount) => newCount == originalSubscriptionsCount + 1);
                    await productMutation(loggedClient, productId);
                    const result = await promisifyNextSubscriptionResult(observable);
                    const productReadModel = result.data.ProductReadModel;
                    (0, expect_1.expect)(productReadModel.id).to.equal(productId);
                });
            });
        });
    });
});
function cartSubscription(client, cartID) {
    return client.subscribe({
        variables: { cartId: cartID },
        query: (0, client_1.gql) `
      subscription CartReadModel($cartId: ID!) {
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
}
function productSubscription(client, productId) {
    return client.subscribe({
        variables: { productId: productId },
        query: (0, client_1.gql) `
      subscription ProductReadModel($productId: ID!) {
        ProductReadModel(id: $productId) {
          id
          price {
            cents
            currency
          }
        }
      }
    `,
    });
}
function cartFilteredSubscription(client, filter) {
    return client.subscribe({
        variables: { filter },
        query: (0, client_1.gql) `
      subscription CartReadModels($filter: CartReadModelSubscriptionFilter) {
        CartReadModels(filter: $filter) {
          id
          cartItems {
            productId
            quantity
          }
          cartItemsIds
        }
      }
    `,
    });
}
function cartFilteredSingleIDSubscription(client, cartId) {
    return client.subscribe({
        variables: { cartId, random: 'variable' },
        query: (0, client_1.gql) `
      subscription CartReadModel($cartId: ID!) {
        CartReadModel(id: $cartId) {
          id
          cartItems {
            productId
            quantity
          }
          cartItemsIds
        }
      }
    `,
    });
}
function promisifyNextSubscriptionResult(observable) {
    return new Promise((resolve, reject) => {
        observable.subscribe({
            next: resolve,
            error: reject,
        });
    });
}
async function cartMutation(client, cartID, productId = faker_1.random.uuid()) {
    await client.mutate({
        variables: {
            cartId: cartID,
            productId: productId,
        },
        mutation: (0, client_1.gql) `
      mutation ChangeCartItem($cartId: ID!, $productId: ID!) {
        ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: 2 })
      }
    `,
    });
}
async function productMutation(client, productId) {
    const sku = faker_1.random.uuid();
    await client.mutate({
        variables: {
            sku: sku,
            productID: productId,
        },
        mutation: (0, client_1.gql) `
      mutation CreateProduct($sku: String!, $productID: ID) {
        CreateProduct(
          input: {
            sku: $sku
            productID: $productID
            priceInCents: 1.0
            displayName: "product"
            description: "product"
            currency: "ANY"
          }
        )
      }
    `,
    });
}
