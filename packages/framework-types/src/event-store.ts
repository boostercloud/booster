import { EventStoreEntryEnvelope } from "./envelope";

export interface EventStore {
  query: (query: unknown, createdAt: number, limit?: number, projections?: unknown) => Promise<EventStoreEntryEnvelope>
}
