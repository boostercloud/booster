"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const expect_1 = require("../../helper/expect");
const client_1 = require("@apollo/client");
const faker_1 = require("faker");
const sleep_1 = require("../../helper/sleep");
const setup_1 = require("./setup");
describe('notifications', async () => {
    let client;
    before(async () => {
        client = setup_1.applicationUnderTest.graphql.client();
    });
    it('should be persisted when flush is call', async () => {
        var _a, _b, _c, _d, _e;
        const mockCartId = faker_1.random.uuid();
        const result = await (0, sleep_1.waitForIt)(() => client.mutate({
            variables: {
                cartId: mockCartId,
            },
            mutation: (0, client_1.gql) `
            mutation FlushNotifications($cartId: ID!) {
              FlushNotifications(input: { cartId: $cartId, previousProducts: 1, afterProducts: 3 }) {
                id
                checks
              }
            }
          `,
        }), (result) => { var _a; return ((_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.FlushNotifications.length) > 0; });
        (0, expect_1.expect)(result).not.to.be.null;
        console.log(JSON.stringify(result));
        const previousChecks = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.FlushNotifications[0].checks;
        const afterChecks = (_b = result === null || result === void 0 ? void 0 : result.data) === null || _b === void 0 ? void 0 : _b.FlushNotifications[1].checks;
        // Events return the cart flushed the first time
        (0, expect_1.expect)(previousChecks, 'previous products').to.be.eq(1);
        // Events doesn't return the last 3 events that were not flushed
        (0, expect_1.expect)(afterChecks, 'after products').to.be.eq(1);
        console.log('Waiting for read model with id', mockCartId, 'to become available');
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
                  checks
                }
              }
            }
          `,
            });
        }, (result) => {
            var _a, _b, _c, _d, _e, _f, _g, _h;
            const isDefined = ((_d = (_c = (_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.ListCartReadModels) === null || _b === void 0 ? void 0 : _b.items) === null || _c === void 0 ? void 0 : _c[0]) === null || _d === void 0 ? void 0 : _d.checks) !== undefined;
            const isExpected = ((_h = (_g = (_f = (_e = result === null || result === void 0 ? void 0 : result.data) === null || _e === void 0 ? void 0 : _e.ListCartReadModels) === null || _f === void 0 ? void 0 : _f.items) === null || _g === void 0 ? void 0 : _g[0]) === null || _h === void 0 ? void 0 : _h.checks) >= 4;
            return isDefined && isExpected;
        });
        console.log('Got result', JSON.stringify(queryResult));
        // After the command is executed, the register is flushed, so we will have the 4 cartItems
        (0, expect_1.expect)((_e = (_d = (_c = queryResult.data.ListCartReadModels) === null || _c === void 0 ? void 0 : _c.items) === null || _d === void 0 ? void 0 : _d[0]) === null || _e === void 0 ? void 0 : _e.checks).to.eq(4);
    });
    it('should create an event in the event store', async () => {
        var _a;
        console.log('Waiting for events to be available');
        const eventsCount = await (0, sleep_1.waitForIt)(() => setup_1.applicationUnderTest.count.events(), (eventsCount) => eventsCount > 0);
        console.log('Events count', eventsCount);
        const mockCartId = faker_1.random.uuid();
        console.log('Abandoning cart', mockCartId);
        const response = await client.mutate({
            variables: {
                cartId: mockCartId,
            },
            mutation: (0, client_1.gql) `
        mutation AbandonCart($cartId: ID!) {
          AbandonCart(input: { cartId: $cartId })
        }
      `,
        });
        (0, expect_1.expect)(response).not.to.be.null;
        (0, expect_1.expect)((_a = response === null || response === void 0 ? void 0 : response.data) === null || _a === void 0 ? void 0 : _a.AbandonCart).to.be.true;
        // Verify number of events
        const expectedEventItemsCount = eventsCount + 1;
        console.log('Waiting for events to be available');
        await (0, sleep_1.waitForIt)(() => setup_1.applicationUnderTest.count.events(), (newEventsCount) => newEventsCount > eventsCount);
        console.log('Events count', expectedEventItemsCount);
        // Verify latest event
        const latestEvent = await setup_1.applicationUnderTest.query.events(`defaultTopic-${mockCartId}-event`);
        (0, expect_1.expect)(latestEvent).not.to.be.null;
        (0, expect_1.expect)(latestEvent).not.to.be.empty;
        (0, expect_1.expect)(latestEvent[0].entityTypeName_entityID_kind).to.be.equal(`defaultTopic-${mockCartId}-event`);
        (0, expect_1.expect)(latestEvent[0].value.something).to.be.equal(mockCartId);
        (0, expect_1.expect)(latestEvent[0].kind).to.be.equal('event');
        (0, expect_1.expect)(latestEvent[0].entityTypeName).to.be.equal('defaultTopic');
        (0, expect_1.expect)(latestEvent[0].typeName).to.be.equal('CartAbandoned');
    });
});
