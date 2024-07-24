"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const expect_1 = require("../../helper/expect");
const client_1 = require("@apollo/client");
const faker_1 = require("faker");
const sleep_1 = require("../../helper/sleep");
const setup_1 = require("./setup");
describe('events', async () => {
    let client;
    before(async () => {
        client = setup_1.applicationUnderTest.graphql.client();
    });
    it('should be persisted when flush is call', async () => {
        var _a, _b, _c;
        const mockCartId = faker_1.random.uuid();
        const result = await (0, sleep_1.waitForIt)(() => client.mutate({
            variables: {
                cartId: mockCartId,
            },
            mutation: (0, client_1.gql) `
            mutation FlushEvents($cartId: ID!) {
              FlushEvents(input: { cartId: $cartId, previousProducts: 1, afterProducts: 3 }) {
                id
                cartItems {
                  productId
                  quantity
                }
              }
            }
          `,
        }), (result) => { var _a, _b; return ((_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.FlushEvents) != null && ((_b = result === null || result === void 0 ? void 0 : result.data) === null || _b === void 0 ? void 0 : _b.FlushEvents.length) > 0; });
        (0, expect_1.expect)(result).not.to.be.null;
        const previousProducts = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.FlushEvents[0].cartItems;
        const afterProducts = (_b = result === null || result === void 0 ? void 0 : result.data) === null || _b === void 0 ? void 0 : _b.FlushEvents[1].cartItems;
        // Events return the cart flushed the first time
        (0, expect_1.expect)(previousProducts.length).to.be.eq(1);
        // Events doesn't return the last 3 events that were not flushed
        (0, expect_1.expect)(afterProducts.length).to.be.eq(1);
        const queryResult = await (0, sleep_1.waitForIt)(() => {
            return client.query({
                variables: {
                    filter: {
                        id: { eq: mockCartId },
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
            var _a, _b, _c, _d;
            return (((_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.ListCartReadModels) === null || _b === void 0 ? void 0 : _b.items.length) === 1 &&
                ((_d = (_c = result === null || result === void 0 ? void 0 : result.data) === null || _c === void 0 ? void 0 : _c.ListCartReadModels) === null || _d === void 0 ? void 0 : _d.items[0].cartItems.length) === 4);
        });
        // After the command is executed, the register is flushed, so we will have the 4 cartItems
        (0, expect_1.expect)((_c = queryResult.data.ListCartReadModels) === null || _c === void 0 ? void 0 : _c.items[0].cartItems.length).to.be.eq(4);
    });
    it('should create an event in the event store', async () => {
        var _a;
        const eventsCount = await (0, sleep_1.waitForIt)(() => setup_1.applicationUnderTest.count.events(), (eventsCount) => eventsCount > 0);
        const mockCartId = faker_1.random.uuid();
        const mockProductId = faker_1.random.uuid();
        const mockQuantity = faker_1.random.number({ min: 1 });
        const response = await client.mutate({
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
        (0, expect_1.expect)(response).not.to.be.null;
        (0, expect_1.expect)((_a = response === null || response === void 0 ? void 0 : response.data) === null || _a === void 0 ? void 0 : _a.ChangeCartItem).to.be.true;
        // Verify number of events
        const expectedEventItemsCount = eventsCount + 1;
        await (0, sleep_1.waitForIt)(() => setup_1.applicationUnderTest.count.events(), (newEventsCount) => newEventsCount === expectedEventItemsCount);
        // Verify latest event
        const latestEvent = await setup_1.applicationUnderTest.query.events(`Cart-${mockCartId}-event`);
        (0, expect_1.expect)(latestEvent).not.to.be.null;
        (0, expect_1.expect)(latestEvent[0].entityTypeName_entityID_kind).to.be.equal(`Cart-${mockCartId}-event`);
        (0, expect_1.expect)(latestEvent[0].value.productId).to.be.equal(mockProductId);
        (0, expect_1.expect)(latestEvent[0].value.cartId).to.be.equal(mockCartId);
        (0, expect_1.expect)(latestEvent[0].value.quantity).to.be.equal(mockQuantity);
        (0, expect_1.expect)(latestEvent[0].kind).to.be.equal('event');
        (0, expect_1.expect)(latestEvent[0].entityTypeName).to.be.equal('Cart');
        (0, expect_1.expect)(latestEvent[0].typeName).to.be.equal('CartItemChanged');
    });
    it('should create multiple events in the event store in batches', async () => {
        var _a;
        const eventsCount = await (0, sleep_1.waitForIt)(() => setup_1.applicationUnderTest.count.events(), (eventsCount) => eventsCount > 0);
        const mockCartId = faker_1.random.uuid();
        const eventsToCreate = 30; // Using 30 because batches are of 25, making sure the batching gets triggered
        const response = await client.mutate({
            variables: {
                cartId: mockCartId,
                itemsCount: eventsToCreate,
            },
            mutation: (0, client_1.gql) `
        mutation ChangeMultipleCartItems($cartId: ID!, $itemsCount: Float!) {
          ChangeMultipleCartItems(input: { cartId: $cartId, itemsCount: $itemsCount })
        }
      `,
        });
        (0, expect_1.expect)(response).not.to.be.null;
        (0, expect_1.expect)((_a = response === null || response === void 0 ? void 0 : response.data) === null || _a === void 0 ? void 0 : _a.ChangeMultipleCartItems).to.be.true;
        // Verify number of events
        const expectedEventItemsCount = eventsCount + eventsToCreate;
        await (0, sleep_1.waitForIt)(() => setup_1.applicationUnderTest.count.events(), (newEventsCount) => {
            console.log('Current count', newEventsCount, '\nExpected', expectedEventItemsCount);
            return newEventsCount >= expectedEventItemsCount;
        });
        // Verify latest events
        const latestEvents = await setup_1.applicationUnderTest.query.events(`Cart-${mockCartId}-event`);
        const eventProductIds = [];
        (0, expect_1.expect)(latestEvents).not.to.be.null;
        for (const event of latestEvents) {
            (0, expect_1.expect)(event.entityTypeName_entityID_kind).to.be.equal(`Cart-${mockCartId}-event`);
            const productIdNumber = parseInt(event.value.productId);
            (0, expect_1.expect)(productIdNumber).to.be.gte(0);
            (0, expect_1.expect)(productIdNumber).to.be.lessThan(eventsToCreate);
            (0, expect_1.expect)(event.value.cartId).to.be.equal(mockCartId);
            (0, expect_1.expect)(event.value.quantity).to.be.equal(1);
            (0, expect_1.expect)(event.kind).to.be.equal('event');
            (0, expect_1.expect)(event.entityTypeName).to.be.equal('Cart');
            (0, expect_1.expect)(event.typeName).to.be.equal('CartItemChanged');
            eventProductIds.push(productIdNumber);
        }
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const expectEvents = (0, expect_1.expect)(eventProductIds);
        expectEvents.to.be.sorted((prev, next) => prev > next);
    });
    it('should create multiple events in the event store in multiple batches', async () => {
        var _a;
        const eventsCount = await (0, sleep_1.waitForIt)(() => setup_1.applicationUnderTest.count.events(), (eventsCount) => eventsCount > 0);
        const firstMockCartId = faker_1.random.uuid();
        const secondMockCartId = faker_1.random.uuid();
        const firstEventsToCreate = 101; // Using 100 because batches are of 101, making sure the batching gets triggered
        const secondEventsToCreate = 50; // Using 50 more to make sure the batching gets triggered per primary key
        const response = await client.mutate({
            variables: {
                items: [
                    {
                        cartId: firstMockCartId,
                        itemsCount: firstEventsToCreate,
                    },
                    {
                        cartId: secondMockCartId,
                        itemsCount: secondEventsToCreate,
                    },
                ],
            },
            mutation: (0, client_1.gql) `
        mutation ChangeMultipleCartItemsWithIds($items: [JSON!]!) {
          ChangeMultipleCartItemsWithIds(input: { items: $items })
        }
      `,
        });
        (0, expect_1.expect)(response).not.to.be.null;
        (0, expect_1.expect)((_a = response === null || response === void 0 ? void 0 : response.data) === null || _a === void 0 ? void 0 : _a.ChangeMultipleCartItemsWithIds).to.be.true;
        // Verify number of events
        const expectedEventItemsCount = eventsCount + firstEventsToCreate + secondEventsToCreate;
        await (0, sleep_1.waitForIt)(() => setup_1.applicationUnderTest.count.events(), (newEventsCount) => {
            console.log('Current count', newEventsCount, '\nExpected', expectedEventItemsCount);
            return newEventsCount >= expectedEventItemsCount;
        });
        await checkLatestEvents(firstMockCartId, firstEventsToCreate);
        await checkLatestEvents(secondMockCartId, secondEventsToCreate);
    });
});
async function checkLatestEvents(cartId, eventsToCreate) {
    const primaryKey = `Cart-${cartId}-event`;
    const events = await setup_1.applicationUnderTest.query.events(primaryKey);
    (0, expect_1.expect)(events).not.to.be.null;
    const eventProductIds = [];
    for (const event of events) {
        (0, expect_1.expect)(event.entityTypeName_entityID_kind).to.be.equal(primaryKey);
        const productIdNumber = parseInt(event.value.productId);
        (0, expect_1.expect)(productIdNumber).to.be.gte(0);
        (0, expect_1.expect)(productIdNumber).to.be.lessThan(eventsToCreate);
        (0, expect_1.expect)(event.value.cartId).to.be.equal(cartId);
        (0, expect_1.expect)(event.value.quantity).to.be.equal(1);
        (0, expect_1.expect)(event.kind).to.be.equal('event');
        (0, expect_1.expect)(event.entityTypeName).to.be.equal('Cart');
        (0, expect_1.expect)(event.typeName).to.be.equal('CartItemChanged');
        eventProductIds.push(productIdNumber);
    }
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const secondExpectEvents = (0, expect_1.expect)(eventProductIds);
    secondExpectEvents.to.be.sorted((prev, next) => prev > next);
}
