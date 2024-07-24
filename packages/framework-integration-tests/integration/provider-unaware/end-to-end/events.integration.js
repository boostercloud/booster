"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const client_1 = require("@apollo/client");
const faker_1 = require("faker");
const expect_1 = require("../../helper/expect");
const sleep_1 = require("../../helper/sleep");
const setup_1 = require("./setup");
const framework_common_helpers_1 = require("@boostercloud/framework-common-helpers");
describe('Events end-to-end tests', () => {
    let anonymousClient;
    let loggedClient;
    let knowledgeableClient;
    let expiredClient;
    let beforeClient;
    let expiredAndBeforeClient;
    before(async () => {
        anonymousClient = setup_1.applicationUnderTest.graphql.client();
        const userEmail = faker_1.internet.email();
        const userToken = setup_1.applicationUnderTest.token.forUser(userEmail, 'UserWithEmail');
        loggedClient = setup_1.applicationUnderTest.graphql.client(userToken);
        const tokenWithTheMagicWord = setup_1.applicationUnderTest.token.forUser(userEmail, undefined, {
            customClaims: {
                magicWord: 'opensesame',
            },
        });
        knowledgeableClient = setup_1.applicationUnderTest.graphql.client(tokenWithTheMagicWord);
        const expiredToken = setup_1.applicationUnderTest.token.forUser(userEmail, 'UserWithEmail', { expiresIn: 0 });
        expiredClient = setup_1.applicationUnderTest.graphql.client(expiredToken);
        const notBefore = Math.floor(Date.now() / 1000) + 999999;
        const beforeToken = setup_1.applicationUnderTest.token.forUser(userEmail, 'UserWithEmail', { notBefore });
        beforeClient = setup_1.applicationUnderTest.graphql.client(beforeToken);
        const expiredAndBeforeToken = setup_1.applicationUnderTest.token.forUser(userEmail, 'UserWithEmail', {
            expiresIn: 0,
            notBefore,
        });
        expiredAndBeforeClient = setup_1.applicationUnderTest.graphql.client(expiredAndBeforeToken);
    });
    describe('Query events', () => {
        describe('the authorization mechanism', () => {
            context('when querying events by type', () => {
                context('with a non-authenticated user', () => {
                    it('can not read events belonging to an entity with no authorization declaration', async () => {
                        await (0, expect_1.expect)(queryByType(anonymousClient, 'OrderCreated')).to.eventually.be.rejectedWith(/Access denied for this resource/);
                    });
                    it('can not read events belonging to an entity authorized to some roles', async () => {
                        await (0, expect_1.expect)(queryByType(anonymousClient, 'ProductUpdated')).to.eventually.be.rejectedWith(/Access denied for this resource/);
                    });
                    it('can read events belonging to an entity authorized for "all"', async () => {
                        await (0, expect_1.expect)(queryByType(anonymousClient, 'CartItemChanged')).to.eventually.be.fulfilled;
                    });
                    it('can not read events from an entity with a custom authorizer', async () => {
                        await (0, expect_1.expect)(queryByType(anonymousClient, 'StockMoved')).to.eventually.be.rejectedWith(/You don't know the magic word/);
                    });
                });
                context('with an expired or not before token', () => {
                    it('can not read events belonging to an entity with an expired token', async () => {
                        await (0, expect_1.expect)(queryByType(expiredClient, 'CartItemChanged'))
                            .to.eventually.be.rejected.and.be.an.instanceOf(Error)
                            .and.have.property('graphQLErrors')
                            .and.have.to.be.deep.equal([
                            {
                                message: 'TokenExpiredError: jwt expired\nTokenExpiredError: jwt expired',
                                extensions: { code: 'BoosterTokenExpiredError' },
                            },
                        ]);
                    });
                    it('can not read events belonging to an entity with a token not before', async () => {
                        await (0, expect_1.expect)(queryByType(beforeClient, 'CartItemChanged'))
                            .to.eventually.be.rejected.and.be.an.instanceOf(Error)
                            .and.have.property('graphQLErrors')
                            .and.have.to.be.deep.equal([
                            {
                                message: 'NotBeforeError: jwt not active\nNotBeforeError: jwt not active',
                                extensions: { code: 'BoosterTokenNotBeforeError' },
                            },
                        ]);
                    });
                    // jwt.verify check NotBefore before Expired. If we have a token NotBefore and Expired we will get a BoosterTokenExpiredError error
                    it('return BoosterTokenNotBeforeError with a token expired and not before', async () => {
                        await (0, expect_1.expect)(queryByType(expiredAndBeforeClient, 'CartItemChanged'))
                            .to.eventually.be.rejected.and.be.an.instanceOf(Error)
                            .and.have.property('graphQLErrors')
                            .and.have.to.be.deep.equal([
                            {
                                message: 'NotBeforeError: jwt not active\nNotBeforeError: jwt not active',
                                extensions: { code: 'BoosterTokenNotBeforeError' },
                            },
                        ]);
                    });
                });
                context('with an authenticated user', () => {
                    it('can not read events belonging to an entity with no authorization declaration', async () => {
                        await (0, expect_1.expect)(queryByType(loggedClient, 'OrderCreated')).to.eventually.be.rejectedWith(/Access denied for this resource/);
                    });
                    it('can not read events belonging to an entity authorized to other role', async () => {
                        await (0, expect_1.expect)(queryByType(loggedClient, 'CartPaid')).to.eventually.be.rejectedWith(/Access denied for this resource/);
                    });
                    it('can read events belonging to an entity authorized for "all"', async () => {
                        await (0, expect_1.expect)(queryByType(loggedClient, 'CartItemChanged')).to.eventually.be.fulfilled;
                    });
                    it('can read events belonging to an entity authorized for their role', async () => {
                        await (0, expect_1.expect)(queryByType(loggedClient, 'ProductUpdated')).to.eventually.be.fulfilled;
                    });
                    it('can not read events from an entity with a custom authorizer', async () => {
                        await (0, expect_1.expect)(queryByType(loggedClient, 'StockMoved')).to.eventually.be.rejectedWith(/You don't know the magic word/);
                    });
                });
                context('with a custom authorizer', () => {
                    it('can not read events belonging to an entity with no authorization declaration', async () => {
                        await (0, expect_1.expect)(queryByType(knowledgeableClient, 'OrderCreated')).to.eventually.be.rejectedWith(/Access denied for this resource/);
                    }).timeout(10000);
                    it('can not read events belonging to an entity authorized to a role', async () => {
                        await (0, expect_1.expect)(queryByType(knowledgeableClient, 'CartPaid')).to.eventually.be.rejectedWith(/Access denied for this resource/);
                    });
                    it('can read events belonging to an entity authorized for "all"', async () => {
                        await (0, expect_1.expect)(queryByType(knowledgeableClient, 'CartItemChanged')).to.eventually.be.fulfilled;
                    });
                    it('can read events belonging to an entity that satisfies the custom authorizer', async () => {
                        await (0, expect_1.expect)(queryByType(knowledgeableClient, 'StockMoved')).to.eventually.be.fulfilled;
                    });
                });
            });
            context('when querying events by entity', () => {
                context('with a non-authenticated user', () => {
                    it('can not read events belonging to an entity with no authorization declaration', async () => {
                        await (0, expect_1.expect)(queryByEntity(anonymousClient, 'Order')).to.eventually.be.rejectedWith(/Access denied for this resource/);
                    });
                    it('can not read events belonging to an entity authorized to some roles', async () => {
                        await (0, expect_1.expect)(queryByEntity(anonymousClient, 'Product')).to.eventually.be.rejectedWith(/Access denied for this resource/);
                    });
                    it('can read events belonging to an entity authorized for "all"', async () => {
                        await (0, expect_1.expect)(queryByEntity(anonymousClient, 'Cart')).to.eventually.be.fulfilled;
                    });
                    it('can not read events from an entity with a custom authorizer', async () => {
                        await (0, expect_1.expect)(queryByEntity(anonymousClient, 'Stock')).to.eventually.be.rejectedWith(/You don't know the magic word/);
                    });
                });
                context('with an authenticated user', () => {
                    it('can not read events belonging to an entity with no authorization declaration', async () => {
                        await (0, expect_1.expect)(queryByEntity(loggedClient, 'Order')).to.eventually.be.rejectedWith(/Access denied for this resource/);
                    });
                    it('can not read events belonging to an entity authorized to other role', async () => {
                        await (0, expect_1.expect)(queryByEntity(loggedClient, 'Payment')).to.eventually.be.rejectedWith(/Access denied for this resource/);
                    });
                    it('can read events belonging to an entity authorized for "all"', async () => {
                        await (0, expect_1.expect)(queryByEntity(loggedClient, 'Cart')).to.eventually.be.fulfilled;
                    });
                    it('can read events belonging to an entity authorized for their role', async () => {
                        await (0, expect_1.expect)(queryByEntity(loggedClient, 'Product')).to.eventually.be.fulfilled;
                    });
                    it('can not read events from an entity with a custom authorizer', async () => {
                        await (0, expect_1.expect)(queryByEntity(loggedClient, 'Stock')).to.eventually.be.rejectedWith(/You don't know the magic word/);
                    });
                });
                context('with a custom authorizer', () => {
                    it('can not read events belonging to an entity with no authorization declaration', async () => {
                        await (0, expect_1.expect)(queryByEntity(knowledgeableClient, 'Order')).to.eventually.be.rejectedWith(/Access denied for this resource/);
                    });
                    it('can not read events belonging to an entity authorized to a role', async () => {
                        await (0, expect_1.expect)(queryByEntity(knowledgeableClient, 'Payment')).to.eventually.be.rejectedWith(/Access denied for this resource/);
                    });
                    it('can read events belonging to an entity authorized for "all"', async () => {
                        await (0, expect_1.expect)(queryByEntity(knowledgeableClient, 'Cart')).to.eventually.be.fulfilled;
                    });
                    it('can read events belonging to an entity that satisfies the custom authorizer', async () => {
                        await (0, expect_1.expect)(queryByEntity(knowledgeableClient, 'Stock')).to.eventually.be.fulfilled;
                    });
                });
            });
        });
        describe('the result of the queries', () => {
            let mockCartId;
            let mockProductId;
            let mockQuantity;
            const numberOfProvisionedEvents = 10;
            let eventsProvisionedStartedAt;
            let eventsProvisionedFinishedAt;
            beforeEach(async () => {
                mockCartId = faker_1.random.uuid();
                mockProductId = faker_1.random.uuid();
                mockQuantity = faker_1.random.number({ min: 1 });
                // Provision 10 events leaving like 100ms of time between them to have a clear time order.
                eventsProvisionedStartedAt = new Date();
                for (let i = 0; i < numberOfProvisionedEvents; i++) {
                    await anonymousClient.mutate({
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
                    await (0, sleep_1.sleep)(100);
                }
                eventsProvisionedFinishedAt = new Date();
            });
            context('when doing a query by entity', () => {
                context('with no time filters', () => {
                    it('returns the expected events in the right order', async () => {
                        const result = await queryByEntity(anonymousClient, 'Cart');
                        // As we are querying by just Entity, we will get the events we provisioned plus others (possible MANY others)
                        // with different IDs. The only things we can check are:
                        // - That, at least, we have "numberOfProvisionedEvents" events
                        // - The structure is the expected one
                        // - They came sorted by "createdAt" in descending order.
                        const events = result.data['eventsByEntity'].slice(0, numberOfProvisionedEvents);
                        (0, expect_1.expect)(events.length).to.be.equal(numberOfProvisionedEvents);
                        checkOrderAndStructureOfEvents(events);
                    });
                });
                context('with time filters', () => {
                    it('returns the expected events in the right order', async () => {
                        // Fetch events with a time filter that goes from 1 second before the provisioning started to 1 seconds
                        // after the provisioning finished. This way we will get, at least, all the events that were provisioned,
                        // and that's that we will check
                        //
                        // The "one second before and after" is to leave enough room for possible misalignment between the
                        // clocks (remember we are using the ISO format, so we can forget about timezones and all that complicated stuff)
                        const from = new Date(eventsProvisionedStartedAt);
                        from.setSeconds(from.getSeconds() - 1);
                        const to = new Date(eventsProvisionedFinishedAt);
                        to.setSeconds(to.getSeconds() + 1);
                        const result = await queryByEntity(anonymousClient, 'Cart', {
                            from: from.toISOString(),
                            to: to.toISOString(),
                        });
                        const events = result.data['eventsByEntity'];
                        // First check the order and structure
                        checkOrderAndStructureOfEvents(events);
                        // Now check if the time filter worked well
                        let provisionedEventsFound = 0;
                        for (const event of events) {
                            if (event.entityID === mockCartId) {
                                provisionedEventsFound++;
                            }
                        }
                        (0, expect_1.expect)(provisionedEventsFound).to.be.gte(numberOfProvisionedEvents);
                    });
                });
            });
            context('when doing a query by entity and entityID', () => {
                context('with no time filters', () => {
                    it('returns the expected events in the right order', async () => {
                        const result = await queryByEntity(anonymousClient, 'Cart', undefined, mockCartId);
                        const events = result.data['eventsByEntity'];
                        // As now the query included the entityId, we can be sure that ONLY the provisioned events were returned
                        (0, expect_1.expect)(events.length).to.be.equal(numberOfProvisionedEvents);
                        checkOrderAndStructureOfEvents(events);
                        for (const event of events) {
                            (0, expect_1.expect)(event.type).to.be.equal('CartItemChanged');
                            (0, expect_1.expect)(event.entityID).to.be.equal(mockCartId);
                            const value = event.value;
                            (0, expect_1.expect)(value.productId).to.be.equal(mockProductId);
                            (0, expect_1.expect)(value.quantity).to.be.equal(mockQuantity);
                        }
                    });
                });
                context('with limit', () => {
                    it('returns the expected events in the right order', async () => {
                        const limit = 3;
                        const result = await queryByEntity(anonymousClient, 'Cart', undefined, mockCartId, limit);
                        const events = result.data['eventsByEntity'];
                        // As now the query included the entityId, we can be sure that ONLY the provisioned events were returned
                        (0, expect_1.expect)(events.length).to.be.equal(limit);
                        checkOrderAndStructureOfEvents(events);
                        for (const event of events) {
                            (0, expect_1.expect)(event.type).to.be.equal('CartItemChanged');
                            (0, expect_1.expect)(event.entityID).to.be.equal(mockCartId);
                            const value = event.value;
                            (0, expect_1.expect)(value.productId).to.be.equal(mockProductId);
                            (0, expect_1.expect)(value.quantity).to.be.equal(mockQuantity);
                        }
                    });
                });
                context('with time filters', () => {
                    it('returns the expected events in the right order', async () => {
                        // Let's use a time filter that tries to get half of the events we provisioned. We can't be sure we will get
                        // exactly half of them, because possible clock differences, but we will check using inequalities
                        const from = new Date(eventsProvisionedStartedAt);
                        from.setSeconds(from.getSeconds() - 1);
                        const halfTheDuration = (eventsProvisionedFinishedAt.getTime() - eventsProvisionedStartedAt.getTime()) / 2;
                        const to = new Date(eventsProvisionedStartedAt.getTime() + halfTheDuration);
                        const result = await queryByEntity(anonymousClient, 'Cart', {
                            from: from.toISOString(),
                            to: to.toISOString(),
                        }, mockCartId);
                        const events = result.data['eventsByEntity'];
                        // First check the order and structure
                        checkOrderAndStructureOfEvents(events);
                        // Now we check that we have received more than 0 events and less than number we provisioned, as time filters
                        // we used should have given us less events than what we provisioned
                        (0, expect_1.expect)(events.length).to.be.within(1, numberOfProvisionedEvents - 1);
                    });
                    it('returns the expected events in the right order if we include limit and time filters and the "to" is reached before the limit', async () => {
                        // Let's use a time filter that tries to get half of the events we provisioned. We can't be sure we will get
                        // exactly half of them, because possible clock differences, but we will check using inequalities
                        const from = new Date(eventsProvisionedStartedAt);
                        from.setSeconds(from.getSeconds() - 1);
                        const halfTheDuration = (eventsProvisionedFinishedAt.getTime() - eventsProvisionedStartedAt.getTime()) / 2;
                        const to = new Date(eventsProvisionedStartedAt.getTime() + halfTheDuration);
                        const result = await queryByEntity(anonymousClient, 'Cart', {
                            from: from.toISOString(),
                            to: to.toISOString(),
                        }, mockCartId, numberOfProvisionedEvents * 2);
                        const events = result.data['eventsByEntity'];
                        // First check the order and structure
                        checkOrderAndStructureOfEvents(events);
                        // Now we check that we have received more than 0 events and less than number we provisioned, as time filters
                        // we used should have given us less events than what we provisioned
                        (0, expect_1.expect)(events.length).to.be.within(1, numberOfProvisionedEvents - 1);
                    });
                });
            });
            context('when doing a query by type', () => {
                context('with no time filters', () => {
                    it('returns the expected events in the right order', async () => {
                        const result = await queryByType(anonymousClient, 'CartItemChanged');
                        // As we are querying by just event type, we will get the events we provisioned plus others (possible MANY others)
                        // with different IDs. The only things we can check are:
                        // - That, at least, we have "numberOfProvisionedEvents" events
                        // - The structure is the expected one
                        // - They came sorted by "createdAt" in descenting order.
                        const events = result.data['eventsByType'].slice(0, numberOfProvisionedEvents);
                        (0, expect_1.expect)(events.length).to.be.equal(numberOfProvisionedEvents);
                        checkOrderAndStructureOfEvents(events);
                    });
                });
                context('with time filters', () => {
                    it('returns the expected events in the right order', async () => {
                        // The structure and the reasons of why this test is this way are exactly the same as described in tests:
                        // 'when doing a query by type'.'with time filters'.'returns the expected events in the right order'
                        const from = new Date(eventsProvisionedStartedAt);
                        from.setSeconds(from.getSeconds() - 1);
                        const to = new Date(eventsProvisionedFinishedAt);
                        to.setSeconds(to.getSeconds() + 1);
                        const result = await queryByType(anonymousClient, 'CartItemChanged', {
                            from: from.toISOString(),
                            to: to.toISOString(),
                        });
                        const events = result.data['eventsByType'];
                        // First check the order and structure
                        checkOrderAndStructureOfEvents(events);
                        // Now check if the time filter worked well
                        let provisionedEventsFound = 0;
                        for (const event of events) {
                            if (event.entityID === mockCartId) {
                                provisionedEventsFound++;
                            }
                        }
                        (0, expect_1.expect)(provisionedEventsFound).to.be.gte(numberOfProvisionedEvents);
                    });
                });
            });
        });
        describe('the result of the queries involving many events', () => {
            let mockCartId;
            const numberOfProvisionedEvents = 50;
            beforeEach(async () => {
                const mutationPromises = [];
                mockCartId = faker_1.random.uuid();
                for (let i = 0; i < numberOfProvisionedEvents; i++) {
                    mutationPromises.push(anonymousClient.mutate({
                        variables: {
                            cartId: mockCartId,
                            productId: faker_1.random.uuid(),
                            quantity: 1,
                        },
                        mutation: (0, client_1.gql) `
                mutation ChangeCartItem($cartId: ID!, $productId: ID!, $quantity: Float!) {
                  ChangeCartItem(input: { cartId: $cartId, productId: $productId, quantity: $quantity })
                }
              `,
                    }));
                }
                try {
                    await Promise.all(mutationPromises);
                }
                catch (e) {
                    console.log(JSON.stringify(e));
                    throw e;
                }
            });
            context('when doing a query that would return many (150) events', () => {
                it('returns the expected result', async () => {
                    console.log(mockCartId);
                    const result = await (0, sleep_1.waitForIt)(() => queryByEntity(anonymousClient, 'Cart', undefined, mockCartId), (result) => {
                        return result.data['eventsByEntity'].length === numberOfProvisionedEvents;
                    });
                    const events = result.data['eventsByEntity'];
                    (0, expect_1.expect)(events.length).to.be.equal(numberOfProvisionedEvents);
                    checkOrderAndStructureOfEvents(events);
                    for (const event of events) {
                        (0, expect_1.expect)(event.type).to.be.equal('CartItemChanged');
                        (0, expect_1.expect)(event.entityID).to.be.equal(mockCartId);
                    }
                });
            });
        });
    });
    describe('Query events ids', () => {
        // warn: this is a non-deterministic test as it needs an empty list of anotherCounter as we can't filter the events search
        describe('without limit', () => {
            //TODO: AWS provider doesn't support entityIds Interface so these tests are skipped for AWS
            if (process.env.TESTED_PROVIDER === 'AWS') {
                console.log('****************** Warning **********************');
                console.log('AWS provider does not support entityIds Interface so these tests are skipped for AWS');
                console.log('*************************************************');
                return;
            }
            let mockCounterId;
            const mockCounterIds = [];
            let mockSameCounterId;
            const numberOfProvisionedEvents = 3;
            let mockIdentifier;
            beforeEach(async () => {
                // Provision N events with same counterId
                mockSameCounterId = faker_1.random.uuid();
                console.log(`Adding ${numberOfProvisionedEvents} events with id ${mockSameCounterId}`);
                for (let i = 0; i < numberOfProvisionedEvents; i++) {
                    await anonymousClient.mutate({
                        variables: {
                            counterId: mockSameCounterId,
                            identifier: mockSameCounterId,
                        },
                        mutation: (0, client_1.gql) `
              mutation IncrementCounter($counterId: ID!, $identifier: String!) {
                IncrementCounter(input: { counterId: $counterId, identifier: $identifier })
              }
            `,
                    });
                }
                await (0, sleep_1.waitForIt)(() => {
                    return anonymousClient.query({
                        variables: {
                            filterBy: { identifier: { eq: mockSameCounterId } },
                        },
                        query: (0, client_1.gql) `
                query ListCounterReadModels($filterBy: ListCounterReadModelFilter) {
                  ListCounterReadModels(filter: $filterBy) {
                    items {
                      id
                      identifier
                      amount
                    }
                  }
                }
              `,
                    });
                }, (result) => {
                    var _a, _b;
                    const items = (_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.ListCounterReadModels) === null || _b === void 0 ? void 0 : _b.items;
                    return (items === null || items === void 0 ? void 0 : items.length) > 0 && items[0].amount === numberOfProvisionedEvents;
                });
                // Provision N events with random counterId and same identifier
                mockIdentifier = faker_1.random.uuid();
                console.log(`Adding ${numberOfProvisionedEvents} events with identifier ${mockIdentifier}`);
                for (let i = 0; i < numberOfProvisionedEvents; i++) {
                    mockCounterId = faker_1.random.uuid();
                    mockCounterIds.push(mockCounterId);
                    await anonymousClient.mutate({
                        variables: {
                            counterId: mockCounterId,
                            identifier: mockIdentifier,
                        },
                        mutation: (0, client_1.gql) `
              mutation IncrementCounter($counterId: ID!, $identifier: String!) {
                IncrementCounter(input: { counterId: $counterId, identifier: $identifier })
              }
            `,
                    });
                }
                await (0, sleep_1.waitForIt)(() => {
                    return anonymousClient.query({
                        variables: {
                            filterBy: { identifier: { eq: mockIdentifier } },
                        },
                        query: (0, client_1.gql) `
                query ListCounterReadModels($filterBy: ListCounterReadModelFilter) {
                  ListCounterReadModels(filter: $filterBy) {
                    items {
                      id
                      identifier
                      amount
                    }
                  }
                }
              `,
                    });
                }, (result) => { var _a, _b, _c; return ((_c = (_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.ListCounterReadModels) === null || _b === void 0 ? void 0 : _b.items) === null || _c === void 0 ? void 0 : _c.length) === numberOfProvisionedEvents; });
            });
            it('Should return all elements', async () => {
                var _a;
                const result = await anonymousClient.mutate({
                    variables: {
                        entityName: 'Counter',
                        limit: 99999, // limit could not be mockSameCounterId as the test could be run several times
                    },
                    mutation: (0, client_1.gql) `
            mutation EntitiesIdsFinder($entityName: String!, $limit: Float!) {
              EntitiesIdsFinder(input: { entityName: $entityName, limit: $limit })
            }
          `,
                });
                const events = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.EntitiesIdsFinder;
                // counter with same id should be only 1
                const sameCounterIdEvents = events.items.filter((event) => event.entityID === mockSameCounterId);
                (0, expect_1.expect)(sameCounterIdEvents.length).to.be.equal(1);
                // all random counters should be returned
                const currentEntitiesIds = events.items.map((item) => item.entityID);
                (0, expect_1.expect)(currentEntitiesIds).to.include.members(mockCounterIds);
                const distinctCurrentEntitiesIds = (0, framework_common_helpers_1.unique)(events.items.map((item) => item.entityID));
                (0, expect_1.expect)(distinctCurrentEntitiesIds.length).to.be.equal(currentEntitiesIds.length);
                // There are exactly the expected number of ids
                (0, expect_1.expect)(currentEntitiesIds.length).to.be.equal(numberOfProvisionedEvents + 1);
            });
        });
        // warn: this is a non-deterministic test as it needs an empty list of anotherCounter as we can't filter the events search
        describe('paginated with limit 1', () => {
            //TODO: AWS provider doesn't support entityIds Interface so these tests are skipped for AWS
            if (process.env.TESTED_PROVIDER === 'AWS') {
                console.log('****************** Warning **********************');
                console.log('AWS provider does not support entityIds Interface so these tests are skipped for AWS');
                console.log('*************************************************');
                return;
            }
            let mockAnotherCounterId;
            const mockAnotherCounterIds = [];
            let mockSameAnotherCounterId;
            const numberOfProvisionedEvents = 3;
            let mockIdentifier;
            beforeEach(async () => {
                // Provision N events with same anotherCounterId
                mockSameAnotherCounterId = faker_1.random.uuid();
                console.log(`Adding ${numberOfProvisionedEvents} events with id ${mockSameAnotherCounterId}`);
                for (let i = 0; i < numberOfProvisionedEvents; i++) {
                    await anonymousClient.mutate({
                        variables: {
                            anotherCounterId: mockSameAnotherCounterId,
                            identifier: mockSameAnotherCounterId,
                        },
                        mutation: (0, client_1.gql) `
              mutation IncrementAnotherCounter($anotherCounterId: ID!, $identifier: String!) {
                IncrementAnotherCounter(input: { anotherCounterId: $anotherCounterId, identifier: $identifier })
              }
            `,
                    });
                }
                await (0, sleep_1.waitForIt)(() => {
                    return anonymousClient.query({
                        variables: {
                            filterBy: { identifier: { eq: mockSameAnotherCounterId } },
                        },
                        query: (0, client_1.gql) `
                query ListAnotherCounterReadModels($filterBy: ListAnotherCounterReadModelFilter) {
                  ListAnotherCounterReadModels(filter: $filterBy) {
                    items {
                      id
                      identifier
                      amount
                    }
                  }
                }
              `,
                    });
                }, (result) => {
                    var _a, _b;
                    const items = (_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.ListAnotherCounterReadModels) === null || _b === void 0 ? void 0 : _b.items;
                    return (items === null || items === void 0 ? void 0 : items.length) > 0 && items[0].amount === numberOfProvisionedEvents;
                });
                // Provision N events with random anotherCounterId and same identifier
                mockIdentifier = faker_1.random.uuid();
                console.log(`Adding ${numberOfProvisionedEvents} events with identifier ${mockIdentifier}`);
                for (let i = 0; i < numberOfProvisionedEvents; i++) {
                    mockAnotherCounterId = faker_1.random.uuid();
                    mockAnotherCounterIds.push(mockAnotherCounterId);
                    await anonymousClient.mutate({
                        variables: {
                            anotherCounterId: mockAnotherCounterId,
                            identifier: mockIdentifier,
                        },
                        mutation: (0, client_1.gql) `
              mutation IncrementAnotherCounter($anotherCounterId: ID!, $identifier: String!) {
                IncrementAnotherCounter(input: { anotherCounterId: $anotherCounterId, identifier: $identifier })
              }
            `,
                    });
                }
                await (0, sleep_1.waitForIt)(() => {
                    return anonymousClient.query({
                        variables: {
                            filterBy: { identifier: { eq: mockIdentifier } },
                        },
                        query: (0, client_1.gql) `
                query ListAnotherCounterReadModels($filterBy: ListAnotherCounterReadModelFilter) {
                  ListAnotherCounterReadModels(filter: $filterBy) {
                    items {
                      id
                      identifier
                      amount
                    }
                  }
                }
              `,
                    });
                }, (result) => { var _a, _b, _c; return ((_c = (_b = (_a = result === null || result === void 0 ? void 0 : result.data) === null || _a === void 0 ? void 0 : _a.ListAnotherCounterReadModels) === null || _b === void 0 ? void 0 : _b.items) === null || _c === void 0 ? void 0 : _c.length) === numberOfProvisionedEvents; });
            });
            it('Should return the exact number of pages', async () => {
                var _a;
                let cursor = undefined;
                let count = 9999;
                let pages = 0;
                const items = [];
                while (count != 0) {
                    const result = await anonymousClient.mutate({
                        variables: {
                            entityName: 'AnotherCounter',
                            limit: 1,
                            afterCursor: cursor,
                        },
                        mutation: (0, client_1.gql) `
              mutation EntitiesIdsFinder($entityName: String!, $limit: Float!, $afterCursor: JSON) {
                EntitiesIdsFinder(input: { entityName: $entityName, limit: $limit, afterCursor: $afterCursor })
              }
            `,
                    });
                    cursor = result.data.EntitiesIdsFinder.cursor;
                    count = result.data.EntitiesIdsFinder.count;
                    if (count !== 0) {
                        pages++;
                        items.push(...(_a = result.data.EntitiesIdsFinder) === null || _a === void 0 ? void 0 : _a.items);
                        console.log(`Pages ${pages}`);
                    }
                }
                (0, expect_1.expect)(pages).to.be.eq(numberOfProvisionedEvents + 1);
                // counter with same id should be only 1
                const sameCounterIdEvents = items.filter((event) => event.entityID == mockSameAnotherCounterId);
                (0, expect_1.expect)(sameCounterIdEvents.length).to.be.equal(1);
                // all random counters should be returned
                const currentEntitiesIds = items.map((item) => item.entityID);
                (0, expect_1.expect)(currentEntitiesIds).to.include.members(mockAnotherCounterIds);
                const distinctCurrentEntitiesIds = (0, framework_common_helpers_1.unique)(items.map((item) => item.entityID));
                (0, expect_1.expect)(distinctCurrentEntitiesIds.length).to.be.equal(currentEntitiesIds.length);
                // There are exactly the expected number of ids
                (0, expect_1.expect)(currentEntitiesIds.length).to.be.equal(numberOfProvisionedEvents + 1);
            });
        });
    });
});
function queryByType(client, type, timeFilters, limit) {
    const queryTimeFilters = timeFilters ? `, from:"${timeFilters.from}" to:"${timeFilters.to}"` : '';
    const queryLimit = limit ? `, limit:${limit}` : '';
    return client.query({
        query: (0, client_1.gql) `
      query {
        eventsByType(type: ${type}${queryTimeFilters}${queryLimit}) {
            createdAt
            entity
            entityID
            requestID
            type
            user {
                id
                roles
                username
            }
            value
        }
      }
    `,
    });
}
function queryByEntity(client, entity, timeFilters, entityID, limit) {
    const queryTimeFilters = timeFilters ? `, from:"${timeFilters.from}" to:"${timeFilters.to}"` : '';
    const queryEntityID = entityID ? `, entityID:"${entityID}"` : '';
    const queryLimit = limit ? `, limit:${limit}` : '';
    return client.query({
        query: (0, client_1.gql) `
      query {
        eventsByEntity(entity: ${entity}${queryEntityID}${queryTimeFilters}${queryLimit}) {
            createdAt
            entity
            entityID
            requestID
            type
            user {
                id
                roles
                username
            }
            value
        }
      }
    `,
    });
}
function checkOrderAndStructureOfEvents(events) {
    // First check if they are in the right order (from more recent to older)
    const eventsSorted = [...events].sort((a, b) => {
        if (a.createdAt > b.createdAt)
            return -1;
        if (a.createdAt < b.createdAt)
            return 1;
        return 0;
    });
    (0, expect_1.expect)(eventsSorted).to.be.deep.equal(events);
    // Now check if the structure and some of their fields are correct
    for (const event of events) {
        (0, expect_1.expect)(event).to.have.keys('__typename', 'createdAt', 'entity', 'entityID', 'requestID', 'type', 'user', 'value');
        (0, expect_1.expect)(event.entity).to.be.equal('Cart');
        // In this function, we can't check for specific values of type, entityID or value.productID because other tests
        // (and ScheduledCommands) could have created other events for the Cart entity with different values for those properties.
        // What we can check is only its presence:
        (0, expect_1.expect)(event.type).not.to.be.undefined;
        (0, expect_1.expect)(event.entityID).not.to.be.undefined;
        const value = event.value;
        (0, expect_1.expect)(value.cartId).not.to.be.undefined;
    }
}
