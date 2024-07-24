"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const mocha_1 = require("mocha");
const sinon_1 = require("sinon");
const framework_types_1 = require("@boostercloud/framework-types");
const raw_events_parser_1 = require("../../src/services/raw-events-parser");
const expect_1 = require("../expect");
const faker_1 = require("faker");
(0, mocha_1.describe)('RawEventsParser', () => {
    afterEach(() => {
        (0, sinon_1.restore)();
    });
    const rawEvents = {}; // This value doesn't matter, because we are going to fake 'rawToEnvelopes'
    const entityAName = 'EntityA';
    const entityAID = 'EntityAID';
    const entityBName = 'EntityB';
    const entityBID = 'EntityBID';
    const snapshottedEntityName = 'SnapshottedEntity';
    let persistedEventEnvelopeForEntityA1;
    let persistedEventEnvelopeForEntityA2;
    let persistedEventEnvelopeForEntityA3;
    let persistedEventEnvelopeForEntityB1;
    let persistedEventEnvelopeForEntityB2;
    let persistedEventEnvelopeForEntityB3;
    let persistedEventEnvelopeForEntityB4;
    let eventSource;
    let fakeRawToEnvelopes;
    let config;
    beforeEach(() => {
        persistedEventEnvelopeForEntityA1 = createPersistedEventEnvelope(entityAName, entityAID);
        persistedEventEnvelopeForEntityA2 = createPersistedEventEnvelope(entityAName, entityAID);
        persistedEventEnvelopeForEntityA3 = createPersistedEventEnvelope(entityAName, entityAID);
        persistedEventEnvelopeForEntityB1 = createPersistedEventEnvelope(entityBName, entityBID);
        persistedEventEnvelopeForEntityB2 = createPersistedEventEnvelope(entityBName, entityBID);
        persistedEventEnvelopeForEntityB3 = createPersistedEventEnvelope(entityBName, entityBID);
        persistedEventEnvelopeForEntityB4 = createPersistedEventEnvelope(entityBName, entityBID);
        eventSource = [
            persistedEventEnvelopeForEntityA1,
            createEntitySnapshotEnvelope(snapshottedEntityName, faker_1.random.uuid()),
            persistedEventEnvelopeForEntityA2,
            persistedEventEnvelopeForEntityA3,
            createEntitySnapshotEnvelope(snapshottedEntityName, faker_1.random.uuid()),
            createEntitySnapshotEnvelope(snapshottedEntityName, faker_1.random.uuid()),
            persistedEventEnvelopeForEntityB1,
            persistedEventEnvelopeForEntityB2,
            persistedEventEnvelopeForEntityB3,
            createEntitySnapshotEnvelope(snapshottedEntityName, faker_1.random.uuid()),
            createEntitySnapshotEnvelope(snapshottedEntityName, faker_1.random.uuid()),
            persistedEventEnvelopeForEntityB4,
            createEntitySnapshotEnvelope(snapshottedEntityName, faker_1.random.uuid()),
        ];
        fakeRawToEnvelopes = sinon_1.fake.returns(eventSource);
        config = new framework_types_1.BoosterConfig('test');
        config.provider = {
            events: {
                rawToEnvelopes: fakeRawToEnvelopes,
            },
        };
        config.logger = {
            error: (0, sinon_1.fake)(),
            info: (0, sinon_1.fake)(),
            debug: (0, sinon_1.fake)(),
            warn: (0, sinon_1.fake)(),
        };
    });
    (0, mocha_1.describe)('streamPerEntityEvents', () => {
        it('strips all snapshots', async () => {
            const callbackFunction = (0, sinon_1.fake)();
            const events = config.provider.events.rawToEnvelopes(rawEvents);
            await raw_events_parser_1.RawEventsParser.streamPerEntityEvents(config, events, callbackFunction);
            (0, expect_1.expect)(callbackFunction).not.to.have.been.calledWith(snapshottedEntityName);
        });
        it('calls the callback function with ordered groups of event envelopes per entity name and ID', async () => {
            const callbackFunction = (0, sinon_1.fake)();
            const events = config.provider.events.rawToEnvelopes(rawEvents);
            await raw_events_parser_1.RawEventsParser.streamPerEntityEvents(config, events, callbackFunction);
            (0, expect_1.expect)(callbackFunction).to.have.been.calledTwice;
            (0, expect_1.expect)(callbackFunction).to.have.been.calledWithExactly(entityAName, entityAID, [persistedEventEnvelopeForEntityA1, persistedEventEnvelopeForEntityA2, persistedEventEnvelopeForEntityA3], config);
            (0, expect_1.expect)(callbackFunction).to.have.been.calledWithExactly(entityBName, entityBID, [
                persistedEventEnvelopeForEntityB1,
                persistedEventEnvelopeForEntityB2,
                persistedEventEnvelopeForEntityB3,
                persistedEventEnvelopeForEntityB4,
            ], config);
        });
        it('calls the callback function for all the events per entity even if for some it throws', async () => {
            var _a;
            const error = new Error('Wow, such error, many failures!');
            const events = [];
            const callbackFunction = (0, sinon_1.fake)(async (entityName, entityId, eventEnvelopes) => {
                if (entityName === entityAName) {
                    throw error;
                }
                events.push(...eventEnvelopes);
            });
            const eventsEnvelopes = config.provider.events.rawToEnvelopes(rawEvents);
            await (0, expect_1.expect)(raw_events_parser_1.RawEventsParser.streamPerEntityEvents(config, eventsEnvelopes, callbackFunction)).to.be.eventually
                .fulfilled;
            (0, expect_1.expect)(callbackFunction).to.have.been.calledTwice;
            (0, expect_1.expect)(callbackFunction).to.have.been.calledWithExactly(entityAName, entityAID, [persistedEventEnvelopeForEntityA1, persistedEventEnvelopeForEntityA2, persistedEventEnvelopeForEntityA3], config);
            const entityBEvents = [
                persistedEventEnvelopeForEntityB1,
                persistedEventEnvelopeForEntityB2,
                persistedEventEnvelopeForEntityB3,
                persistedEventEnvelopeForEntityB4,
            ];
            (0, expect_1.expect)(callbackFunction).to.have.been.calledWithExactly(entityBName, entityBID, entityBEvents, config);
            (0, expect_1.expect)(events).to.deep.equal(entityBEvents);
            (0, expect_1.expect)((_a = config.logger) === null || _a === void 0 ? void 0 : _a.error).to.have.been.calledWithExactly('[Booster]|RawEventsParser#streamPerEntityEvents: ', `An error occurred while processing events for entity ${entityAName} with ID ${entityAID}`, error);
        });
    });
});
function createPersistedEventEnvelope(entityTypeName, entityID) {
    const createdAt = faker_1.random.alpha();
    return {
        entityID: entityID,
        entityTypeName: entityTypeName,
        kind: 'event',
        superKind: 'domain',
        version: 1,
        value: { id: faker_1.random.uuid() },
        requestID: faker_1.random.uuid(),
        typeName: 'Event' + faker_1.random.alpha(),
        createdAt,
    };
}
function createEntitySnapshotEnvelope(entityTypeName, entityID) {
    const snapshottedEventCreatedAt = faker_1.random.alpha();
    return {
        entityID: entityID,
        entityTypeName: entityTypeName,
        kind: 'snapshot',
        superKind: 'domain',
        version: 1,
        value: { id: faker_1.random.uuid() },
        requestID: faker_1.random.uuid(),
        typeName: 'Snapshot' + faker_1.random.alpha(),
        createdAt: snapshottedEventCreatedAt,
        persistedAt: faker_1.random.alpha(),
        snapshottedEventCreatedAt,
    };
}
