"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const expect_1 = require("../expect");
const faker = require("faker");
const faker_1 = require("faker");
const sinon_1 = require("sinon");
const event_helper_1 = require("../helpers/event-helper");
const services_1 = require("../../src/services");
describe('the event registry', () => {
    let initialEventsCount;
    let mockTargetEvent;
    let eventRegistry;
    beforeEach(async () => {
        initialEventsCount = faker_1.random.number({ min: 2, max: 10 });
        eventRegistry = new services_1.EventRegistry();
        // Clear all events
        await eventRegistry.deleteAll();
    });
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    describe('query', () => {
        describe('with db full of random events', () => {
            beforeEach(async () => {
                const publishPromises = [];
                for (let i = 0; i < initialEventsCount; i++) {
                    publishPromises.push(eventRegistry.store((0, event_helper_1.createMockEventEnvelope)()));
                }
                await Promise.all(publishPromises);
                mockTargetEvent = (0, event_helper_1.createMockEventEnvelope)();
                await eventRegistry.store(mockTargetEvent);
            });
            it('should return expected event', async () => {
                const result = (await eventRegistry.query({
                    kind: mockTargetEvent.kind,
                    entityID: mockTargetEvent.entityID,
                    entityTypeName: mockTargetEvent.entityTypeName,
                    value: mockTargetEvent.value,
                    createdAt: mockTargetEvent.createdAt,
                    requestID: mockTargetEvent.requestID,
                    typeName: mockTargetEvent.typeName,
                    version: mockTargetEvent.version,
                }));
                (0, expect_1.expect)(result.length).to.be.equal(1);
                (0, expect_1.expect)(result[0]).to.deep.include(mockTargetEvent);
            });
        });
        describe('with events of the same entity', () => {
            const entityName = faker_1.random.word();
            const entityId = faker_1.random.uuid();
            beforeEach(async () => {
                const publishPromises = [];
                for (let i = 0; i < initialEventsCount; i++) {
                    publishPromises.push(eventRegistry.store((0, event_helper_1.createMockEventEnvelopeForEntity)(entityName, entityId)));
                }
                for (let i = 0; i < initialEventsCount; i++) {
                    publishPromises.push(eventRegistry.store((0, event_helper_1.createMockEventEnvelopeForEntity)(entityName, faker_1.random.uuid())));
                }
                for (let i = 0; i < initialEventsCount; i++) {
                    publishPromises.push(eventRegistry.store((0, event_helper_1.createMockEventEnvelope)()));
                }
                await Promise.all(publishPromises);
            });
            it('should return expected events of the same id sorted', async () => {
                const result = (await eventRegistry.query({
                    kind: 'event',
                    entityID: entityId,
                    entityTypeName: entityName,
                }));
                (0, expect_1.expect)(result.length).to.be.equal(initialEventsCount);
                (0, expect_1.expect)(result[0].entityID).to.be.equal(entityId);
                (0, expect_1.expect)(result[0].entityTypeName).to.be.equal(entityName);
                (0, expect_1.expect)(new Date(result[0].createdAt)).to.be.lessThan(new Date(result[result.length - 1].createdAt));
            });
        });
    });
    describe('query latest entity snapshot', () => {
        let mockTargetSnapshot;
        let copyOfMockTargetSnapshot;
        let newerMockDate;
        beforeEach(async () => {
            mockTargetSnapshot = (0, event_helper_1.createMockEntitySnapshotEnvelope)();
            await eventRegistry.store(mockTargetSnapshot);
            newerMockDate = faker_1.date.recent().toISOString();
            copyOfMockTargetSnapshot = {
                ...mockTargetSnapshot,
                snapshottedEventCreatedAt: newerMockDate,
            };
            await eventRegistry.store(copyOfMockTargetSnapshot);
        });
        it('should return latest item', async () => {
            const result = await eventRegistry.queryLatestSnapshot({
                entityID: mockTargetSnapshot.entityID,
                entityTypeName: mockTargetSnapshot.entityTypeName,
            });
            (0, expect_1.expect)(result).not.to.be.undefined;
            // eslint-disable-next-line @typescript-eslint/no-unused-vars
            const { _id, ...rest } = result;
            (0, expect_1.expect)(rest).to.deep.equal(copyOfMockTargetSnapshot);
        });
        it('should return null', async () => {
            const result = await eventRegistry.queryLatestSnapshot({
                entityID: faker_1.random.uuid(),
                entityTypeName: mockTargetSnapshot.entityTypeName,
            });
            (0, expect_1.expect)(result).to.be.undefined;
        });
    });
    describe('delete all', () => {
        beforeEach(async () => {
            const mockEvent = (0, event_helper_1.createMockEventEnvelope)();
            await eventRegistry.store(mockEvent);
        });
        it('should clear all events', async () => {
            const numberOfDeletedEvents = await eventRegistry.deleteAll();
            (0, expect_1.expect)(numberOfDeletedEvents).to.be.equal(1);
            (0, expect_1.expect)(await eventRegistry.query({})).to.be.deep.equal([]);
        });
    });
    describe('the publish method', () => {
        it('should insert events into the events database', async () => {
            const mockEvent = (0, event_helper_1.createMockEventEnvelope)();
            eventRegistry.events.insertAsync = (0, sinon_1.stub)().returns(mockEvent);
            await eventRegistry.store(mockEvent);
            return (0, expect_1.expect)(eventRegistry.events.insertAsync).to.have.been.called;
        });
        it('should throw if the database `insert` fails', async () => {
            const event = {
                kind: 'event',
                superKind: 'domain',
                entityID: faker.random.uuid(),
                entityTypeName: faker.random.word(),
                value: {
                    id: faker.random.uuid(),
                },
                createdAt: faker.date.past().toISOString(),
                requestID: faker.random.uuid(),
                typeName: faker.random.word(),
                version: faker.random.number(),
            };
            const error = new Error(faker.random.words());
            eventRegistry.events.insertAsync = (0, sinon_1.stub)().throws(error);
            await (0, expect_1.expect)(eventRegistry.store(event)).to.be.rejectedWith(error);
        });
    });
});
