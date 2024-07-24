"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/no-explicit-any,@typescript-eslint/ban-ts-comment */
const mocha_1 = require("mocha");
const framework_types_1 = require("@boostercloud/framework-types");
const sinon_1 = require("sinon");
const event_store_1 = require("../../src/services/event-store");
const expect_1 = require("../expect");
const booster_entity_migrated_1 = require("../../src/core-concepts/data-migration/events/booster-entity-migrated");
const booster_authorizer_1 = require("../../src/booster-authorizer");
(0, mocha_1.describe)('EventStore', () => {
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    const testConfig = new framework_types_1.BoosterConfig('Test');
    testConfig.logLevel = framework_types_1.Level.error;
    class AnEvent {
        constructor(id, entityId, delta) {
            this.id = id;
            this.entityId = entityId;
            this.delta = delta;
        }
        entityID() {
            return this.entityId;
        }
    }
    class AnotherEvent {
        constructor(id) {
            this.id = id;
        }
        entityID() {
            return this.id;
        }
        getPrefixedId(prefix) {
            return `${prefix}-${this.id}`;
        }
    }
    class AnEntity {
        constructor(id, count) {
            this.id = id;
            this.count = count;
        }
        getId() {
            return this.id;
        }
        static reducerThatCallsEntityMethod(event, currentEntity) {
            if (currentEntity) {
                currentEntity.getId();
            }
            return new AnEntity(event.entityId, event.delta);
        }
        // eslint-disable-next-line @typescript-eslint/no-unused-vars
        static reducerThatCallsEventMethod(event, currentEntity) {
            event.getPrefixedId('prefix');
            return new AnEntity('1', 1);
        }
    }
    const config = new framework_types_1.BoosterConfig('test');
    config.provider = {
        events: {
            storeSnapshot: () => { },
            latestEntitySnapshot: () => { },
            forEntitySince: () => { },
        },
    };
    config.entities[AnEntity.name] = {
        class: AnEntity,
        eventStreamAuthorizer: booster_authorizer_1.BoosterAuthorizer.authorizeRoles.bind(null, []),
    };
    config.reducers[AnEvent.name] = {
        class: AnEntity,
        methodName: 'reducerThatCallsEntityMethod',
    };
    config.reducers[AnotherEvent.name] = {
        class: AnEntity,
        methodName: 'reducerThatCallsEventMethod',
    };
    config.events[AnEvent.name] = { class: AnEvent };
    config.events[AnotherEvent.name] = { class: AnotherEvent };
    config.logger = {
        info: (0, sinon_1.fake)(),
        debug: (0, sinon_1.fake)(),
        error: (0, sinon_1.fake)(),
        warn: (0, sinon_1.fake)(),
    };
    const importantDateTimeStamp = new Date(2019, 11, 23, 6, 30).toISOString();
    const originOfTime = new Date(0).toISOString(); // Unix epoch
    const someEvent = {
        id: '1',
        entityID: () => '42',
        entityId: '42',
        delta: 1,
    };
    const otherEvent = {
        entityID: () => '42',
        entityId: '42',
        delta: 2,
    };
    const someEntity = {
        id: '42',
        count: 0,
    };
    function eventEnvelopeFor(event, typeName, timestamp = new Date()) {
        var _a;
        const getEntityID = (_a = event.entityID) !== null && _a !== void 0 ? _a : (() => '');
        const createdAt = timestamp.toISOString();
        return {
            version: 1,
            kind: 'event',
            superKind: 'domain',
            entityID: getEntityID(),
            entityTypeName: AnEntity.name,
            value: event,
            requestID: 'whatever',
            typeName: typeName,
            createdAt,
        };
    }
    function snapshotEnvelopeFor(entity) {
        return {
            version: 1,
            kind: 'snapshot',
            superKind: 'domain',
            entityID: entity.id,
            entityTypeName: AnEntity.name,
            value: entity,
            requestID: 'whatever',
            typeName: AnEntity.name,
            snapshottedEventCreatedAt: importantDateTimeStamp,
        };
    }
    (0, mocha_1.describe)('public methods', () => {
        (0, mocha_1.describe)('fetchEntitySnapshot', () => {
            it('properly binds `this` to the entityReducer', async () => {
                // eslint-disable-next-line @typescript-eslint/no-explicit-any
                const eventStore = new event_store_1.EventStore(config);
                const someEventEnvelope = eventEnvelopeFor(someEvent, AnEvent.name);
                (0, sinon_1.replace)(eventStore, 'loadLatestSnapshot', sinon_1.fake.resolves(null));
                (0, sinon_1.replace)(eventStore, 'loadEventStreamSince', sinon_1.fake.resolves([someEventEnvelope]));
                (0, sinon_1.replace)(eventStore, 'entityReducer', function () {
                    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
                    // @ts-ignore
                    (0, expect_1.expect)(this).to.be.equal(eventStore);
                });
                const entityName = AnEntity.name;
                const entityID = '42';
                await (0, expect_1.expect)(eventStore.fetchEntitySnapshot(entityName, entityID)).to.be.eventually.fulfilled;
            });
            context('when there is a snapshot but no pending events', () => {
                it('returns the snapshot', async () => {
                    // eslint-disable-next-line @typescript-eslint/no-explicit-any
                    const eventStore = new event_store_1.EventStore(config);
                    const someSnapshotEnvelope = snapshotEnvelopeFor(someEntity);
                    (0, sinon_1.replace)(eventStore, 'loadLatestSnapshot', sinon_1.fake.resolves(someSnapshotEnvelope));
                    (0, sinon_1.replace)(eventStore, 'loadEventStreamSince', sinon_1.fake.resolves([]));
                    (0, sinon_1.replace)(eventStore, 'entityReducer', (0, sinon_1.fake)());
                    (0, sinon_1.replace)(eventStore, 'storeSnapshot', (0, sinon_1.fake)());
                    const entityName = AnEntity.name;
                    const entityID = '42';
                    const entity = await eventStore.fetchEntitySnapshot(entityName, entityID);
                    (0, expect_1.expect)(eventStore.loadLatestSnapshot).to.have.been.calledOnceWith(entityName, entityID);
                    (0, expect_1.expect)(eventStore.loadEventStreamSince).to.have.been.calledOnceWith(entityName, entityID, importantDateTimeStamp);
                    (0, expect_1.expect)(eventStore.entityReducer).not.to.have.been.called;
                    (0, expect_1.expect)(eventStore.storeSnapshot).not.to.have.been.called;
                    (0, expect_1.expect)(entity).to.be.deep.equal(snapshotEnvelopeFor(someEntity));
                });
            });
            context('when there is a snapshot and a short list of pending events', () => {
                it('produces and returns a new snapshot, storing it', async () => {
                    // eslint-disable-next-line @typescript-eslint/no-explicit-any
                    const eventStore = new event_store_1.EventStore(config);
                    const someSnapshotEnvelope = snapshotEnvelopeFor(someEntity);
                    const someEventEnvelopePersistedAt = new Date();
                    const someEventEnvelope = eventEnvelopeFor(someEvent, AnEvent.name, someEventEnvelopePersistedAt);
                    const otherEventEnvelopePersistedAt = new Date(someEventEnvelopePersistedAt.getTime() + 100); // 100ms later
                    const otherEventEnvelope = eventEnvelopeFor(otherEvent, AnEvent.name, otherEventEnvelopePersistedAt);
                    (0, sinon_1.replace)(eventStore, 'loadLatestSnapshot', sinon_1.fake.resolves(someSnapshotEnvelope));
                    (0, sinon_1.replace)(eventStore, 'loadEventStreamSince', sinon_1.fake.resolves([someEventEnvelope, otherEventEnvelope]));
                    const reducer = (0, sinon_1.stub)()
                        .onFirstCall()
                        .returns(snapshotEnvelopeFor({
                        id: '42',
                        count: 1,
                    }))
                        .onSecondCall()
                        .returns(snapshotEnvelopeFor({
                        id: '42',
                        count: 3,
                    }));
                    (0, sinon_1.replace)(eventStore, 'entityReducer', reducer);
                    const expectedResult = {
                        ...snapshotEnvelopeFor({
                            id: '42',
                            count: 3,
                        }),
                        createdAt: importantDateTimeStamp,
                    };
                    (0, sinon_1.replace)(eventStore, 'storeSnapshot', sinon_1.fake.resolves(expectedResult));
                    const entityName = AnEntity.name;
                    const entityID = '42';
                    const entity = await eventStore.fetchEntitySnapshot(entityName, entityID);
                    (0, expect_1.expect)(eventStore.loadLatestSnapshot).to.have.been.calledOnceWith(entityName, entityID);
                    (0, expect_1.expect)(eventStore.loadEventStreamSince).to.have.been.calledOnceWith(entityName, entityID, importantDateTimeStamp);
                    (0, expect_1.expect)(eventStore.entityReducer.firstCall.args[0]).to.deep.equal(someEventEnvelope);
                    (0, expect_1.expect)(eventStore.entityReducer.firstCall.args[1]).to.deep.equal(someSnapshotEnvelope);
                    (0, expect_1.expect)(eventStore.entityReducer.secondCall.args[0]).to.deep.equal(otherEventEnvelope);
                    (0, expect_1.expect)(eventStore.entityReducer.secondCall.args[1]).to.deep.equal(snapshotEnvelopeFor({
                        id: '42',
                        count: 1,
                    }));
                    (0, expect_1.expect)(eventStore.storeSnapshot).to.have.been.called;
                    // Directly returns the value returned by storeSnapshot
                    (0, expect_1.expect)(entity).to.be.deep.equal(expectedResult);
                });
            });
            context('when there is a snapshot and a long list of pending events', () => {
                it('produces a new snapshot, stores it and returns it', async () => {
                    // eslint-disable-next-line @typescript-eslint/no-explicit-any
                    const eventStore = new event_store_1.EventStore(config);
                    const someSnapshotEnvelope = snapshotEnvelopeFor(someEntity);
                    const someEventEnvelope = eventEnvelopeFor(someEvent, AnEvent.name);
                    const otherEventEnvelope = eventEnvelopeFor(otherEvent, AnEvent.name);
                    const pendingEvents = [
                        someEventEnvelope,
                        otherEventEnvelope,
                        someEventEnvelope,
                        otherEventEnvelope,
                        someEventEnvelope,
                        otherEventEnvelope,
                    ];
                    const results = [1, 3, 4, 6, 7, 9];
                    const inputs = [someSnapshotEnvelope].concat(results.map((result) => {
                        return snapshotEnvelopeFor({
                            id: '42',
                            count: result,
                        });
                    }));
                    (0, sinon_1.replace)(eventStore, 'loadLatestSnapshot', sinon_1.fake.resolves(someSnapshotEnvelope));
                    (0, sinon_1.replace)(eventStore, 'loadEventStreamSince', sinon_1.fake.resolves(pendingEvents));
                    const reducer = (0, sinon_1.stub)();
                    results.forEach((result, index) => {
                        reducer.onCall(index).returns(snapshotEnvelopeFor({
                            id: '42',
                            count: result,
                        }));
                    });
                    (0, sinon_1.replace)(eventStore, 'entityReducer', reducer);
                    const expectedResult = {
                        ...snapshotEnvelopeFor({
                            id: '42',
                            count: 9,
                        }),
                        createdAt: importantDateTimeStamp,
                    };
                    (0, sinon_1.replace)(eventStore, 'storeSnapshot', sinon_1.fake.resolves(expectedResult));
                    const entityName = AnEntity.name;
                    const entityID = '42';
                    const entity = await eventStore.fetchEntitySnapshot(entityName, entityID);
                    (0, expect_1.expect)(eventStore.loadLatestSnapshot).to.have.been.calledOnceWith(entityName, entityID);
                    (0, expect_1.expect)(eventStore.loadEventStreamSince).to.have.been.calledOnceWith(entityName, entityID, importantDateTimeStamp);
                    for (let index = 0; index < results.length; index++) {
                        (0, expect_1.expect)(eventStore.entityReducer.getCall(index).args[0]).to.deep.equal(pendingEvents[index]);
                        (0, expect_1.expect)(eventStore.entityReducer.getCall(index).args[1]).to.deep.equal(inputs[index]);
                    }
                    (0, expect_1.expect)(eventStore.storeSnapshot).to.have.been.called;
                    (0, expect_1.expect)(entity).to.be.deep.equal(expectedResult);
                });
            });
            context('with no snapshot and a list of more than 5 events', () => {
                it('produces a new snapshot, stores it and returns it', async () => {
                    // eslint-disable-next-line @typescript-eslint/no-explicit-any
                    const eventStore = new event_store_1.EventStore(config);
                    const someEventEnvelope = eventEnvelopeFor(someEvent, AnEvent.name);
                    const otherEventEnvelope = eventEnvelopeFor(otherEvent, AnEvent.name);
                    const pendingEvents = [
                        someEventEnvelope,
                        otherEventEnvelope,
                        someEventEnvelope,
                        otherEventEnvelope,
                        someEventEnvelope,
                        otherEventEnvelope,
                    ];
                    const results = [1, 3, 4, 6, 7, 9];
                    const inputs = results.map((result) => {
                        return snapshotEnvelopeFor({
                            id: '42',
                            count: result,
                        });
                    });
                    (0, sinon_1.replace)(eventStore, 'loadLatestSnapshot', sinon_1.fake.resolves(null));
                    (0, sinon_1.replace)(eventStore, 'loadEventStreamSince', sinon_1.fake.resolves(pendingEvents));
                    const reducer = (0, sinon_1.stub)();
                    results.forEach((result, index) => {
                        reducer.onCall(index).returns(snapshotEnvelopeFor({
                            id: '42',
                            count: result,
                        }));
                    });
                    (0, sinon_1.replace)(eventStore, 'entityReducer', reducer);
                    const expectedResult = {
                        ...snapshotEnvelopeFor({
                            id: '42',
                            count: 9,
                        }),
                        createdAt: importantDateTimeStamp,
                    };
                    (0, sinon_1.replace)(eventStore, 'storeSnapshot', sinon_1.fake.resolves(expectedResult));
                    const entityName = AnEntity.name;
                    const entityID = '42';
                    const entity = await eventStore.fetchEntitySnapshot(entityName, entityID);
                    (0, expect_1.expect)(eventStore.loadLatestSnapshot).to.have.been.calledOnceWith(entityName, entityID);
                    (0, expect_1.expect)(eventStore.loadEventStreamSince).to.have.been.calledOnceWith(entityName, entityID, originOfTime);
                    (0, expect_1.expect)(eventStore.entityReducer.getCall(0).args[0]).to.deep.equal(pendingEvents[0]);
                    (0, expect_1.expect)(eventStore.entityReducer.getCall(0).args[1]).to.be.null;
                    for (let index = 1; index < results.length; index++) {
                        (0, expect_1.expect)(eventStore.entityReducer.getCall(index).args[0]).to.deep.equal(pendingEvents[index]);
                        (0, expect_1.expect)(eventStore.entityReducer.getCall(index).args[1]).to.deep.equal(inputs[index - 1]);
                    }
                    (0, expect_1.expect)(eventStore.storeSnapshot).to.have.been.called;
                    (0, expect_1.expect)(entity).to.be.deep.equal(expectedResult);
                });
            });
            context('with no snapshot and an empty list of events', () => {
                it('does nothing and returns null', async () => {
                    // eslint-disable-next-line @typescript-eslint/no-explicit-any
                    const eventStore = new event_store_1.EventStore(config);
                    (0, sinon_1.replace)(eventStore, 'loadLatestSnapshot', sinon_1.fake.resolves(null));
                    (0, sinon_1.replace)(eventStore, 'loadEventStreamSince', sinon_1.fake.resolves([]));
                    (0, sinon_1.replace)(eventStore, 'entityReducer', (0, sinon_1.fake)());
                    (0, sinon_1.replace)(eventStore, 'storeSnapshot', (0, sinon_1.fake)());
                    const entityName = AnEntity.name;
                    const entityID = '42';
                    const entity = await eventStore.fetchEntitySnapshot(entityName, entityID);
                    (0, expect_1.expect)(eventStore.loadLatestSnapshot).to.have.been.calledOnceWith(entityName, entityID);
                    (0, expect_1.expect)(eventStore.loadEventStreamSince).to.have.been.calledOnceWith(entityName, entityID, originOfTime);
                    (0, expect_1.expect)(eventStore.entityReducer).not.to.have.been.called;
                    (0, expect_1.expect)(eventStore.storeSnapshot).not.to.have.been.called;
                    (0, expect_1.expect)(entity).to.be.null;
                });
            });
            context('with a stream that contains BEM events', () => {
                it('returns the reduced snapshot including the changes from BEM events', async () => {
                    // eslint-disable-next-line @typescript-eslint/no-explicit-any
                    const eventStore = new event_store_1.EventStore(config);
                    const someSnapshotEnvelope = snapshotEnvelopeFor(someEntity);
                    (0, sinon_1.replace)(eventStore, 'loadLatestSnapshot', sinon_1.fake.resolves(someSnapshotEnvelope));
                    const fakeEntityReducer = (0, sinon_1.stub)();
                    const reducersIds = ['42', '90', '42', '91', '42', '92']; // BEM events could return a different ID
                    const reducersCount = [0, 1, 3, 4, 6, 7];
                    reducersCount.forEach((result, index) => {
                        fakeEntityReducer.onCall(index).returns(snapshotEnvelopeFor({
                            id: reducersIds[index],
                            count: result,
                        }));
                    });
                    (0, sinon_1.replace)(eventStore, 'entityReducer', fakeEntityReducer);
                    (0, sinon_1.replace)(eventStore, 'storeSnapshot', (0, sinon_1.fake)());
                    // A list of pending events for entityID = 42 and for BEM 90, 91 and 92
                    const someEventEnvelope = eventEnvelopeFor(someEvent, AnEvent.name);
                    const bemEventsEnvelopes = ['90', '91', '92'].map((id) => {
                        return eventEnvelopeFor({
                            entityID: () => '42',
                            entityId: 42,
                            delta: 2,
                            superKind: framework_types_1.BOOSTER_SUPER_KIND,
                            newEntity: {
                                id: id,
                            },
                        }, 'bemEvent');
                    });
                    const pendingEvents = [
                        someEventEnvelope,
                        bemEventsEnvelopes[0],
                        someEventEnvelope,
                        bemEventsEnvelopes[1],
                        someEventEnvelope,
                        bemEventsEnvelopes[2], // BEM event for entityID = 92
                    ];
                    (0, sinon_1.replace)(eventStore, 'loadEventStreamSince', sinon_1.fake.resolves(pendingEvents));
                    const entityName = AnEntity.name;
                    const entityID = '42';
                    await eventStore.fetchEntitySnapshot(entityName, entityID);
                    (0, expect_1.expect)(eventStore.loadLatestSnapshot).to.have.been.calledOnceWith(entityName, entityID);
                    const expectedCounts = [0, 0, 1, 3, 4, 6]; // should call with updated snapshot from entityID = 42
                    const expectedReturnedIDs = ['42', '42', '90', '42', '91', '42']; // should call with updated snapshot from entityID = 42
                    const expectedSnapshotArguments = expectedCounts.map((expectedCount, index) => {
                        return snapshotEnvelopeFor({
                            id: expectedReturnedIDs[index],
                            count: expectedCount,
                        });
                    });
                    for (let index = 0; index < reducersCount.length; index++) {
                        const expectedSnapshotArgument = expectedSnapshotArguments[index];
                        const expectedEventArgument = pendingEvents[index];
                        (0, expect_1.expect)(eventStore.entityReducer.getCall(index).args[0]).to.deep.equal(expectedEventArgument);
                        (0, expect_1.expect)(eventStore.entityReducer.getCall(index).args[1]).to.deep.equal(expectedSnapshotArgument);
                    }
                    const expectedFinalSnapshot = snapshotEnvelopeFor({
                        id: '92',
                        count: 7,
                    });
                    (0, expect_1.expect)(eventStore.storeSnapshot).to.have.been.calledOnceWith(expectedFinalSnapshot);
                });
            });
            context('when a reducer throws an exception', () => {
                it('the process for the entity is halted and a `ReducerError` exception is raised', async () => {
                    const eventStore = new event_store_1.EventStore(config);
                    const someSnapshotEnvelope = snapshotEnvelopeFor(someEntity);
                    (0, sinon_1.replace)(eventStore, 'loadLatestSnapshot', sinon_1.fake.resolves(someSnapshotEnvelope));
                    const reducer = (0, sinon_1.stub)();
                    const reducersIds = ['42', '90', '42', '91', '42', '92']; // BEM events could return a different ID
                    for (let index = 0; index < 6; index++) {
                        if (index === 2) {
                            reducer.onCall(index).rejects(new Error('Error on reducer'));
                        }
                        else {
                            reducer.onCall(index).returns(snapshotEnvelopeFor({
                                id: reducersIds[index],
                                count: index + 1,
                            }));
                        }
                    }
                    (0, sinon_1.replace)(eventStore, 'entityReducer', reducer);
                    (0, sinon_1.replace)(eventStore, 'storeSnapshot', (0, sinon_1.fake)());
                    // A list of pending events for entityID = 42 and for BEM 90, 91 and 92
                    const someEventEnvelopes = [1, 3, 5].map((delta) => eventEnvelopeFor({ ...someEvent, delta }, AnEvent.name));
                    const bemEventsEnvelopes = ['90', '91', '92'].map((id, index) => {
                        return eventEnvelopeFor({
                            entityID: () => '42',
                            entityId: 42,
                            delta: 2 * (index + 1),
                            superKind: framework_types_1.BOOSTER_SUPER_KIND,
                            newEntity: {
                                id: id,
                            },
                        }, 'bemEvent');
                    });
                    const pendingEvents = [
                        someEventEnvelopes[0],
                        bemEventsEnvelopes[0],
                        someEventEnvelopes[1],
                        bemEventsEnvelopes[1],
                        someEventEnvelopes[2],
                        bemEventsEnvelopes[2],
                    ];
                    (0, sinon_1.replace)(eventStore, 'loadEventStreamSince', sinon_1.fake.resolves(pendingEvents));
                    const entityName = AnEntity.name;
                    const entityID = '42';
                    await (0, expect_1.expect)(eventStore.fetchEntitySnapshot(entityName, entityID)).to.eventually.be.rejectedWith('Error on reducer');
                    (0, expect_1.expect)(eventStore.entityReducer).to.have.been.calledThrice;
                    (0, expect_1.expect)(eventStore.entityReducer).to.have.been.calledWith(pendingEvents[0], someSnapshotEnvelope);
                    (0, expect_1.expect)(eventStore.entityReducer).to.have.been.calledWith(pendingEvents[1], sinon_1.match.any);
                    (0, expect_1.expect)(eventStore.entityReducer).to.have.been.calledWith(pendingEvents[2], sinon_1.match.any);
                    (0, expect_1.expect)(eventStore.entityReducer).not.to.have.been.calledWith(pendingEvents[3], sinon_1.match.any);
                    (0, expect_1.expect)(eventStore.entityReducer).not.to.have.been.calledWith(pendingEvents[4], sinon_1.match.any);
                    (0, expect_1.expect)(eventStore.entityReducer).not.to.have.been.calledWith(pendingEvents[5], sinon_1.match.any);
                    (0, expect_1.expect)(eventStore.storeSnapshot).not.to.have.been.called;
                });
            });
            context('when persisting the entity fails', () => {
                it('does not throw an exception and returns undefined', async () => {
                    //eslint-disable-next-line @typescript-eslint/no-explicit-any
                    const eventStore = new event_store_1.EventStore(config);
                    const someSnapshotEnvelope = snapshotEnvelopeFor(someEntity);
                    (0, sinon_1.replace)(eventStore, 'loadLatestSnapshot', sinon_1.fake.resolves(someSnapshotEnvelope));
                    const reducer = (0, sinon_1.stub)();
                    const reducersReturnIds = ['42', '90', '42', '91', '42', '92']; // BEM events could return a different ID
                    const reducersReturnCount = [1, 2, 3, 4, 5, 6];
                    reducersReturnCount.forEach((result, index) => {
                        reducer.onCall(index).returns(snapshotEnvelopeFor({
                            id: reducersReturnIds[index],
                            count: result,
                        }));
                    });
                    (0, sinon_1.replace)(eventStore, 'entityReducer', reducer);
                    (0, sinon_1.spy)(eventStore, 'storeSnapshot');
                    (0, sinon_1.replace)(config.provider.events, 'storeSnapshot', sinon_1.fake.rejects(new Error('Error on persist')));
                    // A list of pending events for entityID = 42 and for BEM 90, 91 and 92
                    const someEventEnvelope = eventEnvelopeFor(someEvent, AnEvent.name);
                    const bemEventsEnvelopes = ['90', '91', '92'].map((id) => {
                        return eventEnvelopeFor({
                            entityID: () => '42',
                            entityId: 42,
                            delta: 2,
                            superKind: framework_types_1.BOOSTER_SUPER_KIND,
                            newEntity: {
                                id: id,
                            },
                        }, 'bemEvent');
                    });
                    const pendingEvents = [
                        someEventEnvelope,
                        bemEventsEnvelopes[0],
                        someEventEnvelope,
                        bemEventsEnvelopes[1],
                        someEventEnvelope,
                        bemEventsEnvelopes[2],
                    ];
                    (0, sinon_1.replace)(eventStore, 'loadEventStreamSince', sinon_1.fake.resolves(pendingEvents));
                    const entityName = AnEntity.name;
                    const entityID = '42';
                    const entity = await eventStore.fetchEntitySnapshot(entityName, entityID);
                    (0, expect_1.expect)(eventStore.loadLatestSnapshot).to.have.been.calledOnceWith(entityName, entityID);
                    const expectedSnapshotArguments = [someSnapshotEnvelope].concat(reducersReturnCount.map((expectedCount, index) => {
                        return snapshotEnvelopeFor({
                            id: reducersReturnIds[index],
                            count: expectedCount,
                        });
                    }));
                    for (let index = 0; index < reducersReturnCount.length; index++) {
                        (0, expect_1.expect)(eventStore.entityReducer.getCall(index).args[0]).to.deep.equal(pendingEvents[index]);
                        (0, expect_1.expect)(eventStore.entityReducer.getCall(index).args[1]).to.deep.equal(expectedSnapshotArguments[index]);
                    }
                    (0, expect_1.expect)(eventStore.storeSnapshot).to.have.been.calledOnce;
                    (0, expect_1.expect)(config.provider.events.storeSnapshot).to.have.been.calledOnce;
                    (0, expect_1.expect)(entity).to.be.undefined;
                });
            });
            // This could potentially happen if two or more processes are reading the same entity stream at the same time
            context('when there is a snapshot in the middle of the stream', () => {
                it('ignores the snapshot and continues processing the stream', async () => {
                    // eslint-disable-next-line @typescript-eslint/no-explicit-any
                    const eventStore = new event_store_1.EventStore(config);
                    // It doesn't initially see the snapshot
                    (0, sinon_1.replace)(eventStore, 'loadLatestSnapshot', sinon_1.fake.resolves(null));
                    const someSnapshotEnvelope = snapshotEnvelopeFor(someEntity);
                    const someEventEnvelope = eventEnvelopeFor(someEvent, AnEvent.name);
                    const otherEventEnvelope = eventEnvelopeFor(otherEvent, AnEvent.name);
                    const pendingEvents = [
                        someEventEnvelope,
                        otherEventEnvelope,
                        someEventEnvelope,
                        someSnapshotEnvelope,
                        otherEventEnvelope,
                        someEventEnvelope,
                        otherEventEnvelope,
                    ];
                    (0, sinon_1.replace)(eventStore, 'loadEventStreamSince', sinon_1.fake.resolves(pendingEvents));
                    const results = [1, 3, 4, 6, 7, 9];
                    const inputs = results.map((result) => {
                        return snapshotEnvelopeFor({
                            id: '42',
                            count: result,
                        });
                    });
                    const reducer = (0, sinon_1.stub)();
                    results.forEach((result, index) => {
                        reducer.onCall(index).returns(snapshotEnvelopeFor({
                            id: '42',
                            count: result,
                        }));
                    });
                    (0, sinon_1.replace)(eventStore, 'entityReducer', reducer);
                    (0, sinon_1.replace)(eventStore, 'storeSnapshot', (0, sinon_1.fake)());
                    const entityName = AnEntity.name;
                    const entityID = '42';
                    await (0, expect_1.expect)(eventStore.fetchEntitySnapshot(entityName, entityID)).to.eventually.be.fulfilled;
                    (0, expect_1.expect)(eventStore.loadLatestSnapshot).to.have.been.calledOnceWith(entityName, entityID);
                    (0, expect_1.expect)(eventStore.loadEventStreamSince).to.have.been.calledOnceWith(entityName, entityID, originOfTime);
                    (0, expect_1.expect)(eventStore.entityReducer.getCall(0).args[0]).to.deep.equal(pendingEvents[0]);
                    (0, expect_1.expect)(eventStore.entityReducer.getCall(0).args[1]).to.be.null;
                    for (let index = 1; index < results.length; index++) {
                        // skip the snapshot
                        const eventIndex = index > 2 ? index + 1 : index;
                        (0, expect_1.expect)(eventStore.entityReducer.getCall(index).args[0]).to.deep.equal(pendingEvents[eventIndex]);
                        (0, expect_1.expect)(eventStore.entityReducer.getCall(index).args[1]).to.deep.equal(inputs[index - 1]);
                    }
                    (0, expect_1.expect)(eventStore.storeSnapshot).to.have.been.calledOnce;
                });
            });
        });
        (0, mocha_1.describe)('storeSnapshot', () => {
            it('stores a snapshot in the event store', async () => {
                const eventStore = new event_store_1.EventStore(config);
                (0, sinon_1.replace)(config.provider.events, 'storeSnapshot', (0, sinon_1.fake)());
                const someSnapshot = snapshotEnvelopeFor({
                    id: '42',
                    count: 666,
                });
                await eventStore.storeSnapshot(someSnapshot);
                (0, expect_1.expect)(config.provider.events.storeSnapshot).to.have.been.calledOnceWith(someSnapshot, config);
            });
            context('when there is an error storing the snapshot', () => {
                it('logs the error', async () => {
                    var _a;
                    const eventStore = new event_store_1.EventStore(config);
                    const someSnapshot = snapshotEnvelopeFor({
                        id: '42',
                        count: 666,
                    });
                    const someError = new Error('some error');
                    (0, sinon_1.replace)(config.provider.events, 'storeSnapshot', sinon_1.fake.rejects(someError));
                    await eventStore.storeSnapshot(someSnapshot);
                    (0, expect_1.expect)((_a = config.logger) === null || _a === void 0 ? void 0 : _a.error).to.have.been.calledWithMatch('EventStore#storeSnapshot', sinon_1.match.any, someSnapshot, '\nError:', someError);
                });
            });
        });
    });
    (0, mocha_1.describe)('private methods', () => {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const eventStore = new event_store_1.EventStore(config);
        (0, mocha_1.describe)('loadLatestSnapshot', () => {
            it('looks for the latest snapshot stored in the event stream', async () => {
                (0, sinon_1.replace)(config.provider.events, 'latestEntitySnapshot', (0, sinon_1.fake)());
                const entityTypeName = AnEntity.name;
                const entityID = '42';
                await eventStore.loadLatestSnapshot(entityTypeName, entityID);
                (0, expect_1.expect)(config.provider.events.latestEntitySnapshot).to.have.been.calledOnceWith(config, entityTypeName, entityID);
            });
        });
        (0, mocha_1.describe)('loadEventStreamSince', () => {
            it('loads a event stream starting from a specific timestapm', async () => {
                (0, sinon_1.replace)(config.provider.events, 'forEntitySince', (0, sinon_1.fake)());
                const entityTypeName = AnEntity.name;
                const entityID = '42';
                await eventStore.loadEventStreamSince(entityTypeName, entityID, originOfTime);
                (0, expect_1.expect)(config.provider.events.forEntitySince).to.have.been.calledOnceWith(config, entityTypeName, entityID, originOfTime);
            });
        });
        (0, mocha_1.describe)('entityReducer', () => {
            context('when an entity reducer has been registered for the event', () => {
                context('given a snapshot and a new event', () => {
                    it('calculates the new snapshot value using the proper reducer for the event and the entity types', async () => {
                        const snapshot = snapshotEnvelopeFor(someEntity);
                        const fakeTime = new Date();
                        const eventEnvelope = eventEnvelopeFor(someEvent, AnEvent.name, fakeTime);
                        const fakeReducer = sinon_1.fake.returns({
                            id: '42',
                            count: 1,
                        });
                        (0, sinon_1.replace)(eventStore, 'reducerForEvent', sinon_1.fake.returns(fakeReducer));
                        const newSnapshot = await eventStore.entityReducer(eventEnvelope, snapshot);
                        delete newSnapshot.createdAt;
                        const eventInstance = new AnEvent(someEvent.id, someEvent.entityId, someEvent.delta);
                        eventInstance.entityID = someEvent.entityID;
                        const entityInstance = new AnEntity(someEntity.id, someEntity.count);
                        (0, expect_1.expect)(eventStore.reducerForEvent).to.have.been.calledOnceWith(AnEvent.name);
                        (0, expect_1.expect)(fakeReducer).to.have.been.calledOnceWith(eventInstance, entityInstance);
                        (0, expect_1.expect)(newSnapshot).to.be.deep.equal({
                            version: 1,
                            kind: 'snapshot',
                            requestID: eventEnvelope.requestID,
                            entityID: '42',
                            entityTypeName: AnEntity.name,
                            typeName: AnEntity.name,
                            superKind: 'domain',
                            value: {
                                id: '42',
                                count: 1,
                            },
                            snapshottedEventCreatedAt: fakeTime.toISOString(),
                        });
                    });
                });
                context('given no snapshot and an event', () => {
                    it('generates a new snapshot value using the proper reducer for the event and the entity types', async () => {
                        const fakeTime = new Date();
                        const eventEnvelope = eventEnvelopeFor(someEvent, AnEvent.name, fakeTime);
                        const fakeReducer = sinon_1.fake.returns({
                            id: '42',
                            count: 1,
                        });
                        (0, sinon_1.replace)(eventStore, 'reducerForEvent', sinon_1.fake.returns(fakeReducer));
                        const newSnapshot = await eventStore.entityReducer(eventEnvelope);
                        delete newSnapshot.createdAt;
                        const eventInstance = new AnEvent(someEvent.id, someEvent.entityId, someEvent.delta);
                        eventInstance.entityID = someEvent.entityID;
                        (0, expect_1.expect)(eventStore.reducerForEvent).to.have.been.calledOnceWith(AnEvent.name);
                        (0, expect_1.expect)(fakeReducer).to.have.been.calledOnceWith(eventInstance, null);
                        (0, expect_1.expect)(newSnapshot).to.be.deep.equal({
                            version: 1,
                            kind: 'snapshot',
                            requestID: eventEnvelope.requestID,
                            entityID: '42',
                            entityTypeName: AnEntity.name,
                            typeName: AnEntity.name,
                            superKind: 'domain',
                            value: {
                                id: '42',
                                count: 1,
                            },
                            snapshottedEventCreatedAt: fakeTime.toISOString(),
                        });
                    });
                });
                context('given an internal event', () => {
                    it('calculates the new internal snapshot', async () => {
                        const snapshot = {};
                        const fakeTime = new Date();
                        const eventEnvelope = {
                            version: 1,
                            kind: 'event',
                            entityID: '42',
                            entityTypeName: AnEntity.name,
                            value: {
                                oldEntityName: 'oldEntityName',
                                oldEntityId: 'oldEntityId',
                                newEntityName: 'newEntityName',
                                newEntity: {
                                    id: '42',
                                },
                            },
                            requestID: 'whatever',
                            typeName: booster_entity_migrated_1.BoosterEntityMigrated.name,
                            superKind: 'booster',
                            createdAt: fakeTime.toISOString(),
                        };
                        const newSnapshot = await eventStore.entityReducer(eventEnvelope, snapshot);
                        (0, expect_1.expect)(newSnapshot).to.be.deep.equal({
                            version: 1,
                            kind: 'snapshot',
                            requestID: eventEnvelope.requestID,
                            entityID: '42',
                            entityTypeName: 'newEntityName',
                            typeName: 'newEntityName',
                            superKind: 'booster',
                            value: {
                                id: '42',
                            },
                            snapshottedEventCreatedAt: fakeTime.toISOString(),
                        });
                    });
                });
            });
            context('when an entity reducer calls an instance method in the event', () => {
                it('is executed without failing', async () => {
                    const fakeTime = new Date();
                    const eventEnvelope = eventEnvelopeFor(someEvent, AnotherEvent.name, fakeTime);
                    const getIdFake = (0, sinon_1.fake)();
                    (0, sinon_1.replace)(AnotherEvent.prototype, 'getPrefixedId', getIdFake);
                    await eventStore.entityReducer(eventEnvelope);
                    (0, expect_1.expect)(getIdFake).to.have.been.called;
                });
            });
            context('when an entity reducer calls an instance method in the entity', () => {
                it('is executed without failing', async () => {
                    const snapshot = snapshotEnvelopeFor(someEntity);
                    const fakeTime = new Date();
                    const eventEnvelope = eventEnvelopeFor(someEvent, AnEvent.name, fakeTime);
                    const getIdFake = (0, sinon_1.fake)();
                    (0, sinon_1.replace)(AnEntity.prototype, 'getId', getIdFake);
                    await eventStore.entityReducer(eventEnvelope, snapshot);
                    (0, expect_1.expect)(getIdFake).to.have.been.called;
                });
            });
        });
        (0, mocha_1.describe)('reducerForEvent', () => {
            context('for an event with a registered reducer', () => {
                it('returns the proper reducer method for the event', () => {
                    const reducer = eventStore.reducerForEvent(AnEvent.name);
                    (0, expect_1.expect)(reducer).to.be.instanceOf(Function);
                    (0, expect_1.expect)(reducer).to.be.equal(eval('AnEntity')['reducerThatCallsEntityMethod']);
                });
            });
            context('for events without registered reducers', () => {
                it('fails miserably', () => {
                    (0, expect_1.expect)(() => eventStore.reducerForEvent('InventedEvent')).to.throw(/No reducer registered for event InventedEvent/);
                });
            });
        });
    });
});
