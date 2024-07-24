"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.wrapEventEnvelopesForCosmosDB = exports.addMockSystemGeneratedProperties = exports.createMockEventEnvelopes = void 0;
const faker_1 = require("faker");
function createMockEventEnvelopes(numOfEvents = 1) {
    return new Array(numOfEvents).fill({
        version: faker_1.random.number(),
        entityID: faker_1.random.uuid(),
        kind: 'event',
        superKind: 'domain',
        value: {
            id: faker_1.random.uuid(),
        },
        typeName: faker_1.random.word(),
        entityTypeName: faker_1.random.word(),
        requestID: faker_1.random.uuid(),
        createdAt: faker_1.date.past().toISOString(),
        id: faker_1.random.uuid(),
    }, 0, numOfEvents);
}
exports.createMockEventEnvelopes = createMockEventEnvelopes;
function addMockSystemGeneratedProperties(eventEnvelopes) {
    return eventEnvelopes.map((eventEnvelope) => {
        return {
            ...eventEnvelope,
            id: faker_1.random.uuid(),
            _rid: faker_1.random.alphaNumeric(24),
            _self: `dbs/${faker_1.random.alphaNumeric(8)}/colls/${faker_1.random.alphaNumeric(12)}/docs/${faker_1.random.alphaNumeric(24)}/`,
            _etag: `"${faker_1.random.uuid()}"`,
            _attachments: 'attachments/',
            _ts: ~~(faker_1.date.past().getTime() / 1000),
        };
    });
}
exports.addMockSystemGeneratedProperties = addMockSystemGeneratedProperties;
function wrapEventEnvelopesForCosmosDB(eventEnvelopes) {
    return {
        bindingData: {},
        bindingDefinitions: [],
        executionContext: {},
        invocationId: '',
        log: {},
        traceContext: {},
        done(err, result) {
        },
        bindings: { rawEvent: eventEnvelopes },
    };
}
exports.wrapEventEnvelopesForCosmosDB = wrapEventEnvelopesForCosmosDB;
