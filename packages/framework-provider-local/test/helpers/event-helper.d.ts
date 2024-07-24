import { EntitySnapshotEnvelope, EventEnvelope, NonPersistedEventEnvelope } from '@boostercloud/framework-types';
export declare function createMockNonPersistedEventEnvelop(): NonPersistedEventEnvelope;
export declare function createMockNonPersistedEventEnvelopeForEntity(entityTypeName: string, entityID: string): NonPersistedEventEnvelope;
export declare function createMockEventEnvelope(): EventEnvelope;
export declare function createMockEventEnvelopeForEntity(entityTypeName: string, entityID: string): EventEnvelope;
export declare function createMockEntitySnapshotEnvelope(entityTypeName?: string, entityId?: string): EntitySnapshotEnvelope;
