"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.createMockEntitySnapshotEnvelope = exports.createMockEventEnvelopeForEntity = exports.createMockEventEnvelope = exports.createMockNonPersistedEventEnvelopeForEntity = exports.createMockNonPersistedEventEnvelop = void 0;
const faker_1 = require("faker");
function createMockNonPersistedEventEnvelop() {
    return createMockNonPersistedEventEnvelopeForEntity(faker_1.random.word(), faker_1.random.uuid());
}
exports.createMockNonPersistedEventEnvelop = createMockNonPersistedEventEnvelop;
function createMockNonPersistedEventEnvelopeForEntity(entityTypeName, entityID) {
    return {
        kind: 'event',
        superKind: 'domain',
        entityID: entityID,
        entityTypeName: entityTypeName,
        value: {
            id: faker_1.random.uuid(),
        },
        requestID: faker_1.random.uuid(),
        typeName: faker_1.random.word(),
        version: faker_1.random.number(),
    };
}
exports.createMockNonPersistedEventEnvelopeForEntity = createMockNonPersistedEventEnvelopeForEntity;
function createMockEventEnvelope() {
    return createMockEventEnvelopeForEntity(faker_1.random.word(), faker_1.random.uuid());
}
exports.createMockEventEnvelope = createMockEventEnvelope;
function createMockEventEnvelopeForEntity(entityTypeName, entityID) {
    return {
        kind: 'event',
        superKind: 'domain',
        entityID: entityID,
        entityTypeName: entityTypeName,
        value: {
            id: faker_1.random.uuid(),
        },
        createdAt: faker_1.date.past().toISOString(),
        requestID: faker_1.random.uuid(),
        typeName: faker_1.random.word(),
        version: faker_1.random.number(),
    };
}
exports.createMockEventEnvelopeForEntity = createMockEventEnvelopeForEntity;
function createMockEntitySnapshotEnvelope(entityTypeName, entityId) {
    const creationDate = faker_1.date.past();
    const snapshottedEventCreatedAt = creationDate.toISOString();
    return {
        kind: 'snapshot',
        superKind: 'domain',
        entityID: entityId !== null && entityId !== void 0 ? entityId : faker_1.random.uuid(),
        entityTypeName: entityTypeName !== null && entityTypeName !== void 0 ? entityTypeName : faker_1.random.word(),
        value: {
            id: faker_1.random.uuid(),
        },
        createdAt: snapshottedEventCreatedAt,
        persistedAt: new Date(creationDate.getTime() + 1000).toISOString(),
        requestID: faker_1.random.uuid(),
        typeName: faker_1.random.word(),
        version: faker_1.random.number(),
        snapshottedEventCreatedAt,
    };
}
exports.createMockEntitySnapshotEnvelope = createMockEntitySnapshotEnvelope;
