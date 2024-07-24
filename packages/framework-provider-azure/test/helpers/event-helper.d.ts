import { EventEnvelope } from '@boostercloud/framework-types';
import { Context } from '@azure/functions';
export declare function createMockEventEnvelopes(numOfEvents?: number): Array<EventEnvelope>;
export declare function addMockSystemGeneratedProperties(eventEnvelopes: Array<EventEnvelope>): Array<EventEnvelope>;
export declare function wrapEventEnvelopesForCosmosDB(eventEnvelopes: Array<EventEnvelope>): Context;
